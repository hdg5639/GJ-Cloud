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
  const res = await fetch(url, {
    method: capability.method,
    headers: {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...(config.authToken ? { Authorization: `Bearer ${config.authToken}` } : {}),
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  if (!res.ok) {
    throw new Error(`${capability.method} ${url} 요청이 실패했습니다 (${res.status})`);
  }
  if (res.status === 204) {
    return null;
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

// Backend/Ops CapabilityExtractor의 PASSWORD_FIELD_HINTS와 동일한 휴리스틱 — 필드명만 보고
// <input type="password">로 그릴지 판단한다(실제 스키마의 format 정보까지는 Phase A가 옮기지 않음).
export function isPasswordLikeField(fieldName: string): boolean {
  const lower = fieldName.toLowerCase();
  return lower.includes("password") || lower === "pw" || lower === "pwd";
}

// 목록 응답에서 실제 배열을 찾는다 — 순수 배열이거나, Spring Data Page류 봉투(content/items/data/list/results).
export function extractArray(result: unknown): Record<string, unknown>[] {
  if (Array.isArray(result)) {
    return result as Record<string, unknown>[];
  }
  if (result && typeof result === "object") {
    const obj = result as Record<string, unknown>;
    for (const key of ["content", "items", "data", "list", "results"]) {
      if (Array.isArray(obj[key])) {
        return obj[key] as Record<string, unknown>[];
      }
    }
  }
  return [];
}

// 로그인 응답에서 흔한 위치(data.accessToken/token/accessToken)의 토큰 문자열을 찾는다.
export function extractToken(result: unknown): string | null {
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
