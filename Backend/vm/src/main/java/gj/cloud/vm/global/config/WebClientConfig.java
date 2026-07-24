package gj.cloud.vm.global.config;

import gj.cloud.vm.infra.cloudflare.config.CloudflareProperties;
import gj.cloud.vm.infra.proxmox.config.ProxmoxProperties;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final ProxmoxProperties proxmoxProperties;
    private final CloudflareProperties cloudflareProperties;

    // SEC-003: 운영 환경에서는 trust-all 코드 경로 자체가 존재하지 않음 — JVM 기본 트러스트스토어만 사용.
    // Proxmox가 공인 인증서(예: Caddy를 통한 자동 발급)를 쓰지 않는다면 해당 CA를 JVM 트러스트스토어에
    // 등록해야 함(-Djavax.net.ssl.trustStore 등). "prod에서 trust-all 기동 자체가 불가능"을 빈 분리로 보장.
    @Bean(name = "proxmoxWebClient")
    @Profile("!dev")
    public WebClient proxmoxWebClientSecure() {
        return WebClient.builder()
                .baseUrl(proxmoxProperties.getBaseUrl())
                .build();
    }

    // 로컬 개발 전용: 자체 서명 인증서를 쓰는 홈랩 Proxmox에 한해 proxmox.tls-insecure=true로 trust-all 허용.
    // 기본값은 false — dev에서도 기본은 공인/등록된 인증서 검증을 그대로 수행.
    @Bean(name = "proxmoxWebClient")
    @Profile("dev")
    public WebClient proxmoxWebClientDev() throws SSLException {
        if (!proxmoxProperties.isTlsInsecure()) {
            return WebClient.builder()
                    .baseUrl(proxmoxProperties.getBaseUrl())
                    .build();
        }

        log.warn("proxmox.tls-insecure=true — Proxmox TLS 인증서 검증을 건너뜁니다 (dev 전용, prod에서는 이 옵션 자체가 없음)");
        SslContext sslContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        HttpClient httpClient = HttpClient.create()
                .secure(t -> t.sslContext(sslContext));

        return WebClient.builder()
                .baseUrl(proxmoxProperties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public WebClient cloudflareWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.cloudflare.com/client/v4")
                .build();
    }
}
