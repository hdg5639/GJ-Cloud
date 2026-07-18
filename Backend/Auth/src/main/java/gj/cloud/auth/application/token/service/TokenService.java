package gj.cloud.auth.application.token.service;

import gj.cloud.auth.application.auth.dto.TokenExchangeRequest;
import gj.cloud.auth.application.token.dto.RefreshResult;
import gj.cloud.auth.application.token.dto.ServiceTokenRequest;
import gj.cloud.auth.application.token.dto.TokenResponse;
import gj.cloud.auth.domain.token.enums.ServiceAudience;
import gj.cloud.auth.domain.user.enums.UserRole;

public interface TokenService {
    String issueAccessToken(String userId, String email, UserRole role, ServiceAudience audience);
    String issueRefreshToken(String userId, boolean rememberMe);
    RefreshResult refresh(String tokenId);
    TokenResponse exchange(String accessToken, TokenExchangeRequest request);
    TokenResponse issueServiceToken(ServiceTokenRequest request);
    void deleteAllUserTokens(String userId);
    String getJwks();
}
