// Workflow Composition Phase 2 §14의 Blueprint Flow Runtime. Portal 미리보기와 배포 정적
// 아티팩트가 동일한 실행 계약을 사용하며, 실제 HTTP·Navigation·타이머는 deps로 주입한다.
// Backend에서 검증된 제한 표현식과 bounded polling만 실행하고, AbortSignal로 페이지 이탈/사용자
// 취소를 정상 상태(CANCELLED)로 구분한다.
import { isExpressionLike, parseFlowExpression, type FlowExpression } from "./expression";
import type {
  ApiBinding,
  FlowBlueprint,
  FlowStep,
  PollCondition,
  PollStatus,
} from "./types";

// 각 API_CALL/POLL/REFRESH_BINDING 호출의 원본 응답을 stepId로 저장해 "$steps.<id>.response...."
// 표현식이 참조할 수 있게 한다.
export interface FlowContext {
  form: Record<string, unknown>;
  route: Record<string, unknown>;
  context: Record<string, unknown>;
  steps: Record<string, { response: unknown }>;
  currentUser: Record<string, unknown> | null;
  row: Record<string, unknown> | null;
}

export type FlowExecutionStatus = "SUCCESS" | "CANCELLED" | "TIMEOUT" | "STOPPED";

export interface FlowExecutionResult {
  status: FlowExecutionStatus;
  context: FlowContext;
}

type StepOutcome = "continue" | "stop" | "cancelled" | "timeout";

export function createFlowContext(seed: Partial<FlowContext> = {}): FlowContext {
  return {
    form: seed.form ?? {},
    route: seed.route ?? {},
    context: seed.context ?? {},
    steps: seed.steps ?? {},
    currentUser: seed.currentUser ?? null,
    row: seed.row ?? null,
  };
}

export interface BindingRequest {
  path: Record<string, string>;
  query: Record<string, string>;
  body: Record<string, unknown>;
  headers: Record<string, string>;
}

export interface FlowExecutorDeps {
  // 실제 HTTP 호출은 호출 측(향후 BindingRuntime)이 담당한다 — FlowExecutor는 "무엇을, 어떤 요청으로"
  // 호출할지만 ApiBinding.inputMappings로부터 결정한다.
  callBinding: (binding: ApiBinding, request: BindingRequest) => Promise<unknown>;
  navigate?: (pageId: string, parameters: Record<string, unknown>) => void;
  onMessage?: (kind: "SUCCESS" | "ERROR", message: string) => void;
  onPollStatusChange?: (stepId: string, status: PollStatus) => void;
  // §9 "refresh related list or detail bindings after success"는 부가 효과라 실패해도 이미 끝난
  // 주 흐름(POLL 성공 등)을 되돌리지 않는다 — 대신 침묵하지 않고 이 콜백으로 알린다.
  onRefreshBindingError?: (bindingId: string, error: unknown) => void;
  // §9 "stop on page disposal" — 페이지 이탈 시 호출 측이 abort()하면 다음 체크 지점에서 멈춘다.
  signal?: AbortSignal;
  // 테스트에서 실제 타이머 없이 결정론적으로 검증하기 위한 주입 지점(기본은 실제 시간/setTimeout).
  now?: () => number;
  sleep?: (ms: number) => Promise<void>;
}

const defaultSleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

export class FlowExecutionError extends Error {
  readonly stepId: string;

  constructor(stepId: string, cause: unknown) {
    super(`step "${stepId}" 실행 실패: ${cause instanceof Error ? cause.message : String(cause)}`, { cause });
    this.stepId = stepId;
  }
}

function findBinding(bindings: ApiBinding[], id: string): ApiBinding {
  const binding = bindings.find((b) => b.id === id);
  if (!binding) {
    throw new Error(`알 수 없는 bindingRef: ${id}`);
  }
  return binding;
}

function readByPath(root: unknown, path: string[]): unknown {
  let current = root;
  for (const segment of path) {
    if (current === null || typeof current !== "object") {
      return undefined;
    }
    current = (current as Record<string, unknown>)[segment];
  }
  return current;
}

function resolveScopeRoot(ctx: FlowContext, expr: FlowExpression): unknown {
  switch (expr.scope) {
    case "FORM":
      return ctx.form;
    case "ROUTE":
      return ctx.route;
    case "CONTEXT":
      return ctx.context;
    case "STEPS":
      return ctx.steps;
    case "CURRENT_USER":
      return ctx.currentUser;
    case "ROW":
      return ctx.row;
  }
}

