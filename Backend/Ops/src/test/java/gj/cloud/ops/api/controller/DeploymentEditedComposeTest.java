package gj.cloud.ops.api.controller;
import gj.cloud.ops.application.deployment.dto.*;
import gj.cloud.ops.application.deployment.service.*;
import gj.cloud.ops.application.deployment.spec.*;
import gj.cloud.ops.application.deployment.validation.ComposeValidator;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.domain.deployment.entity.DeploymentEntity;
import gj.cloud.ops.domain.deployment.enums.*;
import gj.cloud.ops.global.security.OpsPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class DeploymentEditedComposeTest {
 @Test void fromSpecEnqueuesEditedYamlWithGeneratedDockerfilesAndOriginalSecret() {
  var executor = mock(DeploymentExecutor.class); var renderer = mock(DeploymentSpecRenderer.class); var vm = mock(VmServiceClient.class);
  var controller = new DeploymentController(executor,null,null,mock(DeploymentSpecValidator.class),mock(DeploymentSpecPolicyValidator.class),renderer,null,null,null,null,vm,null,null,new ComposePreparationService(new ComposeValidator()));
  var request = new MockHttpServletRequest(); request.addHeader("Authorization","Bearer fixture"); UUID vmId = UUID.randomUUID();
  when(vm.getContext(anyString(),anyString())).thenReturn(new VmContextResponse(vmId.toString(),"user",null,"RUNNING","OWNER",List.of("DEPLOY")));
  var dockerfile = new UploadedFile("Dockerfile", "FROM nginx".getBytes());
  when(renderer.render(any())).thenReturn(new ComposeArtifact("services:\n  web:\n    image: regenerated\n",List.of(),List.of(dockerfile),List.of(),List.of(),SourceType.TEMPLATE_SPEC));
  when(executor.enqueue(anyString(),anyString(),any(),any())).thenReturn(DeploymentEntity.builder().id("deployment").status(DeploymentStatus.QUEUED).triggerType(DeploymentTriggerType.MANUAL).sourceType(SourceType.AI_SPEC).build());
  String edited = "# edited by user\nservices:\n  web:\n    image: nginx:alpine\n    environment:\n      PASSWORD: original-fixture\n";
  var override = new ComposeSpecResponse(edited,List.of(),List.of(),List.of(),null,null,null);
  controller.createFromSpec(request,vmId,new OpsPrincipal("user","user@example.test"),new DeploymentFromSpecRequest("https://github.com/example/app","main",null,mock(DeploymentSpec.class),null,null,false,null,null,override));
  verify(executor).enqueue(eq("fixture"),eq(vmId.toString()),any(),argThat(artifact -> {
   assertThat(artifact.composeContent()).isEqualTo(edited); assertThat(artifact.uploadedFiles()).containsExactly(dockerfile);
   assertThat(artifact.healthChecks()).anyMatch(HealthCheck::readinessOnly); return true;
  }));
 }
}
