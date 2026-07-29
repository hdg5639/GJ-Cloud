package gj.cloud.ops.application.userclient;

import gj.cloud.ops.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class UserPlanClient {

    private static final ParameterizedTypeReference<ApiResponse<String>> PLAN_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public UserPlanClient(@Value("${user.service-url}") String userServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(userServiceUrl).build();
    }

    public boolean isPro(String bearerToken) {
        try {
            ApiResponse<String> response = restClient.get()
                    .uri("/internal/users/plan")
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .body(PLAN_TYPE);
            return response != null && "PRO".equalsIgnoreCase(response.data());
        } catch (RuntimeException error) {
            // 플랜 서버 장애나 검증 실패 때 유료 기능을 열어버리지 않는 fail-closed 정책.
            log.warn("Custom Scenario PRO 플랜 확인 실패: {}", error.getMessage());
            return false;
        }
    }
}
