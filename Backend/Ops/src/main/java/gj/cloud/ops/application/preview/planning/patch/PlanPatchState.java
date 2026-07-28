package gj.cloud.ops.application.preview.planning.patch;

import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.planning.model.PagePlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// AI/사용자 Patch가 실제로 수정하는 정본. PageDraft만 고친 뒤 PagePlan/Flow를 다시 생성하던 기존
// 구조에서는 SET_LAYOUT/ADD_NAVIGATION/ADD_FLOW가 다음 요청에서 사라졌으므로 세 모델을 한 상태로 묶는다.
public record PlanPatchState(
        List<PagePlan> pagePlans,
        List<FlowBlueprint> flows,
        List<ApiBinding> bindings
) {
    public PlanPatchState {
        pagePlans = immutableList(pagePlans);
        flows = immutableList(flows);
        bindings = immutableList(bindings);
    }

    // 외부 JSON의 null element도 생성자 NPE로 500을 내지 않고 Validator가 구체적인 오류로
    // 보고할 수 있게, 컬렉션 자체만 불변 복사하고 element null은 보존한다.
    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
