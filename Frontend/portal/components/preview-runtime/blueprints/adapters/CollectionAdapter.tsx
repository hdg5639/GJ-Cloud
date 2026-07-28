"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { PageLoader } from "@/components/ui/loader";
import type { PreviewCapability, PreviewRuntimeConfig } from "../../types";
import { renderCollectionPart } from "./registry";
import { useListRows } from "./useResource";

// 목록 계열 Blueprint 파츠(entity-directory/kanban-collection/commerce-product-grid)를 실제 API에
// 연결한다. capability로 목록을 불러와 각 파츠가 기대하는 prop으로 매핑하고, 행 선택은 원본 row를
// 되돌려 준다(선택 → 상세 이동이 기존 카드/테이블과 동일하게 동작).
export function CollectionAdapter({
  componentId,
  capability,
  config,
  onRowClick,
  onCreateClick,
  refreshKey,
}: {
  componentId: string;
  capability: PreviewCapability;
  config: PreviewRuntimeConfig;
  onRowClick?: (row: Record<string, unknown>) => void;
  onCreateClick?: () => void;
  refreshKey?: number;
}) {
  const [search, setSearch] = useState("");
  const { rows, loading, error } = useListRows(capability, config, refreshKey, search);

  return (
    <div>
      <div className="mb-3 flex items-center justify-between gap-2">
        <div className="flex items-baseline gap-2">
          <h3 className="text-sm font-extrabold capitalize">{capability.resourceName}</h3>
          {rows.length > 0 && <span className="text-xs font-semibold text-muted-soft tabular-nums">{rows.length}</span>}
        </div>
        <div className="flex items-center gap-2">
          {capability.hasSearch && (
            <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="검색" className="max-w-[180px]" />
          )}
          {onCreateClick && (
            <Button variant="primary" size="small" onClick={onCreateClick}>
              + 추가
            </Button>
          )}
        </div>
      </div>

      {loading ? (
        <PageLoader label="불러오는 중" />
      ) : error ? (
        <p className="text-sm text-danger">{error}</p>
      ) : rows.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted-soft">데이터가 없습니다</p>
      ) : (
        renderCollectionPart(componentId, { rows, onRowClick })
      )}
    </div>
  );
}