// FlowStep/InputMapping의 "$"로 시작하는 값을 평가한다. "$"로 시작하지 않으면 리터럴로 그대로
// 돌려준다(Backend FlowExpression.isExpressionLike와 동일 규칙).
export function resolveExpression(raw: string | null | undefined, ctx: FlowContext): unknown {
  if (typeof raw !== "string") {
    throw new Error("Flow 표현식 또는 리터럴 값이 비어있습니다.");
  }
  if (!isExpressionLike(raw)) {
    return raw;
  }
  const expr = parseFlowExpression(raw);
  if (!expr) {
    throw new Error(`허용되지 않는 표현식: ${raw}`);
  }
  return readByPath(resolveScopeRoot(ctx, expr), expr.path);
}

function resolveMap(map: Record<string, string> | null, ctx: FlowContext): Record<string, unknown> {
  if (!map) {
    return {};
  }
  const result: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(map)) {
    result[key] = resolveExpression(value, ctx);
  }
  return result;
}

// ApiBinding.inputMappings(WP-3)가 요청 조립의 정본이다. FlowStep.input(API_CALL)/parameters(POLL)는
// ApiBinding이 생기기 전(WP-2 시점)의 임시 자리였던 것으로 판단해 이 실행기는 두 필드를 쓰지 않는다
// (알려진 단순화 — 실제로 이 둘을 함께 써야 하는 사례가 나오면 WP-4/§8 소비 지점에서 재조정).
// NAVIGATE의 parameters는 대상이 ApiBinding이 아니라 페이지 라우트라 이 단순화와 무관하게 계속 쓴다.
function buildBindingRequest(binding: ApiBinding, ctx: FlowContext): BindingRequest {
  const request: BindingRequest = { path: {}, query: {}, body: {}, headers: {} };
  for (const mapping of binding.inputMappings) {
    const value = resolveExpression(mapping.from, ctx);
    if (value === undefined || value === null) {
      throw new Error(`${mapping.target} 입력값을 해석하지 못했습니다.`);
    }
    switch (mapping.targetKind) {
      case "PATH":
        request.path[mapping.target] = String(value);
        break;
      case "QUERY":
        request.query[mapping.target] = String(value);
        break;
      case "HEADER":
        request.headers[mapping.target] = String(value);
        break;
      case "BODY":
        request.body[mapping.target] = value;
        break;
    }
  }
  return request;
}

function applyOutputMappings(binding: ApiBinding, response: unknown, ctx: FlowContext): void {
  for (const mapping of binding.outputMappings) {
    const value = readByPath(response, mapping.from.split("."));
    // 같은 context key에 data.id/result.id/payload.id/id 후보를 순서대로 매핑할 수 있다.
    // 존재하지 않는 후보(undefined)는 앞에서 성공적으로 찾은 값을 덮어쓰면 안 된다.
    if (value !== undefined) {
      ctx.context[mapping.to] = value;
    }
  }
}

async function callAndApply(
  binding: ApiBinding,
  ctx: FlowContext,
  deps: FlowExecutorDeps,
  recordStepId?: string
): Promise<unknown> {
  const request = buildBindingRequest(binding, ctx);
  const response = await deps.callBinding(binding, request);
  applyOutputMappings(binding, response, ctx);
  if (recordStepId) {
    ctx.steps[recordStepId] = { response };
  }
  return response;
}

async function refreshRelatedBindings(
  binding: ApiBinding,
  bindings: ApiBinding[],
  ctx: FlowContext,
  deps: FlowExecutorDeps
): Promise<void> {
  for (const refreshId of binding.refreshBindingIds) {
    try {
      const target = findBinding(bindings, refreshId);
      await callAndApply(target, ctx, deps);
    } catch (error) {
      deps.onRefreshBindingError?.(refreshId, error);
    }
  }
}

// PollCondition은 하나라도 만족하면 종료(OR 의미, Backend FlowStep.until 주석과 동일). 모델에
// 종료 조건이 "성공값"과 "실패값"으로 나뉘어 있지 않아(§9 예시조차 in:["SUCCEEDED","FAILED"]를
// 하나의 종료 조건으로만 씀), 이 실행기는 어떤 until 조건이든 매칭되면 폴링 메커니즘 자체는 SUCCESS로
// 본다 — 응답이 담고 있는 값의 업무적 의미(성공/실패)까지는 이 모델이 표현하지 못한다(알려진 단순화).
// PollStatus.FAILURE는 바인딩 호출 자체가 실패(네트워크/HTTP 오류)했을 때만 쓴다.
function matchesCondition(condition: PollCondition, response: unknown): boolean {
  const value = readByPath(response, condition.path.split("."));
  if (condition.equalsValue !== null) {
    return String(value) === condition.equalsValue;
  }
  if (condition.in) {
    return condition.in.includes(String(value));
  }
  return false;
}

