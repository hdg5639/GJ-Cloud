import { statusFieldOf, statusTone } from "../../status";
import type { StatusTone } from "../../status";
import { blueprintRecordId, blueprintRecordTitle, humanizeBlueprintKey } from "../core";
import type {
  BlueprintAlert,
  BlueprintDirectoryEntry,
  BlueprintField,
  BlueprintKanbanColumn,
  BlueprintMediaItem,
  BlueprintRecord,
  BlueprintTimelineEvent,
} from "../core";

// 임의 리소스 row를 각 Blueprint 파츠가 기대하는 prop 형태로 변환한다. 도메인 하드코딩 없이
// blueprintRecordTitle/statusFieldOf 같은 일반 규칙만 쓴다(machines·todos·customers 등 무엇이 와도 동작).

// 파츠의 onSelect/onCardClick(매핑된 entry.id)을 원본 row로 되돌린다.
export function rowById(rows: BlueprintRecord[], id: string): BlueprintRecord | undefined {
  return rows.find((row) => blueprintRecordId(row) === id);
}

const SUBTITLE_KEYS = ["email", "description", "summary", "region", "type", "category", "owner", "role"];

function pickSubtitle(row: BlueprintRecord, excludeKeys: string[]): string | undefined {
  for (const key of SUBTITLE_KEYS) {
    const match = Object.keys(row).find((k) => k.toLowerCase() === key && !excludeKeys.includes(k));
    if (match && (typeof row[match] === "string" || typeof row[match] === "number")) {
      return String(row[match]);
    }
  }
  return undefined;
}

export function toDirectoryEntries(rows: BlueprintRecord[]): BlueprintDirectoryEntry[] {
  return rows.map((row) => {
    const statusKey = statusFieldOf(row);
    const status = statusKey && typeof row[statusKey] === "string" ? (row[statusKey] as string) : undefined;
    return {
      id: blueprintRecordId(row),
      title: blueprintRecordTitle(row),
      subtitle: pickSubtitle(row, statusKey ? [statusKey] : []),
      status,
      metadata: toFields(row, ["id", "name", "title", "label", statusKey ?? ""]).slice(0, 3),
    };
  });
}

export function toKanbanColumns(rows: BlueprintRecord[]): BlueprintKanbanColumn[] {
  const statusKey = rows.length > 0 ? statusFieldOf(rows[0]) : null;
  if (!statusKey) {
    return [{ id: "all", title: "전체", cards: rows.map((row) => toKanbanCard(row, statusKey)) }];
  }
  const order: string[] = [];
  const grouped = new Map<string, BlueprintRecord[]>();
  for (const row of rows) {
    const value = typeof row[statusKey] === "string" ? (row[statusKey] as string) : "기타";
    if (!grouped.has(value)) {
      grouped.set(value, []);
      order.push(value);
    }
    grouped.get(value)!.push(row);
  }
  return order.map((value) => ({
    id: value,
    title: value,
    tone: statusTone(value),
    cards: (grouped.get(value) ?? []).map((row) => toKanbanCard(row, statusKey)),
  }));
}

function toKanbanCard(row: BlueprintRecord, statusKey: string | null) {
  return {
    id: blueprintRecordId(row),
    title: blueprintRecordTitle(row),
    subtitle: pickSubtitle(row, statusKey ? [statusKey] : []),
    status: statusKey && typeof row[statusKey] === "string" ? (row[statusKey] as string) : undefined,
    metadata: toFields(row, ["id", "name", "title", "label", statusKey ?? ""]).slice(0, 3),
  };
}

// record → 필드 목록(제외 키 지정). 상세/카드 메타데이터 공용.
export function toFields(record: BlueprintRecord, excludeKeys: string[] = []): BlueprintField[] {
  const exclude = new Set(excludeKeys.filter(Boolean));
  return Object.entries(record)
    .filter(([key]) => !exclude.has(key))
    .map(([key, value]) => ({ key, label: humanizeBlueprintKey(key), value }));
}

// 행의 상태 문자열 값(status/state/phase). 없으면 undefined.
function statusValueOf(row: BlueprintRecord): string | undefined {
  const statusKey = statusFieldOf(row);
  return statusKey && typeof row[statusKey] === "string" ? (row[statusKey] as string) : undefined;
}

// 후보 키 중 첫 문자열/숫자 값을 찾아 문자열로 반환(대소문자 무시). 없으면 undefined.
function pickString(row: BlueprintRecord, keys: string[]): string | undefined {
  const lower = keys.map((k) => k.toLowerCase());
  for (const key of Object.keys(row)) {
    if (lower.includes(key.toLowerCase()) && (typeof row[key] === "string" || typeof row[key] === "number")) {
      return String(row[key]);
    }
  }
  return undefined;
}

function severityFromTone(tone: StatusTone): BlueprintAlert["severity"] {
  if (tone === "danger") return "ERROR";
  if (tone === "warn") return "WARNING";
  return "INFO";
}

// row → 경보(AlertInbox). 상태 톤을 심각도로, 부제 규칙을 설명으로 매핑한다.
export function toAlerts(rows: BlueprintRecord[]): BlueprintAlert[] {
  return rows.map((row) => {
    const status = statusValueOf(row);
    return {
      id: blueprintRecordId(row),
      title: blueprintRecordTitle(row),
      description: pickSubtitle(row, statusKeyExclude(row)),
      severity: severityFromTone(statusTone(status)),
      timestamp: pickString(row, ["createdAt", "updatedAt", "timestamp", "date", "time", "occurredAt", "firedAt"]),
      source: pickString(row, ["source", "service", "component", "origin"]),
      acknowledged: false,
    };
  });
}

// row → 미디어 항목(MediaGalleryCollection). 도메인 하드코딩 없이 일반 키 규칙만 쓴다.
export function toMediaItems(rows: BlueprintRecord[]): BlueprintMediaItem[] {
  return rows.map((row) => ({
    id: blueprintRecordId(row),
    title: blueprintRecordTitle(row),
    subtitle: pickSubtitle(row, statusKeyExclude(row)),
    thumbnailUrl: pickString(row, ["thumbnailUrl", "thumbnail", "imageUrl", "image", "url", "cover"]) ?? null,
    mediaType: pickString(row, ["mediaType", "type", "format", "kind"]),
    sizeLabel: pickString(row, ["sizeLabel", "size", "fileSize"]),
    status: statusValueOf(row),
  }));
}

// row → 타임라인 이벤트(TimelineCollection/상세 활동 등에서 재사용). 상태 톤을 그대로 전달한다.
export function toTimelineEvents(rows: BlueprintRecord[]): BlueprintTimelineEvent[] {
  return rows.map((row) => {
    const status = statusValueOf(row);
    return {
      id: blueprintRecordId(row),
      title: blueprintRecordTitle(row),
      description: pickSubtitle(row, statusKeyExclude(row)),
      timestamp: pickString(row, ["createdAt", "updatedAt", "timestamp", "date", "time", "occurredAt"]),
      actor: pickString(row, ["actor", "author", "user", "owner", "by"]),
      status,
      tone: statusTone(status),
    };
  });
}

function statusKeyExclude(row: BlueprintRecord): string[] {
  const statusKey = statusFieldOf(row);
  return statusKey ? [statusKey] : [];
}
