"use client";

import { useEffect, useState } from "react";
import { Input } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { PageLoader } from "@/components/ui/loader";
import { callCapability, extractArray, formatCellValue, rowId } from "./api";
import { StatusBadge } from "./StatusBadge";
import { statusFieldOf, summarizeStatus, toneStyle } from "./status";
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
    // config 객체 전체를 넣으면 부모가 매 렌더마다 새 config를 만들 때(onApiCall 로그 갱신 등)
    // effect가 무한 재발화한다 — 실제 데이터 소스인 apiBaseUrl/authToken만 의존한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [capability, config.apiBaseUrl, config.authToken, search, refreshKey]);

  // 상태 필드가 있으면 요약 스트립과 카드 배지를 그린다 — 목록을 "동작형 스웨거"가 아니라 대시보드처럼
  // 만드는 핵심. 없으면(status/state/phase 없는 리소스) 종전대로 이름+필드만 보여준다.
  const statusField = rows.length > 0 ? statusFieldOf(rows[0]) : null;
  const summary = statusField ? summarizeStatus(rows, statusField) : [];

  return (
    <div>
      <div className="mb-3 flex items-center justify-between gap-2">
        <div className="flex items-baseline gap-2">
          <h3 className="text-sm font-extrabold capitalize">{capability.resourceName}</h3>
          {rows.length > 0 && (
            <span className="text-xs font-semibold text-muted-soft tabular-nums">{rows.length}</span>
          )}
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

      {summary.length > 0 && (
        <div className="mb-3 flex flex-wrap gap-2">
          {summary.map((group) => {
            const style = toneStyle(group.tone);
            return (
              <span
                key={group.value}
                className="inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1 text-xs font-bold"
                style={{ color: style.color, background: style.background, borderColor: style.borderColor }}
              >
                <span style={{ width: 6, height: 6, borderRadius: "9999px", background: style.color }} />
                {group.value}
                <span className="tabular-nums opacity-70">{group.count}</span>
              </span>
            );
          })}
        </div>
      )}

      {loading ? (
        <PageLoader label="불러오는 중" />
      ) : error ? (
        <p className="text-sm text-danger">{error}</p>
      ) : rows.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted-soft">데이터가 없습니다</p>
      ) : (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {rows.map((row, index) => {
            const title = row.name ?? row.title ?? row.label ?? rowId(row);
            const statusValue = statusField ? row[statusField] : undefined;
            const detailEntries = Object.entries(row).filter(
              ([key]) => key !== statusField && !["name", "title", "label"].includes(key)
            );
            return (
              <div
                key={index}
                onClick={() => onRowClick?.(row)}
                className={`rounded-panel border border-line bg-panel p-4 transition-colors ${onRowClick ? "cursor-pointer hover:border-line-strong hover:bg-white/[0.03]" : ""}`}
              >
                <div className="flex items-start justify-between gap-2">
                  <p className="min-w-0 truncate text-sm font-extrabold">{formatCellValue(title)}</p>
                  {typeof statusValue === "string" && <StatusBadge value={statusValue} size="sm" />}
                </div>
                <div className="mt-3 space-y-1">
                  {detailEntries.slice(0, 4).map(([key, value]) => (
                    <div key={key} className="flex justify-between gap-2 text-xs">
                      <span className="text-muted-soft">{key}</span>
                      <span className="truncate text-right tabular-nums">{formatCellValue(value)}</span>
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
