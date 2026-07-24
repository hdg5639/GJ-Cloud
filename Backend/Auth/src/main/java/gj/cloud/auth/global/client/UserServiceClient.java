package gj.cloud.auth.global.client;

import gj.cloud.auth.global.jwt.JwtProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

// SEC-001/SEC-005: 회원가입·이메일인증 시 User 서비스에 프로필 생성을, 회원 탈퇴 시 프로필 삭제를
// 요청하는 유일한 창구. Auth 자신의 client-credentials 신원(auth-service)으로 발급받은 서비스 토큰을
// 프로세스 내부에서 직접 발급해 붙인다(HTTP 자기호출 불필요, 스푸핑 불가능 — 같은 JVM 코드).
// 이전에는 이 호출들이 Authorization 헤더 없이 나가서 User 서비스의 인증 요구 앞에서 항상 실패했었다.
@Slf4j
@Component
public class UserServiceClient {

    private final RestClient restClient;
    private final JwtProvider jwtProvider;
    private final String userServiceUrl;

    public UserServiceClient(
            RestClient restClient,
            JwtProvider jwtProvider,
            @Value("${app.services.user-service-url}") String userServiceUrl
    ) {
        this.restClient = restClient;
        this.jwtProvider = jwtProvider;
        this.userServiceUrl = userServiceUrl;
    }

    public void createProfile(String userId, String email) {
        try {
            restClient.post()
                    .uri(userServiceUrl + "/internal/profiles")
                    .header("Authorization", "Bearer " + serviceToken())
                    .header("Content-Type", "application/json")
                    .body(Map.of("userId", userId, "email", email))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("User 서비스 프로필 생성 실패 (userId={}): {}", userId, e.getMessage());
        }
    }

    // REL-001: 재시도 스케줄러가 성공 여부를 job 상태에 반영해야 하므로 boolean으로 결과를 알려준다.
    public boolean deleteUser(String userId) {
        try {
            restClient.delete()
                    .uri(userServiceUrl + "/internal/users/{userId}", userId)
                    .header("Authorization", "Bearer " + serviceToken())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("User 서비스 데이터 삭제 실패 (userId={}): {}", userId, e.getMessage());
            return false;
        }
    }

    private String serviceToken() {
        return jwtProvider.issueServiceToken("auth-service", "user-service", "profile:manage");
    }
}
