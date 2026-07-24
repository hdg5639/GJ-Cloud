package gj.cloud.ops.application.filebrowser.dto;

// 발급 시점(정상 JWT 인증 하에)에 이미 실경로(symlink 해석 완료)와 internalIp/size까지 확정해 저장 —
// 스트리밍 GET에서는 경로 해석을 다시 하지 않고 이 값만으로 SFTP 세션을 열 수 있게 함.
// SEC-012: bearerToken은 매 스트리밍 요청마다 FILE_READ 권한을 재조회하기 위해 보관 — 이 값은
// Redis(서버 내부)에만 있고 클라이언트에 노출되는 URL에는 opaque 티켓 문자열만 포함된다.
public record FileStreamTicketPayload(String vmId, String path, String internalIp, long size, String bearerToken) {
}
