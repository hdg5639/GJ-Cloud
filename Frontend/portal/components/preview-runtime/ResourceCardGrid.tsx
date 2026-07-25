"use client";

import { useEffect, useState } from "react";
import { Input } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { PageLoader } from "@/components/ui/loader";
import { callCapability, extractArray, formatCellValue } from "./api";
import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

// Direction Recovery Change Request §9.1 "resource-card-grid" — ResourceTable과 데이터 fetching
// 로직·props 시그니처는 완전히 동일해(그래서 compileBlocks/BlueprintCompiler가 componentId만 보고
// 갈아끼울 수 있음), 표 대신 카드 그리드로 보여준다. PRODUCT_LIKE 목적일 때 고른다.
export function ResourceCardGrid({
  capability,
  config,
  onRowClick,
  onCreateClick,
  refreshKey,
}: {
  capability: PreviewCapability;
  config: PreviewRuntimeConfig;
  onRowClick?: (row: Record<string, unknown>) => void;
  onCreateClick?: () => void;
  refreshKey?: number;
}) {
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");

  useEffect(() => {
    let cancelled = false;
    Promise.resolve().then(async () => {
      if (cancelled) {
        return;
      }
      setLoading(true);
      setError(null);
      const query: Record<string, string> = {};
      if (capability.hasSearch && search) {
        query[capability.searchParam ?? "search"] = search;
      }
      try {
        const result = await callCapability(config, capability, { query });
        if (!cancelled) {
          setRows(extractArray(result, capability.collectionPath));
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "목록을 불러오지 못했습니다");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    });
    return () => {
      cancelled = true;
    };
  }, [capability, config, search, refreshKey]);

  return (
    <div>
      <div className="mb-3 flex items-center justify-between gap-2">
        {capability.hasSearch ? (
          <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="검색" className="max-w-xs" />
        ) : (
          <span />
        )}
        {onCreateClick && (
          <Button variant="primary" size="small" onClick={onCreateClick}>
            + 추가
          </Button>
        )}
      </div>

      {loading ? (
        <PageLoader label="불러오는 중" />
      ) : error ? (
        <p className="text-sm text-danger">{error}</p>
      ) : rows.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted-soft">데이터가 없습니다</p>
      ) : (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {rows.map((row, index) => {
            const entries = Object.entries(row);
            const [firstEntry, ...restEntries] = entries;
            return (
              <div
                key={index}
                onClick={() => onRowClick?.(row)}
                className={`rounded-panel border border-line bg-panel p-4 ${onRowClick ? "cursor-pointer hover:bg-white/[0.03]" : ""}`}
              >
                {firstEntry && <p className="mb-2 truncate text-sm font-bold">{formatCellValue(firstEntry[1])}</p>}
                <div className="space-y-1">
                  {restEntries.slice(0, 4).map(([key, value]) => (
                    <div key={key} className="flex justify-between gap-2 text-xs">
                      <span className="text-muted-soft">{key}</span>
                      <span className="truncate text-right">{formatCellValue(value)}</span>
                    </div>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
