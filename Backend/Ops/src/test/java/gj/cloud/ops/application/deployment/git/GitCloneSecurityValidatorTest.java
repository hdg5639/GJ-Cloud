package gj.cloud.ops.application.deployment.git;

import gj.cloud.ops.global.exception.OpsException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// DEP-001: IP 리터럴 URL은 DNS 조회 없이 바로 InetAddress로 판정되므로 결정적으로 테스트 가능.
// 호스트명 기반 URL(예: github.com)은 실제 DNS 조회가 필요해 이 환경에서 검증 불가하므로 다루지 않음.
class GitCloneSecurityValidatorTest {

    private final GitCloneSecurityValidator validator = new GitCloneSecurityValidator();

    @Test
    void rejectsLoopbackAddress() {
        assertThatThrownBy(() -> validator.assertHostNotInternal("http://127.0.0.1/repo.git"))
                .isInstanceOf(OpsException.class);
    }

    @Test
    void rejectsPrivateClassAAddress() {
        assertThatThrownBy(() -> validator.assertHostNotInternal("http://10.0.0.5/repo.git"))
                .isInstanceOf(OpsException.class);
    }

    @Test
    void rejectsPrivateClassCAddress() {
        assertThatThrownBy(() -> validator.assertHostNotInternal("http://192.168.1.10/repo.git"))
                .isInstanceOf(OpsException.class);
    }

    @Test
    void rejectsLinkLocalAddress() {
        assertThatThrownBy(() -> validator.assertHostNotInternal("http://169.254.1.1/repo.git"))
                .isInstanceOf(OpsException.class);
    }

    @Test
    void rejectsCloudMetadataAddress() {
        assertThatThrownBy(() -> validator.assertHostNotInternal("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(OpsException.class);
    }

    @Test
    void rejectsMalformedUrl() {
        assertThatThrownBy(() -> validator.assertHostNotInternal("not-a-url"))
                .isInstanceOf(OpsException.class);
    }

    @Test
    void allowsPublicIpLiteral() {
        // 결정적 공인 주소(구글 DNS) — 실제 DNS 조회 없이 IP 리터럴 자체로 판정되므로 네트워크 접근 불필요
        assertThatCode(() -> validator.assertHostNotInternal("http://8.8.8.8/repo.git"))
                .doesNotThrowAnyException();
    }
}
