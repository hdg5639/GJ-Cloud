// Auto Preview 비주얼 레이어 — 리소스의 status/state/phase 필드를 색으로 구분해 "동작형 스웨거"가
// 아니라 대시보드처럼 보이게 하는 공통 규칙. 백엔드 CapabilityExtractor의 전이/종료 토큰 어휘와
// 의미를 맞춘다(단, 여기선 종료를 다시 ok/idle/danger로 세분한다). 배포 템플릿
// (PreviewComposeArtifactBuilder)에도 동일 로직이 이식돼 있으니 한쪽을 고치면 반드시 둘 다 본다.

export type StatusTone = "ok" | "warn" | "idle" | "danger" | "neutral";

const STATUS_FIELD_KEYS = new Set(["status", "state", "phase"]);

// 정규화(소문자 + _/- 제거) 후 매칭. 어느 집합에도 없으면 "neutral"(값은 배지로 보여주되 색 강조 없음).
const TONE_BY_TOKEN: Record<string, StatusTone> = {};
function register(tone: StatusTone, tokens: string[]) {
  for (const token of tokens) TONE_BY_TOKEN[token] = tone;
}
register("ok", ["running", "ready", "active", "available", "completed", "complete", "succeeded",
  "success", "done", "healthy", "online", "approved", "enabled", "live", "passed", "ok", "up"]);
register("warn", ["pending", "provisioning", "creating", "processing", "inprogress", "starting",
  "queued", "initializing", "building", "deploying", "waiting", "scheduling", "restarting",
  "stopping", "updating", "pausing", "retrying", "syncing", "pending"]);
register("idle", ["stopped", "inactive", "disabled", "paused", "draft", "archived", "offline",
  "closed", "expired", "suspended", "idle", "unknown", "down"]);
register("danger", ["failed", "error", "terminated", "cancelled", "canceled", "rejected", "denied",
  "crashed", "unhealthy", "timeout", "timedout", "deleted", "aborted", "declined"]);

function normalize(value: string): string {
  return value.toLowerCase().replace(/[_-]/g, "");
}

export function isStatusKey(key: string): boolean {
  return STATUS_FIELD_KEYS.has(normalize(key));
}

export function statusTone(value: unknown): StatusTone {
  if (typeof value !== "string") return "neutral";
  return TONE_BY_TOKEN[normalize(value)] ?? "neutral";
}

// 행에서 상태로 쓸 필드 키를 찾는다(status > state > phase 순, 문자열 값인 것만). 없으면 null.
export function statusFieldOf(row: Record<string, unknown>): string | null {
  const keys = Object.keys(row);
  for (const preferred of ["status", "state", "phase"]) {
    const match = keys.find((key) => normalize(key) === preferred && typeof row[key] === "string");
    if (match) return match;
  }
  return null;
}

export interface StatusGroup {
  value: string;
  tone: StatusTone;
  count: number;
}

// 목록 rows를 상태값별로 집계한다(처음 나온 순서 유지) — status-summary 스트립이 쓴다.
export function summarizeStatus(rows: Record<string, unknown>[], fieldKey: string): StatusGroup[] {
  const order: string[] = [];
  const counts = new Map<string, number>();
  for (const row of rows) {
    const raw = row[fieldKey];
    if (typeof raw !== "string") continue;
    if (!counts.has(raw)) order.push(raw);
    counts.set(raw, (counts.get(raw) ?? 0) + 1);
  }
  return order.map((value) => ({ value, tone: statusTone(value), count: counts.get(value) ?? 0 }));
}

// tone → 인라인 스타일(hex). Tailwind 커스텀 색 설정에 의존하지 않도록 색은 인라인으로 준다 —
// 라이트(포털)/다크(배포) 양쪽 바탕에서 tint+border+텍스트 조합이 읽힌다.
export function toneStyle(tone: StatusTone): { color: string; background: string; borderColor: string } {
  switch (tone) {
    case "ok":
      return { color: "#3fbf74", background: "rgba(70,209,127,0.13)", borderColor: "rgba(70,209,127,0.30)" };
    case "warn":
      return { color: "#d98c12", background: "rgba(245,166,35,0.14)", borderColor: "rgba(245,166,35,0.32)" };
    case "danger":
      return { color: "#e0484d", background: "rgba(242,85,90,0.14)", borderColor: "rgba(242,85,90,0.32)" };
    case "idle":
    case "neutral":
    default:
      return { color: "#7c8791", background: "rgba(139,147,160,0.14)", borderColor: "rgba(139,147,160,0.30)" };
  }
}
