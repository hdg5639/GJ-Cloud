package gj.cloud.ops.application.preview.flow;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §6 "Minimum flow step types".
// EVENT_STREAM/UPLOAD/DOWNLOAD/PARALLEL은 문서가 명시한 "Deferred but reserved" — 값만 존재하고
// FlowBlueprintValidator가 지금은 명시적으로 거부한다(폴링이 안정화된 뒤 §9 순서대로 추가).
public enum FlowStepType {
    API_CALL,
    SET_CONTEXT,
    NAVIGATE,
    POLL,
    WAIT,
    CONDITION,
    SHOW_SUCCESS,
    SHOW_ERROR,
    REFRESH_BINDING,
    EVENT_STREAM,
    UPLOAD,
    DOWNLOAD,
    PARALLEL
}
