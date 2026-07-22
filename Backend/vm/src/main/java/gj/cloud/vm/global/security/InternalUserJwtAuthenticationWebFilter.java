package gj.cloud.vm.global.security;

import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.jwt.InternalUserJwtValidator;
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
public class InternalUserJwtAuthenticationWebFilter implements WebFilter {

    private final InternalUserJwtValidator internalUserJwtValidator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = extractToken(exchange);
        if (token == null) {
            return chain.filter(exchange);
        }

        return internalUserJwtValidator.validate(token)
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
