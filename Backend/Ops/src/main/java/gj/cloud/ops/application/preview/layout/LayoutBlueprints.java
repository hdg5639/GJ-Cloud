package gj.cloud.ops.application.preview.layout;

import gj.cloud.ops.application.preview.analysis.Cardinality;

import java.util.List;
import java.util.Map;

// §10 "Minimum initial layouts" 7종을 그대로 등록한 정적 카탈로그(ComponentContracts와 같은 관례 —
// 아직 등록/승격/버전 개념이 없는 고정 상수). auth-layout/dashboard-layout/resource-list-layout/
// list-detail-layout은 SkeletonSlotContracts(PageSkeletonType 4종 전용 Slot 맵)와 같은 Slot
// 구성이다 — 지금은 같은 정보를 두 자리에 들고 있는 셈이지만, SkeletonSlotContracts는
// SlotCardinalityValidator가 이미 실사용 중이라 이번 조각에서 대체하지 않는다(대체는 이 모델의 실제
// 소비자가 생기는 다음 조각에서 판단). resource-detail-layout은 §10 예시 JSON을 그대로 옮겼다 —
// page.main/page.aside 패턴과 Slot 이름 자체가 다른 유일한 레이아웃(문서가 그렇게 예시를 준 이유이기도
// 함). workflow-layout/settings-layout은 지금 어떤 PageType도 실제로 만들어내지 않아(PagePlan의
// pageType 11종 중 WORKFLOW/SETTINGS는 아직 어떤 생성기도 배정 안 함) 검증된 실사용 사례가 없는
// 자리채움 — 다른 레이아웃과 일관된 명명 관례로 만들었을 뿐 실제로 필요한 Slot 구성인지는 그 두
// PageType을 실제로 만드는 생성기가 생길 때 다시 봐야 한다.
public final class LayoutBlueprints {

    public static final Map<String, LayoutBlueprint> ALL = Map.of(
            "auth-layout", new LayoutBlueprint("auth-layout", "1", List.of(
                    new LayoutSlot("page.content", Cardinality.EXACTLY_ONE))),
            "dashboard-layout", new LayoutBlueprint("dashboard-layout", "1", List.of(
                    new LayoutSlot("page.content", Cardinality.EXACTLY_ONE))),
            "resource-list-layout", new LayoutBlueprint("resource-list-layout", "1", List.of(
                    new LayoutSlot("page.main", Cardinality.EXACTLY_ONE),
                    new LayoutSlot("page.aside", Cardinality.ZERO_OR_ONE),
                    new LayoutSlot("page.overlay", Cardinality.ZERO_OR_MORE),
                    new LayoutSlot("page.actions", Cardinality.ZERO_OR_ONE),
                    new LayoutSlot("page.secondary", Cardinality.ZERO_OR_MORE))),
            "list-detail-layout", new LayoutBlueprint("list-detail-layout", "1", List.of(
                    new LayoutSlot("page.main", Cardinality.EXACTLY_ONE),
                    new LayoutSlot("page.aside", Cardinality.ZERO_OR_ONE),
                    new LayoutSlot("page.overlay", Cardinality.ZERO_OR_MORE),
                    new LayoutSlot("page.actions", Cardinality.ZERO_OR_ONE),
                    new LayoutSlot("page.secondary", Cardinality.ZERO_OR_MORE))),
            // §10 예시 JSON 그대로.
            "resource-detail-layout", new LayoutBlueprint("resource-detail-layout", "1", List.of(
                    new LayoutSlot("page.header", Cardinality.EXACTLY_ONE),
                    new LayoutSlot("page.summary", Cardinality.ZERO_OR_MORE),
                    new LayoutSlot("page.primary", Cardinality.EXACTLY_ONE),
                    new LayoutSlot("page.secondary", Cardinality.ZERO_OR_MORE),
                    new LayoutSlot("page.actions", Cardinality.ZERO_OR_MORE),
                    new LayoutSlot("page.overlay", Cardinality.ZERO_OR_MORE))),
            "workflow-layout", new LayoutBlueprint("workflow-layout", "1", List.of(
                    new LayoutSlot("page.steps", Cardinality.EXACTLY_ONE),
                    new LayoutSlot("page.primary", Cardinality.EXACTLY_ONE),
                    new LayoutSlot("page.actions", Cardinality.ZERO_OR_MORE),
                    new LayoutSlot("page.overlay", Cardinality.ZERO_OR_MORE))),
            "settings-layout", new LayoutBlueprint("settings-layout", "1", List.of(
                    new LayoutSlot("page.nav", Cardinality.ZERO_OR_ONE),
                    new LayoutSlot("page.main", Cardinality.EXACTLY_ONE),
                    new LayoutSlot("page.actions", Cardinality.ZERO_OR_MORE)))
    );

    private LayoutBlueprints() {
    }
}
