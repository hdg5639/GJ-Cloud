// FlowBlueprint/ApiBinding(WP-2/WP-3)은 이제 analyze/plan-apply 응답으로 실제 전송된다
// (RuleBasedFlowGenerator, Workflow Composition Phase 2 §22 7번으로 가는 조각) — 정본은
// lib/types.ts(마법사 API 응답과 같은 타입을 공유해야 하므로)이고, 여기서는 flowExecutor.ts가 쓰는
// 이름으로 재수출만 한다(다른 Preview* 타입과 같은 관례, components/preview-runtime/types.ts 참고).
export type {
  PreviewFlowStepType as FlowStepType,
  PreviewPollCondition as PollCondition,
  PreviewFlowStep as FlowStep,
  PreviewFlowTrigger as FlowTrigger,
  PreviewFlowBlueprint as FlowBlueprint,
  PreviewInputTarget as InputTarget,
  PreviewInputMapping as InputMapping,
  PreviewOutputMapping as OutputMapping,
  PreviewApiBinding as ApiBinding,
} from "@/lib/types";

// §9 "Runtime behavior"가 명시한 상태 집합. 서버가 만들어내는 값이 아니라(응답 DTO가 아님)
// flowExecutor.ts가 실행 중 스스로 만들어내는 클라이언트 전용 런타임 상태라 lib/types.ts가 아니라
// 여기서 직접 정의한다.
export type PollStatus = "PENDING" | "RUNNING" | "SUCCESS" | "FAILURE" | "TIMEOUT" | "CANCELLED";
