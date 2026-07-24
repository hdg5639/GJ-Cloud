package gj.cloud.user.global.security;

import com.nimbusds.jwt.JWTClaimsSet;
import gj.cloud.user.global.exception.UserException;
import gj.cloud.user.global.jwt.InternalPlanJwtValidator;
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

// CICD-003: 다른 Filter 클래스들과 동일하게 @Component를 붙이지 않음 — SecurityConfig가 직접 new로
// 생성해 특정 체인에만 addFilterBefore로 등록한다(전역 자동 등록 방지).
@RequiredArgsConstructor
public class InternalPlanJwtAuthenticationFilter extends OncePerRequestFilter {

    private final InternalPlanJwtValidator internalPlanJwtValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            try {
                JWTClaimsSet claims = internalPlanJwtValidator.validate(token);
                String userId = claims.getSubject();
                String email = (String) claims.getClaim("email");
                String role = (String) claims.getClaim("role");

                UserPrincipal principal = new UserPrincipal(userId, email);
                var auth = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (UserException ignored) {
                // 유효하지 않은 토큰 — 인증 없이 진행, 엔드포인트에서 401 처리
            }
        }
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
