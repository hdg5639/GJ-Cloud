package gj.cloud.auth.application.token.service;

import gj.cloud.auth.application.auth.dto.LoginResponse;
import gj.cloud.auth.application.auth.dto.TokenExchangeRequest;
import gj.cloud.auth.application.auth.dto.TokenRefreshRequest;
import gj.cloud.auth.application.token.dto.TokenResponse;
import gj.cloud.auth.domain.token.enums.ServiceAudience;
import gj.cloud.auth.domain.user.enums.UserRole;

public interface TokenService {
    String issueAccessToken(String userId, String email, UserRole role, ServiceAudience audience);
    String issueRefreshToken(String userId);
    LoginResponse refresh(TokenRefreshRequest request);
    TokenResponse exchange(String accessToken, TokenExchangeRequest request);
    void deleteAllUserTokens(String userId);
    String getJwks();
}
