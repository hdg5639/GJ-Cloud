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
        List<String> arrayFieldPaths,
        // 응답 스키마에서 뽑은 문자열 enum 필드(dot-path + 허용값). 상태 전이 폴링(AC-4) 감지에 쓰인다 —
        // CapabilityExtractor가 "status"류 필드의 enum에 전이값과 종료값이 함께 있는지 결정론적으로 본다.
        List<EnumFieldEvidence> enumFields
) {
    // 응답 스키마 안의 한 enum 문자열 필드. values는 OpenAPI에 선언된 원본 표기 그대로 보존한다
    // (런타임 poll 조건이 실제 API가 돌려주는 대문자 값과 매칭돼야 하므로 정규화하지 않는다).
    public record EnumFieldEvidence(String path, List<String> values) {
        public EnumFieldEvidence {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public ApiOperationEvidence {
        enumFields = enumFields == null ? List.of() : List.copyOf(enumFields);
    }

    // enumFields 도입 전 호출부/테스트 호환용 — enum 필드 없이 만들면 빈 목록으로 채운다.
    public ApiOperationEvidence(String path, String method, String operationId, String summary,
            List<String> tags, List<ApiParameterEvidence> parameters, List<String> requestBodyFields,
            boolean requiresAuth, boolean responseIsArray,
            List<String> responseFieldPaths, List<String> arrayFieldPaths) {
        this(path, method, operationId, summary, tags, parameters, requestBodyFields, requiresAuth,
                responseIsArray, responseFieldPaths, arrayFieldPaths, List.of());
    }

    public boolean hasPathParam() {
        return path != null && path.contains("{");
    }
}
