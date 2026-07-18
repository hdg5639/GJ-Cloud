package gj.cloud.auth.application.token.service.impl;

import com.nimbusds.jwt.JWTClaimsSet;
import gj.cloud.auth.application.auth.dto.TokenExchangeRequest;
import gj.cloud.auth.application.token.dto.RefreshResult;
import gj.cloud.auth.application.token.dto.ServiceTokenRequest;
import gj.cloud.auth.application.token.dto.TokenResponse;
import gj.cloud.auth.application.token.service.TokenService;
import gj.cloud.auth.domain.token.enums.ServiceAudience;
import gj.cloud.auth.domain.user.entity.UserEntity;
import gj.cloud.auth.domain.user.enums.UserRole;
import gj.cloud.auth.domain.user.repository.UserRepository;
import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import gj.cloud.auth.global.jwt.JwtProperties;
import gj.cloud.auth.global.jwt.JwtProvider;
import gj.cloud.auth.global.jwt.ServiceClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private static final String PREFIX_REFRESH = "refresh:";
    private static final String PREFIX_USED    = "used:";
    private static final String PREFIX_EXCHANGE = "exchange:";

    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final ServiceClientProperties serviceClientProperties;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    @Override
    public String issueAccessToken(String userId, String email, UserRole role, ServiceAudience audience) {
        return jwtProvider.issueAccessToken(userId, email, role, audience.getValue());
    }

    @Override
    public String issueRefreshToken(String userId, boolean rememberMe) {
        String tokenId = UUID.randomUUID().toString();
        long ttlMs = rememberMe
                ? jwtProperties.getRememberMeRefreshTokenExpiry()
                : jwtProperties.getRefreshTokenExpiry();
        long now = Instant.now().toEpochMilli();
        String metadata = buildMetadata(userId, rememberMe, now, now);
        redisTemplate.opsForValue().set(PREFIX_REFRESH + tokenId, metadata, ttlMs, TimeUnit.MILLISECONDS);
        return tokenId;
    }

    @Override
    public RefreshResult refresh(String tokenId) {
        String metadataJson = redisTemplate.opsForValue().get(PREFIX_REFRESH + tokenId);

        if (metadataJson == null) {
            // 탈취 감지: 이미 rotation으로 소비된 tokenId인지 확인
            String stolenUserId = redisTemplate.opsForValue().get(PREFIX_USED + tokenId);
            if (stolenUserId != null) {
                deleteAllUserTokens(stolenUserId);
                throw new AuthException(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
            }
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        String userId    = parseField(metadataJson, "userId");
        boolean rememberMe = "true".equals(parseField(metadataJson, "rememberMe"));
        long issuedAt  = Long.parseLong(parseField(metadataJson, "issuedAt"));
        long now       = Instant.now().toEpochMilli();

        // 기존 토큰 삭제 + 사용 이력 블랙리스트 등록 (탈취 감지용)
        redisTemplate.delete(PREFIX_REFRESH + tokenId);
        long blacklistTtlMs = rememberMe
                ? jwtProperties.getRememberMeRefreshTokenExpiry()
                : jwtProperties.getRefreshTokenExpiry();
        redisTemplate.opsForValue().set(PREFIX_USED + tokenId, userId, blacklistTtlMs, TimeUnit.MILLISECONDS);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        // rememberMe=true → sliding (30일 리셋), false → 남은 TTL 유지
        long newTtlMs;
        if (rememberMe) {
            newTtlMs = jwtProperties.getRememberMeRefreshTokenExpiry();
        } else {
            long elapsed = now - issuedAt;
            newTtlMs = Math.max(jwtProperties.getRefreshTokenExpiry() - elapsed, 0);
        }

        String newAccessToken  = issueAccessToken(userId, user.getEmail(), user.getRole(), ServiceAudience.AUTH);
        String newTokenId      = UUID.randomUUID().toString();
        String newMetadata     = buildMetadata(userId, rememberMe, issuedAt, now);
        redisTemplate.opsForValue().set(PREFIX_REFRESH + newTokenId, newMetadata, newTtlMs, TimeUnit.MILLISECONDS);

        long cookieMaxAgeSeconds = newTtlMs / 1000;
        return new RefreshResult(newAccessToken, newTokenId, cookieMaxAgeSeconds);
    }

    @Override
    public TokenResponse exchange(String accessToken, TokenExchangeRequest request) {
        JWTClaimsSet claims = jwtProvider.validateAndParseClaims(accessToken);
        String audValue = claims.getAudience().stream().findFirst()
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_ACCESS_TOKEN));
        if (!ServiceAudience.AUTH.getValue().equals(audValue)) {
            throw new AuthException(AuthErrorCode.INVALID_AUDIENCE);
        }

        ServiceAudience targetAudience = ServiceAudience.from(request.targetService());
        String userId = claims.getSubject();
        String cacheKey = PREFIX_EXCHANGE + userId + ":" + targetAudience.getValue();

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            Long ttlMs = redisTemplate.getExpire(cacheKey, TimeUnit.MILLISECONDS);
            if (ttlMs != null && ttlMs > 30_000) {
                return new TokenResponse(cached, "Bearer", ttlMs / 1000);
            }
        }

        String email   = (String) claims.getClaim("email");
        String roleStr = (String) claims.getClaim("role");
        UserRole role;
        try {
            role = UserRole.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            throw new AuthException(AuthErrorCode.INVALID_ACCESS_TOKEN);
        }

        String newToken = jwtProvider.issueAccessToken(userId, email, role, targetAudience.getValue());
        redisTemplate.opsForValue().set(cacheKey, newToken, jwtProperties.getExchangeTokenExpiry(), TimeUnit.MILLISECONDS);
        return new TokenResponse(newToken, "Bearer", jwtProperties.getExchangeTokenExpiry() / 1000);
    }

    // OPS-SEC-002: client_id/client_secret으로 서비스 자신의 신원을 증명하는 client-credentials 발급.
    // audience/scope는 요청자가 지정할 수 없고 등록된 클라이언트 설정에서만 결정됨(자기 audience/scope 확대 불가).
    // 시크릿 비교는 타이밍 공격을 막기 위해 MessageDigest.isEqual(상수 시간 비교)을 사용.
    @Override
    public TokenResponse issueServiceToken(ServiceTokenRequest request) {
        ServiceClientProperties.ServiceClient config = serviceClientProperties.getClients().get(request.clientId());
        if (config == null || config.getSecret() == null || config.getSecret().isBlank()
                || !MessageDigest.isEqual(
                        request.clientSecret().getBytes(StandardCharsets.UTF_8),
                        config.getSecret().getBytes(StandardCharsets.UTF_8))) {
            throw new AuthException(AuthErrorCode.INVALID_SERVICE_CLIENT);
        }

        String token = jwtProvider.issueServiceToken(request.clientId(), config.getAudience(), config.getScope());
        return new TokenResponse(token, "Bearer", jwtProperties.getServiceTokenExpiry() / 1000);
    }

    @Override
    public void deleteAllUserTokens(String userId) {
        Set<String> refreshKeys = redisTemplate.keys(PREFIX_REFRESH + "*");
        if (refreshKeys != null) {
            refreshKeys.stream()
                    .filter(k -> {
                        String val = redisTemplate.opsForValue().get(k);
                        return val != null && userId.equals(parseField(val, "userId"));
                    })
                    .forEach(redisTemplate::delete);
        }
        Set<String> exchangeKeys = redisTemplate.keys(PREFIX_EXCHANGE + userId + ":*");
        if (exchangeKeys != null && !exchangeKeys.isEmpty()) {
            redisTemplate.delete(exchangeKeys);
        }
    }

    @Override
    public String getJwks() {
        return jwtProvider.getJwks();
    }

    // ── 메타데이터 JSON 직렬화/역직렬화 ──────────────────────────────

    private String buildMetadata(String userId, boolean rememberMe, long issuedAt, long lastUsedAt) {
        return "{\"userId\":\"" + userId + "\""
                + ",\"rememberMe\":" + rememberMe
                + ",\"issuedAt\":" + issuedAt
                + ",\"lastUsedAt\":" + lastUsedAt
                + "}";
    }

    private String parseField(String json, String field) {
        String key = "\"" + field + "\":";
        int start = json.indexOf(key);
        if (start == -1) return "";
        start += key.length();
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return json.substring(start + 1, end);
        }
        int end = json.indexOf(',', start);
        if (end == -1) end = json.indexOf('}', start);
        return json.substring(start, end).trim();
    }
}
