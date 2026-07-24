package gj.cloud.vm.global.security;

import gj.cloud.vm.global.sse.SseTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

// SEC-006: /vms/events/subscribe, /vms/{id}/metrics/stream 전용 — Authorization 헤더 대신 1회용
// ?ticket= 쿼리파라미터만 받는다. 일반 JwtAuthenticationWebFilter의 광범위한 ?token= 폴백을 대체.
// CICD-003: 이 필터는 티켓이 없으면 무조건 401(다른 필터들처럼 그냥 통과시키지 않음)이라, 예전에
// @Component로 컴포넌트 스캔에 걸려 있었을 때 Spring Boot가 이걸 SecurityConfig의 체인 스코핑과
// 무관하게 전역 WebFlux 필터로도 중복 등록해버려서, ?ticket= 없는 모든 요청(예: /actuator/health)이
// 전역 필터 실행 순서에 따라 간헐적으로 401을 받는 버그가 있었다. 컴포넌트 스캔에서 빼고
// SecurityConfig에서 직접 생성해 원래 의도한 두 경로에만 적용되도록 함.
@RequiredArgsConstructor
public class SseTicketAuthenticationWebFilter implements WebFilter {

    private final SseTicketService sseTicketService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ticket = exchange.getRequest().getQueryParams().getFirst("ticket");
        if (!StringUtils.hasText(ticket)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return sseTicketService.consumeTicket(ticket)
                .flatMap(payload -> {
                    VmPrincipal principal = new VmPrincipal(payload.userId(), payload.email());
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }));
    }
}
