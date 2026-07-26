// Backend/Ops의 gj.cloud.ops.application.preview.flow.*(WP-2, FlowBlueprint)와
// gj.cloud.ops.application.preview.binding.ApiBinding(WP-3)의 TS 미러. 지금까지는 이 모델들을
// 만들어내는 생성기가 없어(AI Planner의 ADD_FLOW/ASSIGN_FLOW는 WP-4, 아직 없음) 네트워크로 전송되는
// 형태 자체가 없었다 — flowExecutor.ts(§14, WP-8 Blueprint runtime의 첫 조각)가 이 모델들을 처음
// 소비하는 지점이라 여기서 처음 미러링한다.

export type FlowStepType =
  | "API_CALL"
  | "SET_CONTEXT"
  | "NAVIGATE"
  | "POLL"
  | "WAIT"
  | "CONDITION"
  | "SHOW_SUCCESS"
  | "SHOW_ERROR"
  | "REFRESH_BINDING"
  // Deferred but reserved(§6) — Java FlowBlueprintValidator와 동일하게 이 실행기도 명시 거부한다.
  | "EVENT_STREAM"
  | "UPLOAD"
  | "DOWNLOAD"
  | "PARALLEL";

// Java "equalsValue"(equals()와의 accessor 충돌 회피 이유는 FlowStep.java 참고)를 그대로 미러링.
export interface PollCondition {
  path: string;
  equalsValue: string | null;
  in: string[] | null;
}

// FlowStep.java의 "타입마다 실제로 쓰는 필드가 다르고 그 외는 항상 null" 관례를 그대로 미러링.
export interface FlowStep {
  id: string;
  type: FlowStepType;
  bindingRef: string | null;
  input: Record<string, string> | null;
  values: Record<string, string> | null;
  pageId: string | null;
  parameters: Record<string, string> | null;
  until: PollCondition[] | null;
  intervalMs: number | null;
  timeoutSeconds: number | null;
  condition: string | null;
  message: string | null;
}

export interface FlowTrigger {
  pageId: string | null;
  actionId: string | null;
}

export interface FlowBlueprint {
  id: string;
  trigger: FlowTrigger | null;
  steps: FlowStep[];
}

export type InputTarget = "PATH" | "QUERY" | "BODY" | "HEADER";

export interface InputMapping {
  target: string;
  targetKind: InputTarget;
  // FlowExpression 문법("$form.name" 등) 또는 리터럴.
  from: string;
}

export interface OutputMapping {
  // 응답 바디 기준 점경로(예: "data.id") — "$" 접두어 없는 좁은 문법(FlowExpression과 다름).
  from: string;
  // context key 이름만("context." 접두어 없음).
  to: string;
}

export interface ApiBinding {
  id: string;
  capabilityId: string;
  inputMappings: InputMapping[];
  outputMappings: OutputMapping[];
  refreshBindingIds: string[];
}

// §9 "Runtime behavior"가 명시한 상태 집합. Java PollStatus(Polling 증분, 실행기 없어 미사용이던
// enum)를 flowExecutor.ts가 처음 실제로 만들어낸다.
export type PollStatus = "PENDING" | "RUNNING" | "SUCCESS" | "FAILURE" | "TIMEOUT" | "CANCELLED";
