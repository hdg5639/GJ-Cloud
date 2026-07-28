package gj.cloud.ops.application.preview.binding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §4/§8 — "ApiBinding" 모델.
// FlowStep(flow 패키지, WP-2)의 bindingRef가 지금 문자열 참조일 뿐인 것을 실제로 채운다: 어떤
// Capability를 호출하는지, 요청을 어떻게 구성하는지(inputMappings), 응답에서 뭘 뽑아 context에
// 저장하는지(outputMappings), 성공 후 어떤 다른 바인딩을 새로고침할지(refreshBindingIds).
//
// §8이 별도로 언급하는 "context mappings"는 outputMappings의 목적지가 항상 context라 별도 필드로
// 안 둔다. "poll conditions"도 FlowStep.until(PollCondition, WP-2)이 이미 담당한다 — 같은 바인딩도
// 흐름마다 다른 종료 조건을 쓸 수 있어(예: "RUNNING까지" vs "STOPPED까지") FlowStep에 속하는 게 맞다.
public record ApiBinding(
        String id,
        // 어떤 Capability를 호출하는지(Capability.id() 참조).
        String capabilityId,
        List<InputMapping> inputMappings,
        List<OutputMapping> outputMappings,
        // "refresh targets" — 이 바인딩 호출이 성공한 뒤 새로고침해야 할 다른 ApiBinding id 목록.
        List<String> refreshBindingIds
) {
    public ApiBinding {
        inputMappings = immutableList(inputMappings);
        outputMappings = immutableList(outputMappings);
        refreshBindingIds = immutableList(refreshBindingIds);
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }

    // target은 targetKind(PATH/QUERY/BODY/HEADER)에 따라 실제 파라미터/필드 이름. from은 WP-2
    // FlowExpression과 동일한 문법("$form.name" 등) 또는 리터럴.
    public record InputMapping(String target, InputTarget targetKind, String from) {
        public enum InputTarget {
            PATH,
            QUERY,
            BODY,
            HEADER
        }
    }

    // from은 응답 바디 기준 점경로(예: "data.id" — "$" 접두어 없음, FlowExpression과 다른 좁은 문법).
    // to는 context key 이름만(예: "vmId" — §8 예시의 "context." 접두어는 중복 정보라 생략).
    public record OutputMapping(String from, String to) {
    }
}
