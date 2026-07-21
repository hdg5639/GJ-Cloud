package gj.cloud.vm.global.security;

import gj.cloud.vm.global.sse.SseTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

// SEC-006: /vms/events/subscribe, /vms/{id}/metrics/stream 전용 — Authorization 헤더 대신 1회용
// ?ticket= 쿼리파라미터만 받는다. 일반 JwtAuthenticationWebFilter의 광범위한 ?token= 폴백을 대체.
@Component
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
