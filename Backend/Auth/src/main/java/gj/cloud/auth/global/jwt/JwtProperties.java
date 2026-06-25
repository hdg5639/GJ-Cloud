package gj.cloud.auth.global.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {
    private String privateKey;
    private String publicKey;
    private long accessTokenExpiry = 900000L;
    private long refreshTokenExpiry = 604800000L;          // 7일
    private long rememberMeRefreshTokenExpiry = 2592000000L; // 30일
    private long exchangeTokenExpiry = 900000L;
}
