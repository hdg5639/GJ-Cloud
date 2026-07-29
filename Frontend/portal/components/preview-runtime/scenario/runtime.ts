import type {
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

export function extractStageOutputs(
  stage: PreviewCompiledScenarioStage,
  response: unknown,
  previousState: ScenarioState
): ScenarioState {
  const next = { ...previousState };
  for (const binding of stage.outputBindings) {
    let value = extractCandidate(response, binding.fromCandidates);
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
