package gj.cloud.ops.application.preview.analysis;

// OpenAPI components.securitySchemes 항목 하나를 그대로 옮긴 것 — 해석(로그인 방식 판단)은
// CapabilityExtractor가 담당하고, 여기서는 원문 필드만 결정론적으로 옮겨 담는다.
public record SecuritySchemeEvidence(
        String name,
        String type,       // http | apiKey | oauth2 | openIdConnect | mutualTLS
        String scheme,     // type=http일 때: bearer | basic 등
        String in,          // type=apiKey일 때: header | query | cookie
        String paramName   // type=apiKey일 때 헤더/쿼리/쿠키 이름
) {
    public boolean isBearer() {
        return "http".equalsIgnoreCase(type) && "bearer".equalsIgnoreCase(scheme);
    }

    public boolean isApiKey() {
        return "apiKey".equalsIgnoreCase(type);
    }

    public boolean isSupportedByMvp() {
        return isBearer() || isApiKey();
    }
}
