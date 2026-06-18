package gj.cloud.user.application.vm.client;

import gj.cloud.user.global.config.VmServiceProperties;
import gj.cloud.user.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class VmServiceClient {

    private static final ParameterizedTypeReference<ApiResponse<Long>> VM_COUNT_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public VmServiceClient(VmServiceProperties props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.getServiceUrl())
                .build();
    }

    public long getVmCount(String userId, String bearerToken) {
        try {
            ApiResponse<Long> response = restClient.get()
                    .uri("/internal/vms/count?userId={userId}", userId)
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .body(VM_COUNT_RESPONSE_TYPE);
            return response != null && response.data() != null ? response.data() : 0L;
        } catch (Exception e) {
            log.warn("VM 카운트 조회 실패: userId={}, error={}", userId, e.getMessage());
            return 0L;
        }
    }
}
