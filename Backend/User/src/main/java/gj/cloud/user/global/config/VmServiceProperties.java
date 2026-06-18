package gj.cloud.user.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "vm")
public class VmServiceProperties {
    private String serviceUrl;
}
