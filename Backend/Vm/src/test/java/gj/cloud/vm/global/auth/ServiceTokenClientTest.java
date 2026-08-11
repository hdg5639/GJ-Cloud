package gj.cloud.vm.global.auth;

import gj.cloud.vm.global.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTokenClientTest {

    @Test
    void sharesOneRefreshAndReusesToken() {
        AuthProperties properties = new AuthProperties();
        properties.setServerUrl("http://auth-service");
        properties.setServiceClientId("vm-service");
        properties.setServiceClientSecret("secret");
        AtomicInteger requests = new AtomicInteger();
        WebClient webClient = WebClient.builder().exchangeFunction(request -> {
            requests.incrementAndGet();
            return reactor.core.publisher.Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            {"success":true,"data":{"accessToken":"cached-token","tokenType":"Bearer","expiresIn":300},"message":null}
                            """)
                    .build());
        }).build();
        ServiceTokenClient client = new ServiceTokenClient(properties, webClient, Clock.systemUTC());

        assertThat(client.getToken().block()).isEqualTo("cached-token");
        assertThat(client.getToken().block()).isEqualTo("cached-token");
        assertThat(requests).hasValue(1);
    }
}
