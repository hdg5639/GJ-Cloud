package gj.cloud.auth.global.config;

import gj.cloud.auth.global.jwt.JwtProperties;
import gj.cloud.auth.global.jwt.ServiceClientProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, ServiceClientProperties.class})
public class JwtConfig {
}
