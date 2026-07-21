package gj.cloud.auth.global.security;

import com.nimbusds.jwt.JWTClaimsSet;
import gj.cloud.auth.global.exception.AuthException;
import gj.cloud.auth.global.jwt.InternalJwtValidator;
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
public class InternalJwtAuthenticationFilter extends OncePerRequestFilter {

    private final InternalJwtValidator internalJwtValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            try {
                JWTClaimsSet claims = internalJwtValidator.validate(token);
                String clientId = claims.getSubject();

                var auth = new UsernamePasswordAuthenticationToken(
                        clientId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (AuthException ignored) {
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
