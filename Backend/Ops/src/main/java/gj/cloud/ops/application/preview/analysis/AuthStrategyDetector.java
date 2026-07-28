package gj.cloud.ops.application.preview.analysis;

import org.springframework.stereotype.Component;

import java.util.List;

// auto-preview-design/04-api-binding-schema.md §9 — 문서의 securitySchemes에서 인증된 요청에 토큰을
// 실제로 어떻게 실어 보낼지 결정론적으로 뽑는다. Bearer를 API Key보다 우선한다 — 둘 다 선언된 문서라면
// 보통 Bearer가 사람이 로그인해서 받는 진짜 자격증명이고 API Key는 서비스 간 호출용인 경우가 많다.
@Component
public class AuthStrategyDetector {

    private static final String DEFAULT_API_KEY_HEADER_NAME = "X-API-Key";

    public AuthStrategy detect(List<SecuritySchemeEvidence> schemes) {
        return schemes.stream()
                .filter(SecuritySchemeEvidence::isBearer)
                .findFirst()
                .<AuthStrategy>map(scheme -> AuthStrategy.bearer())
                .or(() -> schemes.stream()
                        .filter(SecuritySchemeEvidence::isApiKey)
                        .filter(SecuritySchemeEvidence::isSupportedByMvp)
                        .findFirst()
                        .map(this::toApiKeyStrategy))
                .orElseGet(AuthStrategy::none);
    }

    private AuthStrategy toApiKeyStrategy(SecuritySchemeEvidence scheme) {
        String paramName = scheme.paramName() != null && !scheme.paramName().isBlank()
                ? scheme.paramName()
                : DEFAULT_API_KEY_HEADER_NAME;
        if ("query".equalsIgnoreCase(scheme.in())) {
            return AuthStrategy.apiKeyQuery(paramName);
        }
        return AuthStrategy.apiKeyHeader(paramName);
    }
}
