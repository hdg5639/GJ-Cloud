package gj.cloud.auth.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// SEC-009: CF-Connecting-IP(신뢰 가능한 엣지 설정값) 최우선, 없으면 X-Forwarded-For의
// 마지막 홉, 그것도 없으면 remoteAddr로 폴백하는 순서를 검증.
class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    @Test
    void prefersCfConnectingIpOverEverythingElse() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("CF-Connecting-IP")).thenReturn("1.1.1.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 2.2.2.2");
        when(request.getRemoteAddr()).thenReturn("3.3.3.3");

        assertThat(resolver.resolve(request)).isEqualTo("1.1.1.1");
    }

    @Test
    void fallsBackToLastHopOfForwardedForWhenCfHeaderMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 2.2.2.2");
        when(request.getRemoteAddr()).thenReturn("3.3.3.3");

        assertThat(resolver.resolve(request)).isEqualTo("2.2.2.2");
    }

    @Test
    void fallsBackToRemoteAddrWhenNoHeadersPresent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("3.3.3.3");

        assertThat(resolver.resolve(request)).isEqualTo("3.3.3.3");
    }

    @Test
    void treatsBlankCfHeaderAsAbsent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("CF-Connecting-IP")).thenReturn("   ");
        when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9");
        when(request.getRemoteAddr()).thenReturn("3.3.3.3");

        assertThat(resolver.resolve(request)).isEqualTo("9.9.9.9");
    }
}
