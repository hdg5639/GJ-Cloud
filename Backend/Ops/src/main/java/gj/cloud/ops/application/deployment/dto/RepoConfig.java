package gj.cloud.ops.application.deployment.dto;

// D.3 레포 설정 (공통 1단계). patToken은 영속화하지 않음 — 요청 시점에만 사용하고 버림(GIT_ASKPASS 스크립트도 사용 직후 삭제)
// context는 저장소 내 배포 대상 서브디렉토리(Raw Compose 전용) — null/"."이면 저장소 루트에서 배포.
public record RepoConfig(String repoUrl, String branch, String patToken, String context) {

    public RepoConfig(String repoUrl, String branch, String patToken) {
        this(repoUrl, branch, patToken, null);
    }
}
