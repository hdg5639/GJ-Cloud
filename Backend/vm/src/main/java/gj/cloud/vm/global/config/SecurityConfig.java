package gj.cloud.vm.global.config;

import gj.cloud.vm.global.jwt.InternalJwtValidator;
import gj.cloud.vm.global.jwt.InternalAutomationJwtValidator;
import gj.cloud.vm.global.jwt.InternalOpsJwtValidator;
import gj.cloud.vm.global.jwt.InternalUserJwtValidator;
import gj.cloud.vm.global.jwt.JwtValidator;
import gj.cloud.vm.global.security.InternalJwtAuthenticationWebFilter;
import gj.cloud.vm.global.security.InternalAutomationJwtAuthenticationWebFilter;
import gj.cloud.vm.global.security.InternalOpsJwtAuthenticationWebFilter;
import gj.cloud.vm.global.security.InternalUserJwtAuthenticationWebFilter;
import gj.cloud.vm.global.security.JwtAuthenticationWebFilter;
import gj.cloud.vm.global.security.SseTicketAuthenticationWebFilter;
import gj.cloud.vm.global.sse.SseTicketService;
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

    // CICD-003: 커스텀 인증 WebFilter들은 여기서 특정 SecurityWebFilterChain에만 .addFilterAt으로
    // 넣을 의도였는데, 이 필터 클래스들이 @Component였을 때 Spring Boot가 WebFilter 타입 빈을
    // 전역 WebFlux 필터 체인에도 자동으로 같이 등록해버려서, 체인 스코핑과 무관하게 모든 요청에
    // 중복 적용되는 문제가 있었다(특히 SseTicketAuthenticationWebFilter는 티켓 없으면 무조건 401이라
    // /actuator/health 같은 무관한 경로까지 간헐적으로 401을 받는 버그로 이어짐). 그래서 필터
    // 클래스들은 컴포넌트 스캔에서 빼고, 실제 의존성(검증기/서비스)만 주입받아 여기서 직접 생성한다.
    private final JwtValidator jwtValidator;
    private final InternalJwtValidator internalJwtValidator;
    private final InternalAutomationJwtValidator internalAutomationJwtValidator;
    private final InternalOpsJwtValidator internalOpsJwtValidator;
    private final InternalUserJwtValidator internalUserJwtValidator;
    private final SseTicketService sseTicketService;

    @Bean
    @Order(1)
    public SecurityWebFilterChain internalAutomationFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/internal/automation/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(auth -> auth.anyExchange().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((exchange, ex) -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }))
                .addFilterAt(new InternalAutomationJwtAuthenticationWebFilter(internalAutomationJwtValidator),
                        SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    // /internal/ops/** 전용: Ops 서비스(aud=ops-service)만 호출 가능. 아래 일반 /internal/** 체인보다 먼저 매칭돼야 함
    @Bean
    @Order(2)
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
                .addFilterAt(new InternalOpsJwtAuthenticationWebFilter(internalOpsJwtValidator), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    // /internal/vms/count, /internal/vms/usage 전용: User 서비스가 최종 사용자를 대신해 호출
    // (aud=user-service 위임 토큰 포워딩 — UsageServiceImpl 참고). 아래 일반 /internal/** 체인은
    // 순수 서비스 신원(client_id=auth-service)만 인정해서 이 위임 토큰을 거부하므로, 더 좁은 이 경로가
    // 먼저 매칭되도록 앞에 둬야 함.
    @Bean
    @Order(3)
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
                .addFilterAt(new InternalUserJwtAuthenticationWebFilter(internalUserJwtValidator), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    @Order(4)
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
                .addFilterAt(new InternalJwtAuthenticationWebFilter(internalJwtValidator), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    // SEC-006: SSE 구독 전용 티켓 인증 체인. 아래 일반 체인의 광범위한 인증(및 과거 ?token= 폴백)보다
    // 먼저 매칭돼야 하며, 이 두 경로에는 일반 Authorization 헤더 인증을 적용하지 않는다.
    @Bean
    @Order(5)
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
                .addFilterAt(new SseTicketAuthenticationWebFilter(sseTicketService), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    @Order(6)
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
                .addFilterAt(new JwtAuthenticationWebFilter(jwtValidator), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

}
