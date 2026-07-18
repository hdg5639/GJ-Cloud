package gj.cloud.ops.application.filebrowser.dto;

// 발급 시점(정상 JWT 인증 하에)에 이미 실경로(symlink 해석 완료)와 internalIp/size까지 확정해 저장 —
// 스트리밍 GET에서는 다시 권한 조회/경로 해석 없이 이 값만으로 SFTP 세션을 열 수 있게 함
public record FileStreamTicketPayload(String vmId, String path, String internalIp, long size) {
}
