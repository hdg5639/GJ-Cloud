package gj.cloud.ops.application.preview.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// auto-preview-design/04-api-binding-schema.md §9 회귀 테스트 — Bearer만 가정하던 예전 방식은 API Key
// 인증 API에서 요청이 항상 실패했다(§4.2 정보 손실 사례). 실제로 header/query 위치를 구분해 뽑아내는지 확인.
class AuthStrategyDetectorTest {

    private final AuthStrategyDetector detector = new AuthStrategyDetector();

    @Test
    void detectsBearer() {
        AuthStrategy strategy = detector.detect(List.of(
                new SecuritySchemeEvidence("bearerAuth", "http", "bearer", null, null)
        ));

        assertThat(strategy).isEqualTo(AuthStrategy.bearer());
    }

    @Test
    void detectsApiKeyInHeaderWithActualHeaderName() {
        AuthStrategy strategy = detector.detect(List.of(
                new SecuritySchemeEvidence("apiKeyAuth", "apiKey", null, "header", "X-API-Key")
        ));

        assertThat(strategy).isEqualTo(AuthStrategy.apiKeyHeader("X-API-Key"));
    }

    @Test
    void detectsApiKeyInQuery() {
        AuthStrategy strategy = detector.detect(List.of(
                new SecuritySchemeEvidence("apiKeyAuth", "apiKey", null, "query", "api_key")
        ));

        assertThat(strategy).isEqualTo(AuthStrategy.apiKeyQuery("api_key"));
    }

    @Test
    void prefersBearerOverApiKeyWhenBothArePresent() {
        AuthStrategy strategy = detector.detect(List.of(
                new SecuritySchemeEvidence("apiKeyAuth", "apiKey", null, "header", "X-API-Key"),
                new SecuritySchemeEvidence("bearerAuth", "http", "bearer", null, null)
        ));

        assertThat(strategy).isEqualTo(AuthStrategy.bearer());
    }

    @Test
    void treatsApiKeyInCookieAsUnsupportedRatherThanGuessingHeader() {
        AuthStrategy strategy = detector.detect(List.of(
                new SecuritySchemeEvidence("cookieAuth", "apiKey", null, "cookie", "SESSION")
        ));

        assertThat(strategy).isEqualTo(AuthStrategy.none());
    }

    @Test
    void returnsNoneWhenNoSchemesDeclared() {
        assertThat(detector.detect(List.of())).isEqualTo(AuthStrategy.none());
    }
}
