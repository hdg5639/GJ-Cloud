package gj.cloud.vm.infra.proxmox.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "proxmox")
public class ProxmoxProperties {
    private String baseUrl;
    private String apiToken;
    private String node;
    private String bridge;
    private String storage;
    private int vmidRangeStart;
    private int vmidRangeEnd;
}
