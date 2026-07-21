package gj.cloud.ops.global.security;

import com.nimbusds.jwt.JWTClaimsSet;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.jwt.JwtValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtValidator jwtValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            try {
                JWTClaimsSet claims = jwtValidator.validate(token);
                String userId = claims.getSubject();
                String email = (String) claims.getClaim("email");
                String role = (String) claims.getClaim("role");

                OpsPrincipal principal = new OpsPrincipal(userId, email);
                var auth = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (OpsException ignored) {
                // Invalid token — continue without auth; endpoints will 401
            }
        }
        chain.doFilter(request, response);
    }

    // SEC-006: 배포 진행상황 SSE(DeploymentController.events)는 EventSource가 아니라 fetch
    // 스트리밍(Frontend/portal/lib/api-client.ts의 deployments.streamEvents)으로 Authorization
    // 헤더를 그대로 붙여 호출하므로 쿼리파라미터 인증이 애초에 필요하지 않다 — 이 필터가 적용되는
    // 모든 비-internal 공개 엔드포인트에 걸려 있던 ?token= 폴백은 실사용처가 없는 채로 원본 액세스
    // 토큰을 URL/로그에 노출시키는 경로만 열어두고 있었으므로 제거한다.
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
