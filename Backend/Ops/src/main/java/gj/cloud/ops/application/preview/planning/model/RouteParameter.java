package gj.cloud.ops.application.preview.planning.model;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §5 — 페이지 route가 필요로
// 하는 파라미터 하나. source는 지금 문서 예시가 "navigation" 하나뿐이라 닫힌 enum으로 못박지 않고
// 열린 문자열로 둔다(예상 값: "navigation"=Navigation Rule의 parameters에서 주입, "query"=쿼리
// 파라미터, "context"=Flow context에서 주입 — §6/§7이 실제로 채우기 전까지는 참고용).
// PagePlanMapper는 아직 이 필드를 채우지 않는다(Navigation, §7, 우선순위 4번 작업의 몫).
public record RouteParameter(
        String name,
        String source
) {
}
