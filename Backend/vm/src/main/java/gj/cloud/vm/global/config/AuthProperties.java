package gj.cloud.vm.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
    private String serverUrl;
    private long jwksCacheTtl;
    private String serviceClientId;
    private String serviceClientSecret;
}
