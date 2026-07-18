package gj.cloud.ops.application.deployment.dto;

// .env 등 텍스트 환경변수 파일 (D.4) — 임의 바이너리 업로드(UploadedFile)와 구분되는 별도 카테고리
public record EnvironmentFile(String vmPath, String content) {
}
