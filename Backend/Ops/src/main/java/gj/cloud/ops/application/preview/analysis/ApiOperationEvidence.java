package gj.cloud.ops.application.preview.analysis;

import java.util.List;

// OpenAPI paths.{path}.{method} 오퍼레이션 하나를 결정론적으로 옮긴 것. requestBodyFields/responseIsArray는
// $ref를 한 단계(#/components/schemas/*)만 해석해 채운다 — 외부 $ref는 추가 네트워크 호출(SSRF 벡터)이 될
// 수 있어 절대 따라가지 않고 무시한다.
public record ApiOperationEvidence(
        String path,
        String method,             // GET | POST | PUT | PATCH | DELETE
        String operationId,
        String summary,
        List<String> tags,
        List<ApiParameterEvidence> parameters,
        List<String> requestBodyFields,
        boolean requiresAuth,
        boolean responseIsArray,
        // 응답 스키마에서 뽑은 스칼라(문자열/숫자/불리언) leaf 필드의 dot-path 목록(예: "data.accessToken").
        // 로그인 응답에서 access token이 어디 있는지, LIST 응답에서 총 개수가 어디 있는지 추론할 때 쓰인다.
        List<String> responseFieldPaths,
        // 응답 스키마에서 뽑은 배열 타입 필드의 dot-path 목록(예: "data.content"). LIST capability의
        // collectionPath(목록이 실제로 들어있는 위치)를 추론할 때만 쓰인다.
        List<String> arrayFieldPaths
) {
    public boolean hasPathParam() {
        return path != null && path.contains("{");
    }
}
