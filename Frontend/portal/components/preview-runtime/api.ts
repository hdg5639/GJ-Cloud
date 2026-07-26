import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

// 주의(알려진 제약): 이 렌더러는 브라우저에서 대상 API로 직접 fetch를 보낸다. 대상 API가 이 Preview
// origin을 CORS로 허용하지 않으면 요청이 브라우저에서 막힌다 — Phase C 범위에서는 해결하지 않고,
// 실제 배포(Phase D 이후)에서 프록시가 필요한지 별도로 판단한다.

// DetailPanel/CreateEditModal/DeleteConfirmModal은 실제 OpenAPI 경로 파라미터 이름을 모른 채 항상
// { id: ... } 하나만 넘긴다. 예전엔 문자열 그대로 "{id}"를 찾아 치환해서, capability.path가
// "/vms/{vmId}"처럼 다른 이름을 쓰면 치환에 실패해 URL에 "{vmId}"가 그대로 남아 요청이 깨졌다.
// DETAIL/UPDATE/DELETE는 항상 경로의 마지막 세그먼트만 파라미터이므로(CapabilityExtractor의
// lastSegmentIsParam 규칙), 이름과 무관하게 "경로의 마지막 {...}"를 대상 리소스 ID로 취급해 치환한다.
// 중첩 리소스(경로 중간에 다른 {orgId} 같은 파라미터가 더 있는 경우)는 아직 지원하지 않는다.
function replaceLastPathPlaceholder(path: string, value: string): string {
  const lastOpen = path.lastIndexOf("{");
  const lastClose = path.lastIndexOf("}");
  if (lastOpen === -1 || lastClose === -1 || lastClose < lastOpen) {
    return path;
  }
  return path.slice(0, lastOpen) + encodeURIComponent(value) + path.slice(lastClose + 1);
}

function buildUrl(
  config: PreviewRuntimeConfig,
  capability: PreviewCapability,
  pathParams: Record<string, string> = {},
  query: Record<string, string> = {}
): string {
  let path = capability.path;
  for (const [name, value] of Object.entries(pathParams)) {
    if (name === "id") continue;
    path = path.replaceAll(`{${name}}`, encodeURIComponent(value));
  }
  if (pathParams.id !== undefined && path.includes("{")) {
    path = replaceLastPathPlaceholder(path, pathParams.id);
  }
  const base = config.apiBaseUrl.replace(/\/$/, "");
  const url = new URL(base + path);
  for (const [key, value] of Object.entries(query)) {
    if (value) url.searchParams.set(key, value);
  }
  if (config.authToken && config.authStrategy.type === "API_KEY_QUERY" && config.authStrategy.queryParamName) {
    url.searchParams.set(config.authStrategy.queryParamName, config.authToken);
  }
  return url.toString();
}

// 로그인으로 받은 토큰을 config.authStrategy가 정한 방식대로 헤더에 싣는다 — Bearer만 가정하면
// API Key 인증 API에서 요청이 항상 401/403이 난다.
function buildAuthHeaders(config: PreviewRuntimeConfig): Record<string, string> {
  const { authToken, authStrategy } = config;
  if (!authToken || authStrategy.type === "NONE" || authStrategy.type === "API_KEY_QUERY") {
    return {};
  }
  const headerName = authStrategy.headerName ?? "Authorization";
  const prefix = authStrategy.prefix ?? "";
  return { [headerName]: `${prefix}${authToken}` };
}

export async function callCapability(
  config: PreviewRuntimeConfig,
  capability: PreviewCapability,
  options: {
    pathParams?: Record<string, string>;
    query?: Record<string, string>;
    body?: Record<string, unknown>;
    headers?: Record<string, string>;
    signal?: AbortSignal;
  } = {}
): Promise<unknown> {
  const url = buildUrl(config, capability, options.pathParams, options.query);
  let status: number | null = null;
  let responseBody: unknown = null;
  let errorMessage: string | null = null;
  try {
    const res = await fetch(url, {
      method: capability.method,
      headers: {
        ...(options.body ? { "Content-Type": "application/json" } : {}),
        ...buildAuthHeaders(config),
        ...options.headers,
      },
      body: options.body ? JSON.stringify(options.body) : undefined,
      signal: options.signal,
    });
    status = res.status;
    if (!res.ok) {
      errorMessage = `${capability.method} ${url} 요청이 실패했습니다 (${res.status})`;
      throw new Error(errorMessage);
    }
    if (res.status === 204) {
      return null;
    }
    const text = await res.text();
    responseBody = text ? JSON.parse(text) : null;
    return responseBody;
  } catch (err) {
    errorMessage = errorMessage ?? (err instanceof Error ? err.message : "요청 실패");
    throw err;
  } finally {
    config.onApiCall?.({
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      method: capability.method,
      url,
      status,
      requestBody: options.body ?? null,
      responseBody,
      error: errorMessage,
      timestamp: Date.now(),
    });
  }
}

// Backend/Ops CapabilityExtractor의 PASSWORD_FIELD_HINTS와 동일한 휴리스틱 — 필드명만 보고
// <input type="password">로 그릴지 판단한다(실제 스키마의 format 정보까지는 Phase A가 옮기지 않음).
export function isPasswordLikeField(fieldName: string): boolean {
  const lower = fieldName.toLowerCase();
  return lower.includes("password") || lower === "pw" || lower === "pwd";
}

