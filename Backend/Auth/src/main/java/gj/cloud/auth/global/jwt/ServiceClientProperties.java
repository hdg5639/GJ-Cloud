package gj.cloud.auth.global.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

// OPS-SEC-002: 서비스가 자신의 신원을 증명할 client_id/client_secret을 등록해두는 설정.
// audience/scope는 클라이언트가 요청 시 지정하는 게 아니라 여기서 서버가 결정 —
// 클라이언트가 자신에게 허용되지 않은 audience/scope를 자칭할 수 없도록 함.
@ConfigurationProperties(prefix = "auth.service-clients")
@Getter
@Setter
public class ServiceClientProperties {

    private Map<String, ServiceClient> clients = new HashMap<>();

    @Getter
    @Setter
    public static class ServiceClient {
        private String secret;
        private String audience;
        private String scope;
    }
}
