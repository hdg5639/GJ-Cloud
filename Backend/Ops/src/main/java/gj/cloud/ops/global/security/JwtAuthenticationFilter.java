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

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        // SSE(EventSource)는 헤더 설정이 불가능하므로 쿼리 파라미터 fallback (VM 서비스와 동일한 관례).
        // 이 파라미터가 access log에 남지 않도록 리버스 프록시 로그 설정에서 제외 처리 필요 (D.8)
        String queryToken = request.getParameter("token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken;
        }
        return null;
    }
}
