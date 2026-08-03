package gj.cloud.vm.global.security;

import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.jwt.InternalAutomationJwtValidator;
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

@RequiredArgsConstructor
public class InternalAutomationJwtAuthenticationWebFilter implements WebFilter {

    private final InternalAutomationJwtValidator validator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }
        return validator.validate(header.substring(7))
                .flatMap(claims -> chain.filter(exchange).contextWrite(
                        ReactiveSecurityContextHolder.withAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        claims.getSubject(),
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))))))
                .onErrorResume(VmException.class, e -> chain.filter(exchange));
    }
}
