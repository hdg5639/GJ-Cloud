package gj.cloud.user.global.config;

import gj.cloud.user.global.jwt.InternalJwtValidator;
import gj.cloud.user.global.jwt.InternalAutomationJwtValidator;
import gj.cloud.user.global.jwt.InternalPlanJwtValidator;
import gj.cloud.user.global.jwt.InternalServiceJwtValidator;
import gj.cloud.user.global.jwt.JwtValidator;
import gj.cloud.user.global.security.InternalJwtAuthenticationFilter;
import gj.cloud.user.global.security.InternalAutomationJwtAuthenticationFilter;
import gj.cloud.user.global.security.InternalPlanJwtAuthenticationFilter;
import gj.cloud.user.global.security.InternalServiceJwtAuthenticationFilter;
import gj.cloud.user.global.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtValidator jwtValidator;
    private final InternalJwtValidator internalJwtValidator;
    private final InternalPlanJwtValidator internalPlanJwtValidator;
    private final InternalAutomationJwtValidator internalAutomationJwtValidator;
    private final InternalServiceJwtValidator internalServiceJwtValidator;

    @Autowired(required = false)
    private CorsConfigurationSource corsConfigurationSource;

    // 최종 사용자 위임(delegation) 전용 — VM 서비스가 사용자를 대신해 호출할 때. VM은 자기 자신의
    // client-credentials가 아니라 최종 사용자가 원래 갖고 있던 aud=vm-service 토큰을 그대로 포워딩하고,
    // User는 그 토큰의 aud만 확인한다(누구를 대신하는 조회인지는 각 엔드포인트가 sub로 판단).
    // /internal/profiles/search(조직 초대용 사용자 검색)도 여기 포함 — "위임"이 곧 "본인 리소스만 조회"를
    // 뜻하진 않음: vm이 이미 자기 쪽에서 조직 ADMIN 권한을 확인한 뒤에만 이 경로를 호출하므로, User 입장에선
    // "vm을 거쳐 온 정상 로그인 사용자"라는 사실만 확인하면 충분하다. 아래 순수 서비스 전용 체인보다 먼저
    // 매칭돼야 함 (더 좁은 경로가 우선). /internal/users/plan은 아래 별도 체인에서 처리(aud 완화 필요).
    @Bean
    @Order(1)
    public SecurityFilterChain internalDelegatedFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/internal/ssh-keys/**", "/internal/profiles/search")
                .csrf(AbstractHttpConfigurer::disable);

        if (corsConfigurationSource != null) {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        } else {
            http.cors(AbstractHttpConfigurer::disable);
        }

        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                )
                .addFilterBefore(new InternalJwtAuthenticationFilter(internalJwtValidator),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // /internal/users/plan 전용 — vm이 사용자를 대신해 직접 호출할 때(aud=vm-service)뿐 아니라, Ops가
    // 자동배포 파이프라인에서 vm에 전달한 요청을 vm이 그대로 재포워딩할 때(aud=ops-service, PRO 커스텀
    // CNAME 검증용)도 허용해야 해서 위 위임 체인과 분리(InternalPlanJwtValidator 참고).
    @Bean
    @Order(2)
    public SecurityFilterChain internalPlanFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/internal/users/plan")
                .csrf(AbstractHttpConfigurer::disable);

        if (corsConfigurationSource != null) {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        } else {
            http.cors(AbstractHttpConfigurer::disable);
        }

        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                )
                .addFilterBefore(new InternalPlanJwtAuthenticationFilter(internalPlanJwtValidator),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain internalAutomationFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/internal/automation/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        (req, res, ex) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(new InternalAutomationJwtAuthenticationFilter(internalAutomationJwtValidator),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // 순수 서비스-간 신원 전용 — Auth 서비스만 호출 가능(프로필 생성/삭제). SEC-005: 이전에 프로필 생성이
    // permitAll이었던 것을 포함해 전부 인증 요구로 전환.
    @Bean
    @Order(4)
    public SecurityFilterChain internalServiceFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable);

        if (corsConfigurationSource != null) {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        } else {
            http.cors(AbstractHttpConfigurer::disable);
        }

        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                )
                .addFilterBefore(new InternalServiceJwtAuthenticationFilter(internalServiceJwtValidator),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(5)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable);

        if (corsConfigurationSource != null) {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        } else {
            http.cors(AbstractHttpConfigurer::disable);
        }

        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/webjars/**",
                                "/users/uploads/profile-images/**"
                        ).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtValidator), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
