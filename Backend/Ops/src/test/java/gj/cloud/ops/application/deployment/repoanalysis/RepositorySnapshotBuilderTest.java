package gj.cloud.ops.application.deployment.repoanalysis;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RepositorySnapshotBuilderTest {

    @Test
    void buildsGitHttpAuthorizationWithoutExecutableAskpassScript() {
        Map<String, String> environment =
                RepositorySnapshotBuilder.gitHttpAuthenticationEnvironment("installation-token-example");

        assertThat(environment)
                .containsEntry("GIT_CONFIG_COUNT", "1")
                .containsEntry("GIT_CONFIG_KEY_0", "http.extraHeader")
                .doesNotContainKeys("GIT_ASKPASS");

        String header = environment.get("GIT_CONFIG_VALUE_0");
        assertThat(header).startsWith("Authorization: Basic ");

        String encoded = header.substring("Authorization: Basic ".length());
        assertThat(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8))
                .isEqualTo("oauth2:installation-token-example");
    }
}
