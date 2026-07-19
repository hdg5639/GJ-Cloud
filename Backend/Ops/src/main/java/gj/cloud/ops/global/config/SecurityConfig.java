package gj.cloud.ops.global.config;

import gj.cloud.ops.global.jwt.InternalJwtValidator;
import gj.cloud.ops.global.jwt.JwtValidator;
import gj.cloud.ops.global.security.InternalJwtAuthenticationFilter;
import gj.cloud.ops.global.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtValidator jwtValidator;
    private final InternalJwtValidator internalJwtValidator;

    // VM 서비스(aud=vm-service)가 관리 키 발급/폐기를 요청하는 내부 API 전용
    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
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

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 배포 SSE(SseEmitter)는 emitter가 완료/타임아웃/에러될 때 컨트롤러 스레드가 아닌
                        // 다른 스레드에서 컨테이너가 ASYNC 디스패치를 한 번 더 흘려보내는데, 이 시점엔
                        // SecurityContext가 없어 authenticated() 매칭에 걸려 AuthorizationDeniedException이
                        // 발생함 — 이미 SSE 응답이 커밋된 뒤라 에러 응답을 못 보내고 스트림이 깨져
                        // 클라이언트에 ERR_HTTP2_PROTOCOL_ERROR로 나타남. 최초 REQUEST 디스패치에서 이미
                        // 인증을 마쳤으므로 ASYNC/ERROR 재진입은 인가 재검사 대상에서 제외.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/webjars/**"
                        ).permitAll()
                        // 콘솔 WebSocket은 JWT가 아니라 일회용 티켓(Redis GETDEL)으로 별도 검증
                        .requestMatchers("/ws/terminal/**").permitAll()
                        // 미디어 미리보기 스트리밍(<video>/<audio> 태그가 커스텀 헤더 없이 직접 호출)도 JWT 대신 티켓으로 검증
                        .requestMatchers("/ops/*/files/stream").permitAll()
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
