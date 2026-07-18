package gj.cloud.ops.global.security;

import com.nimbusds.jwt.JWTClaimsSet;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.jwt.InternalJwtValidator;
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
        return null;
    }
}
