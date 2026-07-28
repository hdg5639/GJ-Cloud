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

    // apiKey는 in=header/query일 때만 실제로 요청에 실어 보내는 코드가 있다(AuthStrategy 참고).
    // in=cookie(구식 쿠키 인증 표현)는 아직 아무 데도 구현이 없어 "지원"이라고 하면 안 된다 — 예전엔
    // isApiKey()만 보고 전부 지원한다고 표시해서, 실제로는 요청이 실패하는데 분석 결과는 READY로
    // 나오는 불일치가 있었다.
    public boolean isSupportedByMvp() {
        if (isBearer()) {
            return true;
        }
        return isApiKey() && ("header".equalsIgnoreCase(in) || "query".equalsIgnoreCase(in));
    }
}