const ARRAY_ENVELOPE_KEYS = [
  "content", "items", "data", "list", "results", "records", "rows", "elements", "result", "payload",
];
const MAX_ENVELOPE_UNWRAP_DEPTH = 4;

function extractArrayHeuristic(result: unknown, depth = 0): Record<string, unknown>[] {
  if (Array.isArray(result)) {
    return result as Record<string, unknown>[];
  }
  if (depth >= MAX_ENVELOPE_UNWRAP_DEPTH || !result || typeof result !== "object") {
    return [];
  }
  const obj = result as Record<string, unknown>;
  const orderedKeys = [
    ...ARRAY_ENVELOPE_KEYS.filter((key) => key in obj),
    ...Object.keys(obj).filter((key) => !ARRAY_ENVELOPE_KEYS.includes(key)),
  ];
  for (const key of orderedKeys) {
    const nested = extractArrayHeuristic(obj[key], depth + 1);
    if (nested.length > 0) {
      return nested;
    }
  }
  return [];
}

// 분석 단계(또는 사용자가 직접 지정)에서 확인된 dot-path를 우선 신뢰한다 — 없거나 그 위치에 값이
// 없을 때만 기존 방식(이름 추측 / 재귀 탐색)으로 대체한다.
function readValueAtPath(result: unknown, dotPath: string): unknown {
  let current: unknown = result;
  for (const key of dotPath.split(".")) {
    if (!current || typeof current !== "object") {
      return undefined;
    }
    current = (current as Record<string, unknown>)[key];
  }
  return current;
}

function readDotPath(result: unknown, dotPath: string): string | null {
  const value = readValueAtPath(result, dotPath);
  return typeof value === "string" && value.length > 0 ? value : null;
}

// 목록 응답에서 실제 배열을 찾는다. 분석 단계(OpenApiNormalizer)가 collectionPath를 미리 확정해뒀으면
// 그 위치를 우선 신뢰하고, 없거나 그 위치에 배열이 없으면 기존 재귀 휴리스틱으로 대체한다. API마다
// 봉투 설계가 다르므로(순수 배열 / {data:[...]} / {success,data:{content:[...],totalElements}}처럼
// 겹겹이 감싸는 경우까지) 휴리스틱은 알려진 키를 먼저 확인한 뒤 나머지 속성까지 재귀적으로 훑는다.
export function extractArray(result: unknown, collectionPath?: string | null): Record<string, unknown>[] {
  if (collectionPath) {
    const viaPath = readValueAtPath(result, collectionPath);
    if (Array.isArray(viaPath)) {
      return viaPath as Record<string, unknown>[];
    }
  }
  return extractArrayHeuristic(result);
}

// 로그인 응답에서 토큰 문자열을 찾는다.
export function extractToken(result: unknown, accessTokenPath?: string | null): string | null {
  if (accessTokenPath) {
    const viaPath = readDotPath(result, accessTokenPath);
    if (viaPath) {
      return viaPath;
    }
  }
  if (!result || typeof result !== "object") {
    return null;
  }
  const obj = result as Record<string, unknown>;
  const data = obj.data && typeof obj.data === "object" ? (obj.data as Record<string, unknown>) : undefined;
  const candidates = [obj.accessToken, obj.token, data?.accessToken, data?.token];
  for (const candidate of candidates) {
    if (typeof candidate === "string" && candidate.length > 0) {
      return candidate;
    }
  }
  return null;
}

const COUNT_FIELD_KEYS = ["totalElements", "total", "totalCount", "count"];
const MAX_COUNT_FIELD_DEPTH = 3;

// Spring Data Page류 응답은 배열(content)과 같은 깊이에 총 개수(totalElements 등)를 함께 담는 경우가
// 많아, extractArray와 별개로 그 필드를 먼저 찾는다. 못 찾으면 받은 페이지의 배열 길이로 대체한다 —
// 정확한 전체 개수는 아니지만(페이지네이션 시) 최소한 데이터가 있다는 신호는 된다.
function findCountField(value: unknown, depth: number): number | null {
  if (depth > MAX_COUNT_FIELD_DEPTH || !value || typeof value !== "object") {
    return null;
  }
  const obj = value as Record<string, unknown>;
  for (const key of COUNT_FIELD_KEYS) {
    if (typeof obj[key] === "number") {
      return obj[key] as number;
    }
  }
  for (const key of Object.keys(obj)) {
    if (obj[key] && typeof obj[key] === "object" && !Array.isArray(obj[key])) {
      const nested = findCountField(obj[key], depth + 1);
      if (nested !== null) return nested;
    }
  }
  return null;
}

export function extractCount(
  result: unknown,
  totalCountPath?: string | null,
  collectionPath?: string | null
): number {
  if (totalCountPath) {
    const viaPath = readValueAtPath(result, totalCountPath);
    if (typeof viaPath === "number") {
      return viaPath;
    }
  }
  return findCountField(result, 0) ?? extractArray(result, collectionPath).length;
}

export function formatCellValue(value: unknown): string {
  if (value === null || value === undefined) {
    return "—";
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}

export function rowId(row: Record<string, unknown>): string {
  const candidate = row.id ?? row.ID ?? row.Id ?? row.uuid;
  return candidate != null ? String(candidate) : "";
}
