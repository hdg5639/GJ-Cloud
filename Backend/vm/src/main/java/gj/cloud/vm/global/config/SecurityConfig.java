package gj.cloud.vm.global.config;

import gj.cloud.vm.global.security.InternalJwtAuthenticationWebFilter;
import gj.cloud.vm.global.security.InternalOpsJwtAuthenticationWebFilter;
import gj.cloud.vm.global.security.InternalUserJwtAuthenticationWebFilter;
import gj.cloud.vm.global.security.JwtAuthenticationWebFilter;
import gj.cloud.vm.global.security.SseTicketAuthenticationWebFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationWebFilter jwtAuthenticationWebFilter;
    private final InternalJwtAuthenticationWebFilter internalJwtAuthenticationWebFilter;
    private final InternalOpsJwtAuthenticationWebFilter internalOpsJwtAuthenticationWebFilter;
    private final InternalUserJwtAuthenticationWebFilter internalUserJwtAuthenticationWebFilter;
    private final SseTicketAuthenticationWebFilter sseTicketAuthenticationWebFilter;

    // /internal/ops/** 전용: Ops 서비스(aud=ops-service)만 호출 가능. 아래 일반 /internal/** 체인보다 먼저 매칭돼야 함
    @Bean
    @Order(1)
    public SecurityWebFilterChain internalOpsFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/internal/ops/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(auth -> auth.anyExchange().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((exchange, ex) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        })
                )
                .addFilterAt(internalOpsJwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    // /internal/vms/count, /internal/vms/usage 전용: User 서비스가 최종 사용자를 대신해 호출
    // (aud=user-service 위임 토큰 포워딩 — UsageServiceImpl 참고). 아래 일반 /internal/** 체인은
    // 순수 서비스 신원(client_id=auth-service)만 인정해서 이 위임 토큰을 거부하므로, 더 좁은 이 경로가
    // 먼저 매칭되도록 앞에 둬야 함.
    @Bean
    @Order(2)
    public SecurityWebFilterChain internalUserDelegatedFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/internal/vms/count", "/internal/vms/usage"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(auth -> auth.anyExchange().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((exchange, ex) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        })
                )
                .addFilterAt(internalUserJwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    @Order(3)
    public SecurityWebFilterChain internalFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/internal/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(auth -> auth.anyExchange().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((exchange, ex) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        })
                )
                .addFilterAt(internalJwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    // SEC-006: SSE 구독 전용 티켓 인증 체인. 아래 일반 체인의 광범위한 인증(및 과거 ?token= 폴백)보다
    // 먼저 매칭돼야 하며, 이 두 경로에는 일반 Authorization 헤더 인증을 적용하지 않는다.
    @Bean
    @Order(4)
    public SecurityWebFilterChain sseTicketFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/vms/events/subscribe", "/vms/*/metrics/stream"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(auth -> auth.anyExchange().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((exchange, ex) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        })
                )
                .addFilterAt(sseTicketAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    @Order(5)
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(auth -> auth
                        .pathMatchers(
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/webjars/**"
                        ).permitAll()
                        .pathMatchers("/admin/**").hasRole("ADMIN")
                        .anyExchange().authenticated()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((exchange, ex) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        })
                        .accessDeniedHandler((exchange, ex) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        })
                )
                .addFilterAt(jwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

}