function matchesUntil(until: PollCondition[], response: unknown): boolean {
  return until.some((condition) => matchesCondition(condition, response));
}

async function executePoll(
  step: FlowStep,
  bindings: ApiBinding[],
  ctx: FlowContext,
  deps: FlowExecutorDeps
): Promise<StepOutcome> {
  const binding = findBinding(bindings, step.bindingRef!);
  const sleep = deps.sleep ?? defaultSleep;
  const now = deps.now ?? Date.now;
  const deadline = now() + step.timeoutSeconds! * 1000;

  deps.onPollStatusChange?.(step.id, "PENDING");
  deps.onPollStatusChange?.(step.id, "RUNNING");

  for (;;) {
    if (deps.signal?.aborted) {
      deps.onPollStatusChange?.(step.id, "CANCELLED");
      return "cancelled";
    }

    let response: unknown;
    try {
      response = await deps.callBinding(binding, buildBindingRequest(binding, ctx));
    } catch (error) {
      if (deps.signal?.aborted || (error instanceof DOMException && error.name === "AbortError")) {
        deps.onPollStatusChange?.(step.id, "CANCELLED");
        return "cancelled";
      }
      deps.onPollStatusChange?.(step.id, "FAILURE");
      throw new FlowExecutionError(step.id, error);
    }

    if (matchesUntil(step.until!, response)) {
      applyOutputMappings(binding, response, ctx);
      ctx.steps[step.id] = { response };
      deps.onPollStatusChange?.(step.id, "SUCCESS");
      await refreshRelatedBindings(binding, bindings, ctx, deps);
      return "continue";
    }

    if (now() >= deadline) {
      deps.onPollStatusChange?.(step.id, "TIMEOUT");
      return "timeout";
    }

    await sleep(step.intervalMs!);
    if (deps.signal?.aborted) {
      deps.onPollStatusChange?.(step.id, "CANCELLED");
      return "cancelled";
    }
  }
}

async function executeStep(
  step: FlowStep,
  bindings: ApiBinding[],
  ctx: FlowContext,
  deps: FlowExecutorDeps
): Promise<StepOutcome> {
  switch (step.type) {
    case "API_CALL":
    case "REFRESH_BINDING": {
      const binding = findBinding(bindings, step.bindingRef!);
      try {
        await callAndApply(binding, ctx, deps, step.id);
      } catch (error) {
        if (deps.signal?.aborted) return "cancelled";
        throw new FlowExecutionError(step.id, error);
      }
      return "continue";
    }
    case "SET_CONTEXT":
      Object.assign(ctx.context, resolveMap(step.values, ctx));
      return "continue";
    case "NAVIGATE":
      deps.navigate?.(step.pageId!, resolveMap(step.parameters, ctx));
      return "continue";
    case "POLL":
      return executePoll(step, bindings, ctx, deps);
    case "WAIT":
      await (deps.sleep ?? defaultSleep)(step.timeoutSeconds! * 1000);
      return deps.signal?.aborted ? "cancelled" : "continue";
    case "CONDITION":
      // 모델에 step 간 분기 링크가 없어(Backend FlowBlueprintValidator 주석과 동일 전제) false면
      // 나머지 step을 건너뛰는 "가드"로만 쓴다.
      return resolveExpression(step.condition!, ctx) ? "continue" : "stop";
    case "SHOW_SUCCESS":
      deps.onMessage?.("SUCCESS", step.message!);
      return "continue";
    case "SHOW_ERROR":
      deps.onMessage?.("ERROR", step.message!);
      return "continue";
    case "EVENT_STREAM":
    case "UPLOAD":
    case "DOWNLOAD":
    case "PARALLEL":
      throw new FlowExecutionError(step.id, new Error(`아직 지원하지 않는 step 타입: ${step.type}`));
  }
}

// FlowBlueprint를 순서대로 실행한다. Backend FlowBlueprintValidator가 이미 구조를 검증했다는 전제라
// 여기서는 필드 존재 여부를 다시 확인하지 않는다(검증을 통과한 Blueprint만 들어온다고 가정).
export async function executeFlow(
  flow: FlowBlueprint,
  bindings: ApiBinding[],
  ctx: FlowContext,
  deps: FlowExecutorDeps
): Promise<FlowExecutionResult> {
  for (const step of flow.steps) {
    if (deps.signal?.aborted) {
      return { status: "CANCELLED", context: ctx };
    }
    const outcome = await executeStep(step, bindings, ctx, deps);
    if (outcome === "cancelled") return { status: "CANCELLED", context: ctx };
    if (outcome === "timeout") return { status: "TIMEOUT", context: ctx };
    if (outcome === "stop") return { status: "STOPPED", context: ctx };
  }
  return { status: "SUCCESS", context: ctx };
}
