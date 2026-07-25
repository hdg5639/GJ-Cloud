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
        boolean responseIsArray
) {
    public boolean hasPathParam() {
        return path != null && path.contains("{");
    }
}
