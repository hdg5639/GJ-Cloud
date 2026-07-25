package gj.cloud.ops.application.preview.analysis;

// auto-preview-design/04-api-binding-schema.md §9 — 인증된 요청에 토큰을 실제로 어떻게 실어 보낼지
// 정의한다. Capability 하나가 아니라 문서 전체(product) 단위로 하나만 존재한다 — 로그인으로 받은
// 자격증명을 나머지 모든 보호된 요청에 동일한 방식으로 붙이기 때문.
public record AuthStrategy(
        String type,            // NONE | BEARER | API_KEY_HEADER | API_KEY_QUERY
        // BEARER/API_KEY_HEADER일 때 실제 헤더 이름(예: "Authorization", "X-API-Key"). 그 외 null.
        String headerName,
        // BEARER일 때만 값 앞에 붙일 접두사(예: "Bearer "). 그 외 null.
        String prefix,
        // API_KEY_QUERY일 때만 실제 쿼리 파라미터 이름. 그 외 null.
        String queryParamName
) {
    public static AuthStrategy none() {
        return new AuthStrategy("NONE", null, null, null);
    }

    public static AuthStrategy bearer() {
        return new AuthStrategy("BEARER", "Authorization", "Bearer ", null);
    }

    public static AuthStrategy apiKeyHeader(String headerName) {
        return new AuthStrategy("API_KEY_HEADER", headerName, null, null);
    }

    public static AuthStrategy apiKeyQuery(String queryParamName) {
        return new AuthStrategy("API_KEY_QUERY", null, null, queryParamName);
    }
}
