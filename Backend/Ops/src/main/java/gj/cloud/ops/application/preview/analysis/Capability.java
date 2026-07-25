package gj.cloud.ops.application.preview.analysis;

import java.util.List;

// GamjaBox_2.0_Key_Features.md 14절의 Capability 객체를 MVP 수준으로 축소한 것.
// id는 "{resourceName}.{type}" 형태(예: "vm.list") — 사람이 읽는 태그이자 PageDraft가 참조하는 키.
public record Capability(
        String id,
        String resourceName,
        CapabilityType type,
        String operationId,
        String path,
        String method,
        boolean hasSearch,
        boolean hasSort,
        boolean hasPagination,
        String confidence,     // RepositoryEvidence.CONFIDENCE_* 값 재사용(HIGH/MEDIUM/LOW)
        List<String> evidence,
        // CREATE/UPDATE/LOGIN의 요청 본문 필드명(ApiOperationEvidence.requestBodyFields 그대로) —
        // Phase C 렌더러가 폼/로그인 입력칸을 실제 API 스키마에 맞춰 그릴 때 필요. LIST/DETAIL/DELETE는 항상 빈 리스트.
        List<String> fields,
        // LOGIN 응답에서 access token이 위치한 dot-path(예: "data.accessToken") — 이름 힌트로 추론하지 못하면
        // null이고 PreviewAnalysisService가 unresolved로 표시해 사용자가 직접 지정하게 한다. LOGIN 외 타입은 항상 null.
        String accessTokenPath
) {
    public static String idOf(String resourceName, CapabilityType type) {
        return resourceName + "." + type.name().toLowerCase();
    }
}
