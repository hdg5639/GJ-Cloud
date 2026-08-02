import type {
  PreviewCompiledScenario,
  PreviewCompiledScenarioStage,
  PreviewScenarioAssertionResult,
  PreviewScenarioStageExecution,
  PreviewVerificationContract,
} from "@/lib/types";
import type { ApiCallLogEntry, PreviewCapability, PreviewRuntimeConfig } from "../types";
import { callCapability, extractArray, rowId, unwrapEnvelope } from "../api";

export type ScenarioState = Record<string, unknown>;

export interface ScenarioRequest {
  path: Record<string, string>;
  query: Record<string, string>;
  headers: Record<string, string>;
  body: Record<string, unknown>;
}

export interface StageRunResult {
  execution: PreviewScenarioStageExecution;
  nextState: ScenarioState;
  log: ApiCallLogEntry | null;
}

export interface ScenarioExecutionPath {
  stages: PreviewCompiledScenarioStage[];
  error: string | null;
}

// Scenario는 배열 순서가 아니라 entryStageId -> nextStageIds 그래프가 정본이다. 분기 조건 모델이
// 아직 없으므로 둘 이상의 다음 단계는 임의로 하나를 고르지 않고 실행 전에 중단한다.
export function buildScenarioExecutionPath(
  scenario: PreviewCompiledScenario,
  startStageId?: string
): ScenarioExecutionPath {
  if (!scenario.entryStageId) {
    return { stages: [], error: "시나리오 시작 단계가 없습니다." };
  }
  const byId = new Map(scenario.stages.map((stage) => [stage.id, stage]));
  const ordered: PreviewCompiledScenarioStage[] = [];
  const visited = new Set<string>();
  let currentId: string | undefined = scenario.entryStageId;

  while (currentId) {
    if (visited.has(currentId)) {
      return { stages: [], error: `시나리오 순환 연결을 감지했습니다: ${currentId}` };
    }
    const stage = byId.get(currentId);
    if (!stage) {
      return { stages: [], error: `연결된 시나리오 단계를 찾지 못했습니다: ${currentId}` };
    }
    visited.add(currentId);
    ordered.push(stage);
    if (stage.nextStageIds.length > 1) {
      return {
        stages: [],
        error: `${stage.intent} 단계에 조건 없는 분기가 ${stage.nextStageIds.length}개 있습니다. 실행 경로를 하나로 확정해야 합니다.`,
      };
    }
    currentId = stage.nextStageIds[0];
  }

  if (visited.size !== scenario.stages.length) {
    const unreachable = scenario.stages.filter((stage) => !visited.has(stage.id)).map((stage) => stage.id);
    return { stages: [], error: `시작 단계에서 도달할 수 없는 단계가 있습니다: ${unreachable.join(", ")}` };
  }
  if (!startStageId) return { stages: ordered, error: null };
  const startIndex = ordered.findIndex((stage) => stage.id === startStageId);
  if (startIndex < 0) {
    return { stages: [], error: `재실행할 단계를 현재 실행 경로에서 찾지 못했습니다: ${startStageId}` };
  }
  return { stages: ordered.slice(startIndex), error: null };
}

export function readPath(value: unknown, path: string | null | undefined): unknown {
  if (!path || path === "$") return value;
  const normalized = path.replace(/^\$\.?/, "");
  if (!normalized) return value;
  let current = value;
  for (const part of normalized.split(".")) {
    if (current === null || current === undefined) return undefined;
    if (Array.isArray(current) && /^\d+$/.test(part)) {
      current = current[Number(part)];
      continue;
    }
    if (typeof current !== "object") return undefined;
    current = (current as Record<string, unknown>)[part];
  }
  return current;
}

export function resolveSource(source: string, state: ScenarioState, inputs: ScenarioState = {}): unknown {
  if (source.startsWith("$scenario.")) return readPath(state, source.slice("$scenario.".length));
  if (source.startsWith("$input.")) return readPath(inputs, source.slice("$input.".length));
  if (source === "$auth.token") return state.authToken;
  return undefined;
}

function present(value: unknown): boolean {
  return value !== undefined && value !== null && value !== "";
}

