package gj.cloud.ops.application.preview.analysis;

import java.util.List;

// OpenAPI 문서 하나를 결정론적으로 정규화한 결과 — RepositoryEvidence와 같은 역할(AI 호출 전 항상 먼저
// 계산됨). 문서 원문(raw JSON/YAML)은 이 객체를 만든 뒤 버리고 저장/로깅하지 않는다.
public record OpenApiEvidence(
        String title,
        String description,
        String version,
        List<String> serverUrls,
        List<SecuritySchemeEvidence> securitySchemes,
        List<ApiOperationEvidence> operations,
        int truncatedOperationCount // 상한을 넘겨 분석에서 제외된 오퍼레이션 수 (0이면 전부 반영됨)
) {
    // 기존 테스트와 호출부가 description 도입 전 생성자를 계속 사용할 수 있도록 유지한다.
    public OpenApiEvidence(
            String title,
            String version,
            List<String> serverUrls,
            List<SecuritySchemeEvidence> securitySchemes,
            List<ApiOperationEvidence> operations,
            int truncatedOperationCount
    ) {
        this(title, null, version, serverUrls, securitySchemes, operations, truncatedOperationCount);
    }
}
