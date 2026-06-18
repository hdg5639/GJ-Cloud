package gj.cloud.vm.infra.cloudflare.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cloudflare")
public class CloudflareProperties {
    private String apiToken;
    private String accountId;
    private String zoneId;
    private String tunnelId;
    private String baseDomain;
}
