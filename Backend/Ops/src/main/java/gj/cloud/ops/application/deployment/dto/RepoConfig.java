package gj.cloud.ops.application.deployment.dto;

// D.3 레포 설정 (공통 1단계). patToken은 영속화하지 않음 — 요청 시점에만 사용하고 버림(GIT_ASKPASS 스크립트도 사용 직후 삭제)
// context는 저장소 내 배포 대상 서브디렉토리(Raw Compose 전용) — null/"."이면 저장소 루트에서 배포.
// installPath는 VM 파일시스템 절대경로 — null이면 미사용, 지정하면 VM 내 해당 경로가 현재 활성 release를
// 가리키는 심볼릭 링크가 됨 (release/rollback 디렉토리 체계는 그대로 두고 접근 편의용 별칭만 추가).
public record RepoConfig(String repoUrl, String branch, String patToken, String context, String installPath) {

    public RepoConfig(String repoUrl, String branch, String patToken) {
        this(repoUrl, branch, patToken, null, null);
    }
}
