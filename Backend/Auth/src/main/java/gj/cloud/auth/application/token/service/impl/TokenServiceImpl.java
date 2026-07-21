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
import gj.cloud.auth.domain.user.enums.UserStatus;
import gj.cloud.auth.domain.user.repository.UserRepository;
import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.exception.enums.AuthErrorCode;
import gj.cloud.auth.global.jwt.JwtProperties;
import gj.cloud.auth.global.jwt.JwtProvider;
import gj.cloud.auth.global.jwt.ServiceClientProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private static final String PREFIX_REFRESH = "refresh:";
    private static final String PREFIX_USED    = "used:";
    private static final String PREFIX_EXCHANGE = "exchange:";
    private static final String REUSE_MARKER   = "REUSE:";

    // SEC-007: GET(존재 확인) → DELETE(소비) → SET(블랙리스트 등록)이 별개의 Redis 왕복이라
    // 동시에 같은 refresh token으로 두 요청이 들어오면 둘 다 GET에서 값을 보고 통과할 수 있었음.
    // 이 스크립트로 조회+소비+블랙리스트 등록을 하나의 원자적 실행으로 묶어 둘 중 하나만 성공하게 한다.
    // used: 값도 원본 metadata(userId+familyId 포함)를 그대로 저장해, 재사용 감지 시 어느 family를
    // 무효화해야 하는지 별도 조회 없이 바로 알 수 있게 한다.
    private static final RedisScript<String> REFRESH_CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local metadata = redis.call('GET', KEYS[1])
            if metadata then
              redis.call('DEL', KEYS[1])
              redis.call('SET', KEYS[2], metadata, 'PX', ARGV[1])
              return metadata
            end
            local usedMetadata = redis.call('GET', KEYS[2])
            if usedMetadata then
              return 'REUSE:' .. usedMetadata
            end
            return false
            """, String.class);

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
        String familyId = UUID.randomUUID().toString();
        long ttlMs = rememberMe
                ? jwtProperties.getRememberMeRefreshTokenExpiry()
                : jwtProperties.getRefreshTokenExpiry();
        long now = Instant.now().toEpochMilli();
        String metadata = buildMetadata(userId, familyId, rememberMe, now, now);
        redisTemplate.opsForValue().set(PREFIX_REFRESH + tokenId, metadata, ttlMs, TimeUnit.MILLISECONDS);
        return tokenId;
    }

    @Override
    public RefreshResult refresh(String tokenId) {
        // rememberMe 여부와 무관하게 더 긴 쪽(30일)을 블랙리스트 TTL로 사용 — 짧게 잡아 탈취 감지 창이
        // 실제 refresh token 수명보다 먼저 끝나는 사고를 피하기 위함(다소 보수적으로 길게 유지해도 무해함).
        String result = redisTemplate.execute(REFRESH_CONSUME_SCRIPT,
                List.of(PREFIX_REFRESH + tokenId, PREFIX_USED + tokenId),
                String.valueOf(jwtProperties.getRememberMeRefreshTokenExpiry()));

        if (result == null || result.isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (result.startsWith(REUSE_MARKER)) {
            // SEC-007: 이전에는 탈취 감지 시 사용자 전체 세션을 죽였음 — family ID로 범위를 좁혀
            // 탈취된 토큰 체인만 무효화하고, 같은 사용자의 다른 정상 로그인(다른 기기 등)은 유지한다.
            String stolenMetadata = result.substring(REUSE_MARKER.length());
            revokeFamilyOrAll(parseField(stolenMetadata, "userId"), parseField(stolenMetadata, "familyId"));
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        }

        String metadataJson = result;
        String userId      = parseField(metadataJson, "userId");
        String familyId     = parseField(metadataJson, "familyId");
        boolean rememberMe = "true".equals(parseField(metadataJson, "rememberMe"));
        long issuedAt  = Long.parseLong(parseField(metadataJson, "issuedAt"));
        long now       = Instant.now().toEpochMilli();

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        // SEC-004: login()과 달리 refresh는 상태를 재확인하지 않아, 정지 이후에도 이미 발급된 refresh
        // token으로 계속 갱신이 가능했던 문제. 여기서도 동일하게 재확인한다.
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AuthException(AuthErrorCode.ACCOUNT_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new AuthException(AuthErrorCode.ACCOUNT_DELETED);
        }

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
        String newMetadata     = buildMetadata(userId, familyId, rememberMe, issuedAt, now);
        redisTemplate.opsForValue().set(PREFIX_REFRESH + newTokenId, newMetadata, newTtlMs, TimeUnit.MILLISECONDS);

        long cookieMaxAgeSeconds = newTtlMs / 1000;
        return new RefreshResult(newAccessToken, newTokenId, cookieMaxAgeSeconds);
    }

    // 탈취된 토큰의 family를 특정할 수 있으면 그 family만, 알 수 없으면(예: 배포 직후 과거 방식으로
    // 저장된 레코드) 안전하게 사용자 전체 세션을 무효화한다.
    private void revokeFamilyOrAll(String userId, String familyId) {
        if (familyId == null || familyId.isBlank()) {
            deleteAllUserTokens(userId);
        } else {
            revokeFamily(userId, familyId);
        }
    }

    private void revokeFamily(String userId, String familyId) {
        for (String key : scanKeys(PREFIX_REFRESH + "*")) {
            String val = redisTemplate.opsForValue().get(key);
            if (val != null && userId.equals(parseField(val, "userId")) && familyId.equals(parseField(val, "familyId"))) {
                redisTemplate.delete(key);
            }
        }
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

        // SEC-004: exchange도 refresh와 마찬가지로 원 토큰 발급 시점 이후의 계정 상태 변화(정지/탈퇴)를
        // 반영하지 않고 있었음 — 매 교환마다 최신 상태를 재확인한다.
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AuthException(AuthErrorCode.ACCOUNT_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new AuthException(AuthErrorCode.ACCOUNT_DELETED);
        }

        String cacheKey = PREFIX_EXCHANGE + userId + ":" + targetAudience.getValue();

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            Long ttlMs = redisTemplate.getExpire(cacheKey, TimeUnit.MILLISECONDS);
            if (ttlMs != null && ttlMs > 30_000) {
                return new TokenResponse(cached, "Bearer", ttlMs / 1000);
            }
        }

        String newToken = jwtProvider.issueAccessToken(userId, user.getEmail(), user.getRole(), targetAudience.getValue());
        redisTemplate.opsForValue().set(cacheKey, newToken, jwtProperties.getExchangeTokenExpiry(), TimeUnit.MILLISECONDS);
        return new TokenResponse(newToken, "Bearer", jwtProperties.getExchangeTokenExpiry() / 1000);
    }

    // OPS-SEC-002: client_id/client_secret으로 서비스 자신의 신원을 증명하는 client-credentials 발급.
    // audience/scope는 요청자가 지정할 수 없고 등록된 클라이언트 설정에서만 결정됨(자기 audience/scope 확대 불가).
    // 시크릿 비교는 타이밍 공격을 막기 위해 MessageDigest.isEqual(상수 시간 비교)을 사용.
    @Override
    public TokenResponse issueServiceToken(ServiceTokenRequest request) {
        ServiceClientProperties.ServiceClient config = serviceClientProperties.getClients().get(request.clientId());
        if (config == null) {
            log.warn("서비스 토큰 발급 거부: clientId='{}'가 auth.service-clients 설정에 등록돼 있지 않음 (등록된 clientId 목록: {})",
                    request.clientId(), serviceClientProperties.getClients().keySet());
            throw new AuthException(AuthErrorCode.INVALID_SERVICE_CLIENT);
        }
        if (config.getSecret() == null || config.getSecret().isBlank()) {
            log.warn("서비스 토큰 발급 거부: clientId='{}'는 등록돼 있으나 설정된 secret이 비어있음 (VM_SERVICE_CLIENT_SECRET 환경변수 확인 필요)",
                    request.clientId());
            throw new AuthException(AuthErrorCode.INVALID_SERVICE_CLIENT);
        }
        boolean requestSecretPresent = request.clientSecret() != null && !request.clientSecret().isBlank();
        boolean secretMatches = requestSecretPresent && MessageDigest.isEqual(
                request.clientSecret().getBytes(StandardCharsets.UTF_8),
                config.getSecret().getBytes(StandardCharsets.UTF_8));
        if (!secretMatches) {
            log.warn("서비스 토큰 발급 거부: clientId='{}' secret 불일치 (요청 secret 존재={}, 요청 길이={}, 등록된 길이={})",
                    request.clientId(), requestSecretPresent,
                    request.clientSecret() == null ? -1 : request.clientSecret().length(),
                    config.getSecret().length());
            throw new AuthException(AuthErrorCode.INVALID_SERVICE_CLIENT);
        }

        String token = jwtProvider.issueServiceToken(request.clientId(), config.getAudience(), config.getScope());
        return new TokenResponse(token, "Bearer", jwtProperties.getServiceTokenExpiry() / 1000);
    }

    // 로그아웃/탈퇴/정지처럼 의도적으로 전체 세션을 끊어야 하는 경우 전용 — 탈취 감지 시의 좁은 범위
    // revokeFamily와 구분됨.
    @Override
    public void deleteAllUserTokens(String userId) {
        for (String key : scanKeys(PREFIX_REFRESH + "*")) {
            String val = redisTemplate.opsForValue().get(key);
            if (val != null && userId.equals(parseField(val, "userId"))) {
                redisTemplate.delete(key);
            }
        }
        List<String> exchangeKeys = scanKeys(PREFIX_EXCHANGE + userId + ":*");
        if (!exchangeKeys.isEmpty()) {
            redisTemplate.delete(exchangeKeys);
        }
    }

    @Override
    public String getJwks() {
        return jwtProvider.getJwks();
    }

    // SEC-007: 활성 refresh token 전체를 훑는 KEYS는 블로킹 커맨드라 운영 중인 Redis 인스턴스를
    // 순간 정지시킬 수 있음 — SCAN 커서 기반으로 교체.
    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(200).build())) {
            cursor.forEachRemaining(keys::add);
        }
        return keys;
    }

    // ── 메타데이터 JSON 직렬화/역직렬화 ──────────────────────────────

    private String buildMetadata(String userId, String familyId, boolean rememberMe, long issuedAt, long lastUsedAt) {
        return "{\"userId\":\"" + userId + "\""
                + ",\"familyId\":\"" + familyId + "\""
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