export function missingRequiredStageInputs(
  stage: PreviewCompiledScenarioStage,
  state: ScenarioState
): string[] {
  return stage.inputBindings
    .filter((binding) => binding.required && !present(resolveSource(binding.source, state)))
    .map((binding) => `${binding.target} ← ${binding.source}`);
}

function scenarioStateKey(source: string): string | null {
  if (!source.startsWith("$scenario.")) return null;
  return source.slice("$scenario.".length).split(".")[0] || null;
}

// 상태 변경 API를 호출하기 전에 전체 실행 경로의 데이터 계보를 검사한다. API 응답으로 생길 값은
// anticipated로 추적하되, 사용자 입력을 생산하는 로컬 stage의 값은 현재 state에 실제로 있어야 한다.
export function preflightScenarioExecution(
  stages: PreviewCompiledScenarioStage[],
  initialState: ScenarioState
): string[] {
  const errors: string[] = [];
  const available = new Set(
    Object.entries(initialState).filter(([, value]) => present(value)).map(([key]) => key)
  );

  for (const stage of stages) {
    const localInputStage = stage.role === "ENTRY"
      || stage.role === "PREPARE"
      || stage.role === "CONFIGURE"
      || stage.role === "SELECT_CONTEXT";
    const supportsLocalOutput = localInputStage || stage.role === "SELECT";
    if (!stage.capabilityId && stage.outputs.length > 0 && !supportsLocalOutput) {
      errors.push(`${stage.intent}: ${stage.role} 로컬 단계의 출력 생성 규칙을 현재 런타임이 지원하지 않습니다.`);
    }
    if (localInputStage) {
      for (const output of stage.outputs) {
        if (!available.has(output)) {
          errors.push(`${stage.intent}: 사용자 입력 ${output} 값이 비어 있습니다.`);
        }
      }
    }
    if (stage.role === "SELECT") {
      if (!available.has("selectedId")
        && !available.has("collection")
        && !available.has("authenticatedCollection")) {
        errors.push(`${stage.intent}: 실제 목록 또는 선택된 리소스 ID가 없습니다.`);
      }
      available.add("selectedId");
    }
    for (const binding of stage.inputBindings) {
      if (!binding.required) continue;
      const key = scenarioStateKey(binding.source);
      if (key && !available.has(key)) {
        errors.push(`${stage.intent}: ${binding.target}에 연결할 ${key} 값의 선행 생산자가 없습니다.`);
      }
    }
    for (const output of stage.outputs) available.add(output);
    for (const binding of stage.outputBindings) available.add(binding.to);
  }
  return Array.from(new Set(errors));
}

function stringify(value: unknown): string {
  if (value === null || value === undefined) return "";
  return typeof value === "string" ? value : String(value);
}

export function buildScenarioRequest(
  stage: PreviewCompiledScenarioStage,
  state: ScenarioState,
  inputs: ScenarioState = {}
): ScenarioRequest {
  const request: ScenarioRequest = { path: {}, query: {}, headers: {}, body: {} };
  for (const binding of stage.inputBindings) {
    const value = resolveSource(binding.source, state, inputs);
    if ((value === undefined || value === null || value === "") && !binding.required) continue;
    switch (binding.targetKind) {
      case "PATH":
        request.path[binding.target] = stringify(value);
        break;
      case "QUERY":
        request.query[binding.target] = stringify(value);
        break;
      case "HEADER":
        request.headers[binding.target] = stringify(value);
        break;
      case "BODY":
        request.body[binding.target] = value;
        break;
    }
  }
  return request;
}

function extractCandidate(response: unknown, candidates: string[]): unknown {
  for (const candidate of candidates) {
    const value = readPath(response, candidate);
    if (value !== undefined && value !== null) return value;
  }
  return undefined;
}

function collectIdentifierCandidates(value: unknown, depth = 0): unknown[] {
  if (depth > 4 || !value || typeof value !== "object" || Array.isArray(value)) return [];
  const found: unknown[] = [];
  for (const [key, item] of Object.entries(value as Record<string, unknown>)) {
    if ((key.toLowerCase() === "id" || key.toLowerCase().endsWith("id"))
      && (typeof item === "string" || typeof item === "number") && String(item).length > 0) {
      found.push(item);
    }
    if (item && typeof item === "object" && !Array.isArray(item)) {
      found.push(...collectIdentifierCandidates(item, depth + 1));
    }
  }
  return found;
}

