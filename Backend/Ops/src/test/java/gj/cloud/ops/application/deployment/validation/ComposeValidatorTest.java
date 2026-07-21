package gj.cloud.ops.application.deployment.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// DEP-003: 기존 privileged/host네트워크/docker소켓/DB포트 체크에 이번에 추가한
// pid:host/ipc:host/devices/cap_add/security_opt/일반화된 민감 바인드마운트 체크까지 검증.
class ComposeValidatorTest {

    private final ComposeValidator validator = new ComposeValidator();

    private static String composeWith(String extraServiceFields) {
        return """
                services:
                  web:
                    image: nginx:latest
                %s
                """.formatted(extraServiceFields);
    }

    @Test
    void safeComposePasses() {
        String compose = composeWith("    ports:\n      - \"8080:80\"");
        assertThat(validator.validate(compose).valid()).isTrue();
    }

    @Test
    void rejectsPrivileged() {
        String compose = composeWith("    privileged: true");
        assertThat(validator.validate(compose).valid()).isFalse();
    }

    @Test
    void rejectsHostNetworkMode() {
        String compose = composeWith("    network_mode: host");
        assertThat(validator.validate(compose).valid()).isFalse();
    }

    @Test
    void rejectsDockerSocketMount() {
        String compose = composeWith("    volumes:\n      - /var/run/docker.sock:/var/run/docker.sock");
        assertThat(validator.validate(compose).valid()).isFalse();
    }

    @Test
    void rejectsSensitiveHostRootBindMount() {
        String compose = composeWith("    volumes:\n      - /:/host-root");
        assertThat(validator.validate(compose).valid()).isFalse();
    }

    @Test
    void rejectsSensitiveEtcBindMount() {
        String compose = composeWith("    volumes:\n      - /etc:/etc");
        assertThat(validator.validate(compose).valid()).isFalse();
    }

    @Test
    void allowsNamedVolume() {
        // "소스:대상" 콜론 파싱이 named volume(콜론 없는 볼륨명)까지 잘못 걸러내지 않는지 확인
        String compose = composeWith("    volumes:\n      - app_data:/var/lib/data");
        assertThat(validator.validate(compose).valid()).isTrue();
    }

    @Test
    void rejectsExposedDatabasePort() {
        String compose = composeWith("    ports:\n      - \"5432:5432\"");
        assertThat(validator.validate(compose).valid()).isFalse();
    }

    @Test
    void allowsDatabasePortBoundToLocalhost() {
        String compose = composeWith("    ports:\n      - \"127.0.0.1:5432:5432\"");
        assertThat(validator.validate(compose).valid()).isTrue();
    }

    @Test
    void rejectsPidHost() {
        String compose = composeWith("    pid: host");
        assertThat(validator.validate(compose).valid()).isFalse();
    }

    @Test
    void rejectsIpcHost() {
        String compose = composeWith("    ipc: host");
        assertThat(validator.validate(compose).valid()).isFalse();
    }

    @Test
    void rejectsDevices() {
        String compose = composeWith("    devices:\n      - \"/dev/mem:/dev/mem\"");
        assertThat(validator.validate(compose).valid()).isFalse();
    }

    @Test
    void rejectsCapAdd() {
        String compose = composeWith("    cap_add:\n      - SYS_ADMIN");
        assertThat(validator.validate(compose).valid()).isFalse();
    }

    @Test
    void allowsCapDrop() {
        String compose = composeWith("    cap_drop:\n      - ALL");
        assertThat(validator.validate(compose).valid()).isTrue();
    }

    @Test
    void rejectsSeccompUnconfined() {
        String compose = composeWith("    security_opt:\n      - \"seccomp:unconfined\"");
        assertThat(validator.validate(compose).valid()).isFalse();
    }

    @Test
    void rejectsApparmorUnconfined() {
        String compose = composeWith("    security_opt:\n      - \"apparmor:unconfined\"");
        assertThat(validator.validate(compose).valid()).isFalse();
    }
}
