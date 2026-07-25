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
        List<String> evidence
) {
    public static String idOf(String resourceName, CapabilityType type) {
        return resourceName + "." + type.name().toLowerCase();
    }
}
