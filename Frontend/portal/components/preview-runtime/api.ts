import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

// 주의(알려진 제약): 이 렌더러는 브라우저에서 대상 API로 직접 fetch를 보낸다. 대상 API가 이 Preview
// origin을 CORS로 허용하지 않으면 요청이 브라우저에서 막힌다 — Phase C 범위에서는 해결하지 않고,
// 실제 배포(Phase D 이후)에서 프록시가 필요한지 별도로 판단한다.

function buildUrl(
  config: PreviewRuntimeConfig,
  capability: PreviewCapability,
  pathParams: Record<string, string> = {},
  query: Record<string, string> = {}
): string {
  let path = capability.path;
  for (const [key, value] of Object.entries(pathParams)) {
    path = path.replace(`{${key}}`, encodeURIComponent(value));
  }
  const base = config.apiBaseUrl.replace(/\/$/, "");
  const url = new URL(base + path);
  for (const [key, value] of Object.entries(query)) {
    if (value) url.searchParams.set(key, value);
  }
  return url.toString();
}

export async function callCapability(
  config: PreviewRuntimeConfig,
  capability: PreviewCapability,
  options: {
    pathParams?: Record<string, string>;
    query?: Record<string, string>;
    body?: Record<string, unknown>;
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
        ...(config.authToken ? { Authorization: `Bearer ${config.authToken}` } : {}),
      },
      body: options.body ? JSON.stringify(options.body) : undefined,
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

// 목록 응답에서 실제 배열을 찾는다. API마다 봉투 설계가 다르므로(순수 배열 / {data:[...]} /
// {success,data:{content:[...],totalElements}}처럼 겹겹이 감싸는 경우까지) 이름을 고정하지 않고
// 알려진 키를 먼저 확인한 뒤 나머지 속성까지 재귀적으로 훑는다.
export function extractArray(result: unknown, depth = 0): Record<string, unknown>[] {
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
    const nested = extractArray(obj[key], depth + 1);
    if (nested.length > 0) {
      return nested;
    }
  }
  return [];
}

// 분석 단계(또는 사용자가 직접 지정)에서 확인된 dot-path("data.accessToken" 등)를 우선 신뢰한다 —
// 분석이 못 찾았거나 그 위치에 값이 없을 때만 흔한 이름(accessToken/token)으로 추측한다.
function readDotPath(result: unknown, dotPath: string): string | null {
  let current: unknown = result;
  for (const key of dotPath.split(".")) {
    if (!current || typeof current !== "object") {
      return null;
    }
    current = (current as Record<string, unknown>)[key];
  }
  return typeof current === "string" && current.length > 0 ? current : null;
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
