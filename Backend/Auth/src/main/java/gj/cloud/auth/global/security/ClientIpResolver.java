package gj.cloud.auth.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// SEC-009: 이전에는 X-Forwarded-For의 첫 홉(클라이언트가 자유롭게 지정 가능)을 그대로 신뢰해서,
// 아무 요청에나 X-Forwarded-For: 1.2.3.4를 붙이면 LoginRateLimiter의 IP별 잠금(20회/15분)을
// 매번 새 IP로 우회할 수 있었음. 이 스택은 Cloudflare Tunnel을 앞단에 두므로(CF-Connecting-IP는
// Cloudflare 엣지가 직접 설정하며 이후 어떤 클라이언트도 덮어쓸 수 없음) 이를 최우선으로 신뢰하고,
// 없을 때만 X-Forwarded-For의 마지막 홉(자체 리버스 프록시가 추가한 가장 안쪽 값)을 사용한다.
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (StringUtils.hasText(cfConnectingIp)) {
            return cfConnectingIp.trim();
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }

        return request.getRemoteAddr();
    }
}
