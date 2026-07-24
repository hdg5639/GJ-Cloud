package gj.cloud.user.global.security;

import gj.cloud.user.global.exception.UserException;
import gj.cloud.user.global.jwt.InternalAutomationJwtValidator;
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
public class InternalAutomationJwtAuthenticationFilter extends OncePerRequestFilter {

    private final InternalAutomationJwtValidator validator;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            try {
                String clientId = validator.validate(header.substring(7)).getSubject();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                clientId, null, List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
            } catch (UserException ignored) {
                // 인증 체인에서 401로 처리
            }
        }
        chain.doFilter(request, response);
    }
}
