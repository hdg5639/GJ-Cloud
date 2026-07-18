package gj.cloud.ops.application.deployment.dto;

// D.3 레포 설정 (공통 1단계). patToken은 영속화하지 않음 — 요청 시점에만 사용하고 버림(GIT_ASKPASS 스크립트도 사용 직후 삭제)
public record RepoConfig(String repoUrl, String branch, String patToken) {
}
