package gj.cloud.vm.infra.cloudflare.client;

import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import gj.cloud.vm.infra.cloudflare.config.CloudflareProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudflareClientTest {

    @Test
    void retriesTransientCloudflareFailures() {
        AtomicInteger attempts = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                return Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY)
                        .body("{\"success\":false}")
                        .build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .body("{\"result\":{\"id\":\"dns-record-1\"}}")
                    .build());
        };
        CloudflareClient client = client(exchange);

        assertThat(client.registerCname("preview").block()).isEqualTo("dns-record-1");

        assertThat(attempts).hasValue(3);
    }

    @Test
    void doesNotRetryDuplicateDnsRecord() {
        AtomicInteger postAttempts = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            if (request.method() == HttpMethod.GET) {
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                        .body("{\"result\":[]}")
                        .build());
            }
            postAttempts.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                    .body("{\"errors\":[{\"code\":81057,\"message\":\"record already exists\"}]}")
                    .build());
        };
        CloudflareClient client = client(exchange);

        assertThatThrownBy(() -> client.registerCname("preview").block())
                .isInstanceOfSatisfying(VmException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(VmErrorCode.SUBDOMAIN_ALREADY_TAKEN));

        assertThat(postAttempts).hasValue(1);
    }

    @Test
    void adoptsExistingCnameThatAlreadyTargetsTheSameTunnel() {
        AtomicInteger postAttempts = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            if (request.method() == HttpMethod.GET) {
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                        .body("{\"result\":[{\"id\":\"dns-existing\",\"name\":\"preview.example.test\",\"content\":\"tunnel.cfargotunnel.com\"}]}")
                        .build());
            }
            postAttempts.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                    .body("{\"errors\":[{\"code\":81057,\"message\":\"record already exists\"}]}")
                    .build());
        };
        CloudflareClient client = client(exchange);

        assertThat(client.registerCname("preview").block()).isEqualTo("dns-existing");
        assertThat(postAttempts).hasValue(1);
    }

    @Test
    void doesNotBlindlyRetryNonIdempotentAccessAppCreation() {
        AtomicInteger attempts = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            attempts.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY)
                    .body("{\"success\":false}")
                    .build());
        };
        CloudflareClient client = client(exchange);

        assertThatThrownBy(() -> client.createAccessApp("preview", "self_hosted").block())
                .isInstanceOf(WebClientResponseException.BadGateway.class);

        assertThat(attempts).hasValue(1);
    }

    private CloudflareClient client(ExchangeFunction exchange) {
        CloudflareProperties properties = new CloudflareProperties();
        properties.setApiToken("test-token");
        properties.setAccountId("account");
        properties.setZoneId("zone");
        properties.setTunnelId("tunnel");
        properties.setBaseDomain("example.test");
        return new CloudflareClient(
                WebClient.builder().exchangeFunction(exchange).build(), properties);
    }
}
