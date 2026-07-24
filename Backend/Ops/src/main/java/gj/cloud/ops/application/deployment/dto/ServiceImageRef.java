package gj.cloud.ops.application.deployment.dto;

// D.7 — 다이제스트 대신 `docker image inspect --format '{{.Id}}'`로 얻은 로컬 이미지 ID를 기록 (로컬 Docker만 쓰는 경우 registry digest가 없을 수 있음)
public record ServiceImageRef(String imageTag, String imageId) {
}
