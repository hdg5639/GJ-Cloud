package gj.cloud.ops.global.auth;

import gj.cloud.ops.global.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ServiceTokenClientTest {

    @Test
    void reusesTokenUntilRefreshWindow() {
        AuthProperties properties = new AuthProperties();
        properties.setServerUrl("http://auth-service");
        properties.setServiceClientId("ops-service");
        properties.setServiceClientSecret("secret");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ServiceTokenClient client = new ServiceTokenClient(properties, builder, Clock.systemUTC());
        server.expect(once(), requestTo("http://auth-service/auth/token/service"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"accessToken":"cached-token","tokenType":"Bearer","expiresIn":300},"message":null}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getToken()).isEqualTo("cached-token");
        assertThat(client.getToken()).isEqualTo("cached-token");
        server.verify();
    }
}
