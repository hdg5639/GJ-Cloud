package gj.cloud.auth.application.token.service.impl;

import com.nimbusds.jwt.JWTClaimsSet;
import gj.cloud.auth.application.auth.dto.LoginResponse;
import gj.cloud.auth.application.auth.dto.TokenExchangeRequest;
import gj.cloud.auth.application.auth.dto.TokenRefreshRequest;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    @Override
    public String issueAccessToken(String userId, String email, UserRole role, ServiceAudience audience) {
        return jwtProvider.issueAccessToken(userId, email, role, audience.getValue());
    }

    @Override
    public String issueRefreshToken(String userId) {
        String tokenId = UUID.randomUUID().toString();
        String key = "refresh:" + userId + ":" + tokenId;
        redisTemplate.opsForValue().set(key, tokenId, jwtProperties.getRefreshTokenExpiry(), TimeUnit.MILLISECONDS);
        return tokenId;
    }

    @Override
    public LoginResponse refresh(TokenRefreshRequest request) {
        String tokenId = request.refreshToken();
        Set<String> keys = redisTemplate.keys("refresh:*:" + tokenId);
        if (keys == null || keys.isEmpty()) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        String key = keys.iterator().next();
        String userId = key.split(":")[1];

        // Rotation: delete old, issue new
        redisTemplate.delete(key);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        String newAccessToken = issueAccessToken(userId, user.getEmail(), user.getRole(), ServiceAudience.AUTH);
        String newRefreshToken = issueRefreshToken(userId);
        return new LoginResponse(newAccessToken, newRefreshToken, "Bearer", 900L);
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
        String cacheKey = "exchange:" + userId + ":" + targetAudience.getValue();

        // Check cache — reuse if >30s remaining
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            Long ttlMs = redisTemplate.getExpire(cacheKey, TimeUnit.MILLISECONDS);
            if (ttlMs != null && ttlMs > 30_000) {
                return new TokenResponse(cached, "Bearer", ttlMs / 1000);
            }
        }

        String email = (String) claims.getClaim("email");
        String roleStr = (String) claims.getClaim("role");
        UserRole role = UserRole.valueOf(roleStr);

        String newToken = jwtProvider.issueAccessToken(userId, email, role, targetAudience.getValue());
        redisTemplate.opsForValue().set(
                cacheKey,
                newToken,
                jwtProperties.getExchangeTokenExpiry(),
                TimeUnit.MILLISECONDS
        );
        return new TokenResponse(newToken, "Bearer", jwtProperties.getExchangeTokenExpiry() / 1000);
    }

    @Override
    public void deleteAllUserTokens(String userId) {
        Set<String> refreshKeys = redisTemplate.keys("refresh:" + userId + ":*");
        if (refreshKeys != null && !refreshKeys.isEmpty()) {
            redisTemplate.delete(refreshKeys);
        }
        Set<String> exchangeKeys = redisTemplate.keys("exchange:" + userId + ":*");
        if (exchangeKeys != null && !exchangeKeys.isEmpty()) {
            redisTemplate.delete(exchangeKeys);
        }
    }

    @Override
    public String getJwks() {
        return jwtProvider.getJwks();
    }
}
