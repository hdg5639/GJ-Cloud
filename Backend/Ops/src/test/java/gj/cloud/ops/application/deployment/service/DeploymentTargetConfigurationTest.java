package gj.cloud.ops.application.deployment.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.deployment.dto.*;
import gj.cloud.ops.domain.deployment.entity.DeploymentTargetEntity;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import gj.cloud.ops.domain.deployment.repository.DeploymentTargetRepository;
import gj.cloud.ops.global.crypto.AesGcmCipher;
import gj.cloud.ops.global.exception.OpsException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class DeploymentTargetConfigurationTest {
 final DeploymentTargetRepository repository = mock(DeploymentTargetRepository.class);
 final AesGcmCipher cipher = new AesGcmCipher("12345678901234567890123456789012");
 final DeploymentTargetService service = new DeploymentTargetService(repository,cipher,new ObjectMapper());
 final DeploymentTargetEntity target = DeploymentTargetEntity.builder().id("target").sourceComposeCiphertext(cipher.encrypt("original".getBytes())).active(true).build();
 final ComposeArtifact artifact = new ComposeArtifact("edited YAML",List.of(new EnvironmentFile(".env","SECRET=fixture")),List.of(),List.of(),List.of(),SourceType.AI_SPEC);
 @Test void savesEncryptedConfigurationWithCompareAndSwap() {
  when(repository.updateConfiguration(eq("target"),eq(target.getSourceComposeCiphertext()),anyString(),anyString(),isNull(),isNull(),any())).thenReturn(1);
  service.updateConfiguration(target,service.configurationVersion(target),artifact);
  verify(repository).updateConfiguration(eq("target"),eq(target.getSourceComposeCiphertext()),argThat(value -> !value.contains("edited YAML") && new String(cipher.decrypt(value)).equals("edited YAML")),argThat(value -> !value.contains("fixture") && new String(cipher.decrypt(value)).contains("SECRET=fixture")),isNull(),isNull(),any());
 }
 @Test void rejectsStaleEditBeforeWriting() {
  assertThatThrownBy(() -> service.updateConfiguration(target,"stale",artifact)).isInstanceOf(OpsException.class);
  verifyNoInteractions(repository);
 }
 @Test void concurrentUpdateCannotOverwriteNewerConfiguration() {
  when(repository.updateConfiguration(any(),any(),any(),any(),any(),any(),any())).thenReturn(0);
  assertThatThrownBy(() -> service.updateConfiguration(target,service.configurationVersion(target),artifact)).isInstanceOf(OpsException.class);
 }
}