function inferUnambiguousIdentifier(response: unknown): unknown {
  const candidates = collectIdentifierCandidates(response);
  const unique = Array.from(new Map(candidates.map((value) => [String(value), value])).values());
  return unique.length === 1 ? unique[0] : undefined;
}

export function extractStageOutputs(
  stage: PreviewCompiledScenarioStage,
  response: unknown,
  previousState: ScenarioState
): ScenarioState {
  const next = { ...previousState };
  for (const binding of stage.outputBindings) {
    let value = extractCandidate(response, binding.fromCandidates);
    if (value === undefined && binding.to === "createdId") {
      value = inferUnambiguousIdentifier(response);
    }
    if (value === undefined && (binding.to === "collection" || binding.to === "authenticatedCollection")) {
      const collection = extractArray(response);
      if (collection.length > 0) value = collection;
    }
    if (value === undefined && (binding.to === "selectedResource" || binding.to === "verifiedResource")) {
      value = unwrapEnvelope(response);
    }
    if (value !== undefined) next[binding.to] = value;
  }
  return next;
}

function valuesEqual(left: unknown, right: unknown): boolean {
  if (left === right) return true;
  if (left === null || left === undefined || right === null || right === undefined) return false;
  return String(left) === String(right);
}

function collectionContains(response: unknown, expected: unknown): boolean {
  if (expected === undefined || expected === null) return false;
  return extractArray(response).some((item) => valuesEqual(rowId(item), expected));
}

export function evaluateVerification(
  contract: PreviewVerificationContract | null,
  response: unknown,
  state: ScenarioState,
  log: ApiCallLogEntry | null
): PreviewScenarioAssertionResult[] {
  if (!contract) return [];
  const expected = contract.expectedSource ? resolveSource(contract.expectedSource, state) : null;
  const directActual = contract.responsePath ? readPath(response, contract.responsePath) : response;
  const actual = directActual === undefined && contract.responsePath
    ? readPath(unwrapEnvelope(response), contract.responsePath)
    : directActual;
  let passed = false;
  let message = "";

  switch (contract.type) {
    case "HTTP_STATUS_MATCH":
      passed = log?.status !== null && log?.status !== undefined && log.status >= 200 && log.status < 300;
      message = passed ? `HTTP ${log?.status} 응답 확인` : `성공 HTTP 상태를 받지 못했습니다`;
      break;
    case "RESPONSE_SCHEMA_VALID":
      passed = response !== undefined && response !== null;
      message = passed ? "응답 본문을 정상적으로 해석했습니다" : "검증할 응답 본문이 없습니다";
      break;
    case "RESOURCE_EXISTS":
      passed = response !== undefined && response !== null;
      message = passed ? "후속 조회에서 리소스를 확인했습니다" : "리소스를 찾지 못했습니다";
      break;
    case "RESOURCE_NOT_EXISTS":
      passed = response === undefined || response === null || log?.status === 404;
      message = passed ? "리소스가 더 이상 존재하지 않습니다" : "리소스가 여전히 조회됩니다";
      break;
    case "FIELD_EQUALS":
      passed = valuesEqual(actual, expected);
      message = passed ? "응답 필드가 시나리오 상태와 일치합니다" : "응답 필드가 예상값과 다릅니다";
      break;
    case "STATE_EQUALS":
      passed = contract.acceptedValues.length > 0
        ? contract.acceptedValues.some((value) => valuesEqual(actual, value))
        : expected !== null
          ? valuesEqual(actual, expected)
          : actual !== undefined && actual !== null;
      message = passed ? "백엔드 상태가 기대 상태에 도달했습니다" : "백엔드 상태가 아직 기대값과 다릅니다";
      break;
    case "COLLECTION_CONTAINS":
      passed = collectionContains(response, expected);
      message = passed ? "목록에서 대상 리소스를 확인했습니다" : "목록에서 대상 리소스를 찾지 못했습니다";
      break;
    case "COLLECTION_EXCLUDES":
      passed = !collectionContains(response, expected);
      message = passed ? "목록에서 대상 리소스가 제거되었습니다" : "대상 리소스가 목록에 남아 있습니다";
      break;
    case "OUTPUT_EXTRACTABLE":
      passed = expected !== undefined && expected !== null && expected !== "";
      message = passed ? "다음 단계에 전달할 출력값을 추출했습니다" : "필수 출력값을 추출하지 못했습니다";
      break;
  }
  return [{ type: contract.type, passed, message, actual, expected }];
}

