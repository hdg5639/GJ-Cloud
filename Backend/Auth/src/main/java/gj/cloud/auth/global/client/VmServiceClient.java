package gj.cloud.auth.global.client;

import gj.cloud.auth.global.jwt.JwtProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

// SEC-001/SEC-002: 회원 탈퇴 시 VM 서비스에 사용자 소유 VM/조직/협업 데이터 정리를 요청하는 유일한 창구.
// Auth 자신의 client-credentials 신원(auth-service)으로 발급받은 서비스 토큰을 프로세스 내부에서 직접
// 발급해 붙인다. 이전에는 이 호출이 Authorization 헤더 없이 나가서 VM 서비스의 인증 요구 앞에서 항상
// 실패했었다(공격 경로만 열려 있고 정상 흐름은 항상 401 후 무시됨).
@Slf4j
@Component
public class VmServiceClient {

    private final RestClient restClient;
    private final JwtProvider jwtProvider;
    private final String vmServiceUrl;

    public VmServiceClient(
            RestClient restClient,
            JwtProvider jwtProvider,
            @Value("${app.services.vm-service-url}") String vmServiceUrl
    ) {
        this.restClient = restClient;
        this.jwtProvider = jwtProvider;
        this.vmServiceUrl = vmServiceUrl;
    }

    // REL-001: 재시도 스케줄러가 성공 여부를 job 상태에 반영해야 하므로 boolean으로 결과를 알려준다.
    public boolean deleteUserData(String userId, String email) {
        String correlationId = UUID.randomUUID().toString();
        String token = jwtProvider.issueServiceToken("auth-service", "vm-service", "user:cleanup");
        try {
            restClient.delete()
                    .uri(vmServiceUrl + "/internal/users?userId={userId}&email={email}", userId, email)
                    .header("Authorization", "Bearer " + token)
                    .header("X-Correlation-Id", correlationId)
                    .retrieve()
                    .toBodilessEntity();
            log.info("VM 서비스 데이터 삭제 완료 (userId={}, correlationId={})", userId, correlationId);
            return true;
        } catch (Exception e) {
            log.warn("VM 서비스 데이터 삭제 실패 (userId={}, correlationId={}): {}", userId, correlationId, e.getMessage());
            return false;
        }
    }
}
