"use client";

import type { Block } from "./blueprint";
import { baseComponentFor, componentKind, partsForKind } from "./blueprints/adapters";

const KIND_LABEL: Record<string, string> = {
  collection: "목록",
  detail: "상세",
  dashboard: "대시보드",
};

// 마법사 파츠 선택기 — 활성 페이지의 스왑 대상 Block(목록/상세/대시보드)마다 드롭다운을 그려
// 사용자가 자동/기본/특정 파츠를 고르게 한다. 고른 값은 partOverrides("pageId/instanceId"→componentId)로
// 상위에 전달되고, 상위가 blocks를 다시 받아 프리뷰에 반영한다(백엔드 BlueprintPartSelector가 처리).
export function BlueprintPartPicker({
  blocks,
  pageId,
  purpose,
  overrides,
  onChange,
}: {
  blocks: Block[];
  pageId: string;
  purpose: string | null;
  overrides: Record<string, string>;
  onChange: (nextOverrides: Record<string, string>) => void;
}) {
  const swappable = blocks.filter((block) => componentKind(block.componentId));
  if (swappable.length === 0) return null;

  function select(instanceId: string, value: string) {
    const key = `${pageId}/${instanceId}`;
    const next = { ...overrides };
    if (value) next[key] = value;
    else delete next[key]; // "자동"
    onChange(next);
  }

  return (
    <div className="mb-3 flex flex-wrap items-center gap-3 rounded-md border border-line-strong bg-white/[0.02] px-3 py-2">
      <span className="text-[11px] font-bold uppercase tracking-widest text-muted-soft">레이아웃</span>
      {swappable.map((block) => {
        const kind = componentKind(block.componentId)!;
        const key = `${pageId}/${block.instanceId}`;
        const value = overrides[key] ?? "";
        return (
          <label key={block.instanceId} className="flex items-center gap-1.5 text-xs">
            <span className="text-muted-soft">{KIND_LABEL[kind] ?? kind}</span>
            <select
              value={value}
              onChange={(event) => select(block.instanceId, event.target.value)}
              className="rounded border border-line-strong bg-panel px-2 py-1 text-xs font-semibold"
            >
              <option value="">자동</option>
              <option value={baseComponentFor(kind, purpose)}>기본</option>
              {partsForKind(kind).map((part) => (
                <option key={part.id} value={part.id}>
                  {part.label}
                </option>
              ))}
            </select>
          </label>
        );
      })}
    </div>
  );
}
