import { statusFieldOf, statusTone } from "../../status";
import { blueprintRecordId, blueprintRecordTitle, humanizeBlueprintKey } from "../core";
import type {
  BlueprintDirectoryEntry,
  BlueprintField,
  BlueprintKanbanColumn,
  BlueprintRecord,
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
