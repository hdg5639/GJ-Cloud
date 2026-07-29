"use client";

import type { PreviewCompiledScenarioStage } from "@/lib/types";
import type { BlueprintRecord } from "../blueprints/core";
import {
  partKind,
  renderCollectionPart,
  renderDetailPart,
} from "../blueprints/adapters/registry";
import type { ScenarioStageProjection } from "./projection";

export function ScenarioBlueprintSurface({
  stage,
  projection,
  rows,
  selectedId,
  onSelect,
}: {
  stage: PreviewCompiledScenarioStage;
  projection: ScenarioStageProjection | undefined;
  rows: BlueprintRecord[];
  selectedId: string;
  onSelect: (row: BlueprintRecord) => void;
}) {
  const componentId = projection?.blueprintComponentId;
  if (!componentId) return null;
  const kind = partKind(componentId);

  if (kind === "collection" && stage.role === "SELECT" && rows.length > 0) {
    return (
      <div className="mt-3 overflow-hidden rounded-lg border border-line-strong bg-background p-3">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <p className="text-[10px] font-extrabold tracking-[.1em] text-muted-soft">BLUEPRINT PROJECTION</p>
          <span className="rounded-full border border-line px-2 py-1 text-[10px] font-bold text-muted">
            {projection.blueprintLabel ?? componentId}
          </span>
        </div>
        {renderCollectionPart(componentId, {
          rows,
          onRowClick: onSelect,
        })}
        {selectedId && (
          <p className="mt-2 text-[11px] font-bold text-brand-strong">선택됨 · {selectedId}</p>
        )}
      </div>
    );
  }

  if (kind === "detail" && (stage.role === "INSPECT" || stage.role === "COMPARE")) {
    const selected = rows.find((row) => String(row.id ?? row.uuid ?? row.key ?? "") === selectedId);
    if (!selected) return null;
    return (
      <div className="mt-3 overflow-hidden rounded-lg border border-line-strong bg-background p-3">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <p className="text-[10px] font-extrabold tracking-[.1em] text-muted-soft">BLUEPRINT PROJECTION</p>
          <span className="rounded-full border border-line px-2 py-1 text-[10px] font-bold text-muted">
            {projection.blueprintLabel ?? componentId}
          </span>
        </div>
        {renderDetailPart(componentId, { record: selected })}
      </div>
    );
  }
  return null;
}
