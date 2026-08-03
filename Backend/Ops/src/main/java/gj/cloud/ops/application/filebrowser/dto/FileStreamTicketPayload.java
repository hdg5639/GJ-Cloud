package gj.cloud.ops.application.filebrowser.dto;

// 발급 시점의 실경로(symlink 해석 완료)와 internalIp/size를 확정해 저장한다.
// 스트리밍 GET마다 현재 경로·IP·크기와 다시 비교해 TTL 도중의 심볼릭 교체나 VM IP 변경을 거부한다.
// SEC-012: 권한 재검증에 필요한 사용자 토큰은 Redis에 평문으로 두지 않고 AES-256-GCM 암호문만 보관한다.
public record FileStreamTicketPayload(
        String vmId,
        String path,
        String internalIp,
        long size,
        String bearerTokenCiphertext
) {
}