export async function runApiStage({
  stage,
  capability,
  state,
  config,
  signal,
  requestOverride,
}: {
  stage: PreviewCompiledScenarioStage;
  capability: PreviewCapability;
  state: ScenarioState;
  config: PreviewRuntimeConfig;
  signal: AbortSignal;
  requestOverride?: Partial<ScenarioRequest>;
}): Promise<StageRunResult> {
  const startedAt = Date.now();
  const generatedRequest = buildScenarioRequest(stage, state);
  const request: ScenarioRequest = {
    path: { ...generatedRequest.path, ...requestOverride?.path },
    query: { ...generatedRequest.query, ...requestOverride?.query },
    headers: { ...generatedRequest.headers, ...requestOverride?.headers },
    body: requestOverride?.body ?? generatedRequest.body,
  };
  const capturedLog: { current: ApiCallLogEntry | null } = { current: null };
  const runtimeConfig: PreviewRuntimeConfig = {
    ...config,
    onApiCall: (entry) => {
      capturedLog.current = entry;
      config.onApiCall?.(entry);
    },
  };

  try {
    const response = await callCapability(runtimeConfig, capability, {
      pathParams: request.path,
      query: request.query,
      headers: request.headers,
      body: Object.keys(request.body).length > 0 ? request.body : undefined,
      signal,
    });
    const nextState = extractStageOutputs(stage, response, state);
    const assertions = evaluateVerification(stage.verification, response, nextState, capturedLog.current);
    const requiredAssertionFailed = stage.verification?.required && assertions.some((assertion) => !assertion.passed);
    const completedAt = Date.now();
    return {
      nextState,
      log: capturedLog.current,
      execution: {
        stageId: stage.id,
        status: requiredAssertionFailed ? "FAILED" : "SUCCESS",
        operationId: stage.operationId,
        method: capability.method,
        url: capturedLog.current?.url ?? null,
        request,
        response,
        responseHeaders: capturedLog.current?.responseHeaders ?? {},
        extractedOutputs: Object.fromEntries(
          stage.outputBindings
            .filter((binding) => nextState[binding.to] !== undefined)
            .map((binding) => [binding.to, binding.sensitive ? "••••••" : nextState[binding.to]])
        ),
        assertions,
        durationMs: completedAt - startedAt,
        error: requiredAssertionFailed ? assertions.find((assertion) => !assertion.passed)?.message ?? "검증 실패" : null,
        startedAt,
        completedAt,
      },
    };
  } catch (error) {
    const completedAt = Date.now();
    const aborted = signal.aborted;
    return {
      nextState: state,
      log: capturedLog.current,
      execution: {
        stageId: stage.id,
        status: aborted ? "CANCELLED" : "FAILED",
        operationId: stage.operationId,
        method: capability.method,
        url: capturedLog.current?.url ?? null,
        request,
        response: capturedLog.current?.responseBody ?? null,
        responseHeaders: capturedLog.current?.responseHeaders ?? {},
        extractedOutputs: {},
        assertions: [],
        durationMs: completedAt - startedAt,
        error: aborted ? "사용자가 실행을 취소했습니다" : error instanceof Error ? error.message : "요청 실패",
        startedAt,
        completedAt,
      },
    };
  }
}

export function emptyExecution(stage: PreviewCompiledScenarioStage): PreviewScenarioStageExecution {
  return {
    stageId: stage.id,
    status: "IDLE",
    operationId: stage.operationId,
    method: null,
    url: null,
    request: null,
    response: null,
    responseHeaders: {},
    extractedOutputs: {},
    assertions: [],
    durationMs: null,
    error: null,
    startedAt: null,
    completedAt: null,
  };
}
