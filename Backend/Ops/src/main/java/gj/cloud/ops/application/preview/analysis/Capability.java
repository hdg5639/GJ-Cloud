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
        List<String> fields
) {
    public static String idOf(String resourceName, CapabilityType type) {
        return resourceName + "." + type.name().toLowerCase();
    }
}
