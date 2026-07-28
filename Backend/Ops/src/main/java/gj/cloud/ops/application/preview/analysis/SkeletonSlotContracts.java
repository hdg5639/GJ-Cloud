package gj.cloud.ops.application.preview.analysis;

import java.util.Map;

// auto-preview-design/03-slot-contract.md §7·10 — PreviewPageRenderer.tsx에 암묵적으로 있던 Slot
// 구조(그리드 첫 열=page.main, 우측 상세=page.aside, 모달 영역=page.overlay 등)를 그대로 선언형으로
// 옮긴 것. 문서가 명시한 대로 "첫 버전부터 임의 Slot을 많이 만들지 않는다" — page.header/summary/
// toolbar/footer 등 문서가 나열한 다른 표준 Slot은 지금 어떤 스켈레톤도 실제로 쓰지 않으므로 추가하지
// 않는다. page.actions는 Direction Recovery Change Request Increment 4 command 계열에서 처음으로
// 실제 사용처(quick-action-button-group)가 생겨 추가했다. RESOURCE_LIST/LIST_DETAIL은
// PreviewBlockResolver와 마찬가지로 동일하게 취급한다.
public final class SkeletonSlotContracts {

    public static final Map<PageSkeletonType, Map<String, Cardinality>> ALL = Map.of(
            PageSkeletonType.AUTH_PAGE, Map.of(
                    "page.content", Cardinality.EXACTLY_ONE),
            PageSkeletonType.DASHBOARD, Map.of(
                    "page.content", Cardinality.EXACTLY_ONE),
            PageSkeletonType.RESOURCE_LIST, Map.of(
                    "page.main", Cardinality.EXACTLY_ONE,
                    "page.aside", Cardinality.ZERO_OR_ONE,
                    "page.overlay", Cardinality.ZERO_OR_MORE,
                    "page.actions", Cardinality.ZERO_OR_ONE,
                    "page.secondary", Cardinality.ZERO_OR_MORE),
            PageSkeletonType.RESOURCE_DETAIL, Map.of(
                    "page.primary", Cardinality.EXACTLY_ONE,
                    "page.actions", Cardinality.ZERO_OR_ONE,
                    "page.secondary", Cardinality.ZERO_OR_MORE,
                    "page.overlay", Cardinality.ZERO_OR_MORE),
            PageSkeletonType.LIST_DETAIL, Map.of(
                    "page.main", Cardinality.EXACTLY_ONE,
                    "page.aside", Cardinality.ZERO_OR_ONE,
                    "page.overlay", Cardinality.ZERO_OR_MORE,
                    "page.actions", Cardinality.ZERO_OR_ONE,
                    "page.secondary", Cardinality.ZERO_OR_MORE)
    );

    private SkeletonSlotContracts() {
    }
}
