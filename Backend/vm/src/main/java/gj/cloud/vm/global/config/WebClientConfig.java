package gj.cloud.vm.global.config;

import gj.cloud.vm.infra.proxmox.config.ProxmoxProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final ProxmoxProperties proxmoxProperties;

    @Bean
    public WebClient proxmoxWebClient() {
        return WebClient.builder()
                .baseUrl(proxmoxProperties.getBaseUrl())
                .build();
    }
}
