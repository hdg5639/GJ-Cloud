package gj.cloud.vm.global.security;

import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.jwt.JwtValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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

@Component
@RequiredArgsConstructor
public class JwtAuthenticationWebFilter implements WebFilter {

    private final JwtValidator jwtValidator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = extractToken(exchange);
        if (token == null) {
            return chain.filter(exchange);
        }

        return jwtValidator.validate(token)
                .flatMap(claims -> {
                    String userId = claims.getSubject();
                    String email = (String) claims.getClaim("email");
                    String role = (String) claims.getClaim("role");

                    VmPrincipal principal = new VmPrincipal(userId, email);
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );

                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
                })
                .onErrorResume(VmException.class, e -> chain.filter(exchange));
    }

    // SEC-006: 과거 SSE용 ?token= 쿼리파라미터 폴백은 이 필터가 적용되는 모든 비-internal 공개
    // 엔드포인트에 걸려 있어 원본 액세스 토큰이 URL/로그에 노출되는 범위가 SSE보다 훨씬 넓었다.
    // SSE 두 경로는 SseTicketAuthenticationWebFilter의 1회용 티켓으로 완전히 대체했으므로 여기서는
    // Authorization 헤더만 인정한다.
    private String extractToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
