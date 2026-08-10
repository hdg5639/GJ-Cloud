package gj.cloud.ops.application.vmclient;

import gj.cloud.ops.global.auth.ServiceTokenClient;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class VmAutomationClientTest {
    @Test
    void startsWithoutRestClientBuilderBean() {
        new ApplicationContextRunner()
                .withPropertyValues("vm.service-url=http://vm-service")
                .withBean(ServiceTokenClient.class, () -> mock(ServiceTokenClient.class))
                .withBean(VmAutomationClient.class)
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
                    org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(VmAutomationClient.class);
                });
    }

    @Test
    void preservesNotFoundAsDefinitiveVmMissingSignal() {
        ServiceTokenClient tokenClient = mock(ServiceTokenClient.class);
        when(tokenClient.getToken()).thenReturn("service-token");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VmAutomationClient client = new VmAutomationClient("http://vm-service", tokenClient, builder);
        server.expect(once(), requestTo("http://vm-service/internal/automation/vms/vm-1/context?ownerUserId=user-1&ownerEmail=owner@example.com"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getContext("vm-1", "user-1", "owner@example.com"))
                .isInstanceOfSatisfying(OpsException.class, error ->
                        org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                                .isEqualTo(OpsErrorCode.VM_NOT_FOUND));
        server.verify();
    }
}
