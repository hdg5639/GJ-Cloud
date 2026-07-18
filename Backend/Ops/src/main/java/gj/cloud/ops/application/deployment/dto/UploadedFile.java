package gj.cloud.ops.application.deployment.dto;

// D.4 공통 파일 업로드 — 소스 체크아웃 이후 SFTP로 전송되어 체크아웃 결과를 덮어씀 (순서 보장)
public record UploadedFile(String vmPath, byte[] content) {
}
