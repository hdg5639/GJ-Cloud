package gj.cloud.ops.application.deployment.git;

import gj.cloud.ops.global.exception.OpsException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// DEP-001: IP 리터럴을 테스트별 allowlist에 넣어, allowlist 통과 후에도 내부 대역 검사가
// 동작하는지 DNS 외부 의존성 없이 검증한다.
class GitCloneSecurityValidatorTest {

    @Test
    void rejectsLoopbackAddress() {
        GitCloneSecurityValidator validator = new GitCloneSecurityValidator("127.0.0.1");
        assertThatThrownBy(() -> validator.assertHostNotInternal("http://127.0.0.1/repo.git"))
                .isInstanceOf(OpsException.class);
    }

    @Test
    void rejectsPrivateClassAAddress() {
        GitCloneSecurityValidator validator = new GitCloneSecurityValidator("10.0.0.5");
        assertThatThrownBy(() -> validator.assertHostNotInternal("http://10.0.0.5/repo.git"))
                .isInstanceOf(OpsException.class);
    }

    @Test
    void rejectsPrivateClassCAddress() {
        GitCloneSecurityValidator validator = new GitCloneSecurityValidator("192.168.1.10");
        assertThatThrownBy(() -> validator.assertHostNotInternal("http://192.168.1.10/repo.git"))
                .isInstanceOf(OpsException.class);
    }

    @Test
    void rejectsLinkLocalAddress() {
        GitCloneSecurityValidator validator = new GitCloneSecurityValidator("169.254.1.1");
        assertThatThrownBy(() -> validator.assertHostNotInternal("http://169.254.1.1/repo.git"))
                .isInstanceOf(OpsException.class);
    }

    @Test
    void rejectsCloudMetadataAddress() {
        GitCloneSecurityValidator validator = new GitCloneSecurityValidator("169.254.169.254");
        assertThatThrownBy(() -> validator.assertHostNotInternal("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(OpsException.class);
    }

    @Test
    void rejectsMalformedUrl() {
        GitCloneSecurityValidator validator = new GitCloneSecurityValidator("github.com");
        assertThatThrownBy(() -> validator.assertHostNotInternal("not-a-url"))
                .isInstanceOf(OpsException.class);
    }

    @Test
    void rejectsPublicIpLiteralOutsideAllowlist() {
        GitCloneSecurityValidator validator = new GitCloneSecurityValidator("github.com");
        assertThatThrownBy(() -> validator.assertHostNotInternal("http://8.8.8.8/repo.git"))
                .isInstanceOf(OpsException.class);
    }
}
