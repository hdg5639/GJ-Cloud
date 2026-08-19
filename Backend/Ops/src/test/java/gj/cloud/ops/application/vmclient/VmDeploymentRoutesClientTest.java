package gj.cloud.ops.application.vmclient;

import gj.cloud.ops.application.deployment.dto.DeploymentRoutesRequest;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class VmDeploymentRoutesClientTest {

    @Test
    void startsWithoutRestClientBuilderBean() {
        new ApplicationContextRunner()
                .withPropertyValues("vm.service-url=http://vm-service")
                .withBean(VmDeploymentRoutesClient.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(VmDeploymentRoutesClient.class);
                });
    }

    @Test
    void preservesVmServiceConflictMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VmDeploymentRoutesClient client = new VmDeploymentRoutesClient("http://vm-service", builder);
        server.expect(once(), requestTo("http://vm-service/internal/ops/vms/vm-1/deployment-routes"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"success\":false,\"data\":null,\"message\":\"이미 사용 중인 서브도메인입니다.\"}"));

        assertThatThrownBy(() -> client.syncRoutes(
                "user-token", "vm-1", new DeploymentRoutesRequest("target-1", "deployment-1", List.of())))
                .isInstanceOfSatisfying(OpsException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(OpsErrorCode.DEPLOYMENT_ROUTE_CONFLICT);
                    assertThat(error.getMessage()).isEqualTo("이미 사용 중인 서브도메인입니다.");
                });
        server.verify();
    }
}
