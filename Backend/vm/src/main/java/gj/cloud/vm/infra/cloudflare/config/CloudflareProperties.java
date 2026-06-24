package gj.cloud.vm.infra.cloudflare.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

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
    private List<String> reservedSubdomains = List.of();
}
