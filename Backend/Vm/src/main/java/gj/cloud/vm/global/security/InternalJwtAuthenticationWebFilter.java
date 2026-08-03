package gj.cloud.vm.global.security;

import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.jwt.InternalJwtValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

// CICD-003: @Component로 두면 SecurityConfig의 특정 체인 스코핑과 무관하게 전역 WebFlux 필터로도
// 중복 등록돼버리므로(JwtAuthenticationWebFilter 참고) 컴포넌트 스캔에서 빼고 SecurityConfig에서
// 직접 생성한다.
@RequiredArgsConstructor
public class InternalJwtAuthenticationWebFilter implements WebFilter {

    private final InternalJwtValidator internalJwtValidator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = extractToken(exchange);
        if (token == null) {
            return chain.filter(exchange);
        }

        return internalJwtValidator.validate(token)
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

    private String extractToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
