package gj.cloud.ops.application.filebrowser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// AUTHZ-001: SECRET_READ 필요 판정 대상(.env류, 배포 앱 디렉토리, SSH/Git 자격증명)이 정확히
// 걸리고, 일반 파일은 걸리지 않는지 확인.
class SensitiveFilePolicyTest {

    @Test
    void flagsEnvFile() {
        assertThat(SensitiveFilePolicy.isSensitive("/home/deploy/app/.env")).isTrue();
    }

    @Test
    void flagsEnvVariantFile() {
        assertThat(SensitiveFilePolicy.isSensitive("/home/deploy/app/.env.production")).isTrue();
    }

    @Test
    void flagsDeployedAppsDirectory() {
        assertThat(SensitiveFilePolicy.isSensitive("/home/deploy/gamjabox/apps/my-app/docker-compose.yml")).isTrue();
    }

    @Test
    void flagsSshDirectory() {
        assertThat(SensitiveFilePolicy.isSensitive("/home/deploy/.ssh/config")).isTrue();
    }

    @Test
    void flagsIdRsaFilename() {
        assertThat(SensitiveFilePolicy.isSensitive("/home/deploy/.ssh/id_rsa")).isTrue();
    }

    @Test
    void flagsGitCredentialsFilename() {
        assertThat(SensitiveFilePolicy.isSensitive("/home/deploy/.git-credentials")).isTrue();
    }

    @Test
    void flagsAwsDirectory() {
        assertThat(SensitiveFilePolicy.isSensitive("/home/deploy/.aws/credentials")).isTrue();
    }

    @Test
    void allowsRegularFile() {
        assertThat(SensitiveFilePolicy.isSensitive("/home/deploy/notes.txt")).isFalse();
    }

    @Test
    void allowsRegularDirectoryListing() {
        assertThat(SensitiveFilePolicy.isSensitive("/home/deploy/projects/readme.md")).isFalse();
    }

    @Test
    void doesNotFlagUnrelatedDotFile() {
        assertThat(SensitiveFilePolicy.isSensitive("/home/deploy/.bashrc")).isFalse();
    }
}
