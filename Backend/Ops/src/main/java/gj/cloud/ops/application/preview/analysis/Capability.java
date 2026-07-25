package gj.cloud.ops.application.preview.analysis;

import java.util.List;

// GamjaBox_2.0_Key_Features.md 14절의 Capability 객체를 MVP 수준으로 축소한 것.
// id는 "{resourceName}.{type}" 형태(예: "vm.list") — 사람이 읽는 태그이자 PageDraft가 참조하는 키.
public record Capability(
        String id,
        String resourceName,
        // kind=COMMAND(커맨드형 오퍼레이션)일 때는 null — CRUD 6종 enum에 억지로 끼워 넣지 않는다.
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
        String accessTokenPath,
        // hasSearch=true일 때 실제로 감지된 쿼리 파라미터 이름(예: "keyword") — 렌더러가 "search"로
        // 하드코딩해 보내면 API가 다른 이름을 쓸 때 검색이 조용히 실패한다. LIST 외 타입/미감지 시 null.
        String searchParam,
        // auto-preview-design/05-capability-taxonomy.md의 위험도/자동실행 정책 축소판. CapabilityType별
        // 고정 기본값만 매긴다(예: DELETE→DESTRUCTIVE) — IRREVERSIBLE·EXTERNAL_SIDE_EFFECT는 OpenAPI
        // 만으로 결정론적으로 판별할 근거가 없어 규칙 기반으로는 절대 자동 배정하지 않는다.
        RiskLevel risk,
        // risk의 기본 매핑값을 그대로 저장한다(05번 문서 §6 표). 지금은 참고용 메타데이터일 뿐 —
        // MVP에는 아직 이 값으로 실행을 막는 자동화 파이프라인 자체가 없다.
        AutomationPolicy automationPolicy,
        // auto-preview-design/04-api-binding-schema.md §7.2 — LIST 응답에서 실제 배열이 위치한
        // dot-path(예: "data.content"). 못 찾으면 null이고 렌더러가 기존 런타임 재귀 휴리스미(extractArray)로
        // 대체한다. LIST 외 타입은 항상 null.
        String collectionPath,
        // 같은 절의 totalCountPath — LIST 응답에서 총 개수가 위치한 dot-path(예: "data.totalElements").
        // 못 찾으면 null이고 렌더러가 배열 길이로 대체한다(extractCount). LIST 외 타입은 항상 null.
        String totalCountPath,
        // GamjaBox_Auto_Preview_Direction_Recovery_Change_Request.md §7.1 — capability의 진짜 정체성.
        // CRUD/LOGIN은 QUERY/MUTATION/AUTH로 역산해서 채우고, `/resource/{id}/action` 형태의 커맨드형
        // 오퍼레이션은 COMMAND로 표현한다(이 경우 type()은 null — CRUD 6종 enum에 억지로 끼워 넣지 않음).
        CapabilityKind kind,
        // kind=COMMAND일 때 실제 동작 이름(예: "start", "invite"). 그 외 kind는 항상 null.
        String action,
        // 이 capability가 의미상 의존하는 다른 capability id 목록(예: vm.start → ["vm.detail"]).
        // COMMAND 외 kind는 항상 빈 리스트.
        List<String> dependencies
) {
    public static String idOf(String resourceName, CapabilityType type) {
        return resourceName + "." + type.name().toLowerCase();
    }
}
