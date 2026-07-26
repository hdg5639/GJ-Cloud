"use client";

import { useEffect, useState } from "react";
import { callCapability, extractArray, formatCellValue, rowId } from "./api";
import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

const MAX_ROWS_PER_RESOURCE = 5;
const ID_LIKE_FIELDS = ["id", "ID", "Id", "uuid"];

// 행 하나를 사람이 읽을 수 있는 한 줄 요약으로 압축한다 — 스키마를 모르는 채로 임의 API 응답을
// 보여줘야 해서, ResourceTable처럼 전체 컬럼을 그리지 않고 id를 뺀 처음 두 필드만 보여준다.
function summarizeRow(row: Record<string, unknown>): string {
  const entries = Object.entries(row)
    .filter(([key]) => !ID_LIKE_FIELDS.includes(key))
    .slice(0, 2);
  if (entries.length === 0) {
    return rowId(row);
  }
  return entries.map(([key, value]) => `${key}: ${formatCellValue(value)}`).join(" · ");
}

interface FeedState {
  loading: boolean;
  rows: Record<string, unknown>[];
  error: string | null;
}

// Direction Recovery Change Request §9.5 "recent-activity-dashboard" — dashboard-view와 데이터
// 요구조건(LIST capability 여러 개)은 동일하고, 개수 카드 대신 리소스마다 최근 항목 몇 개를
// 피드 형태로 보여준다. 새 capability 필드가 필요 없어(collectionPath로 이미 충분) DashboardView와
// 같은 callCapability 페칭 골격을 그대로 재사용한다.
export function RecentActivityDashboard({
  capabilities,
  config,
}: {
  capabilities: PreviewCapability[];
  config: PreviewRuntimeConfig;
}) {
  const [feeds, setFeeds] = useState<Record<string, FeedState>>({});

  useEffect(() => {
    let cancelled = false;
    capabilities.forEach((capability) => {
      callCapability(config, capability)
        .then((result) => {
          if (cancelled) return;
          const rows = extractArray(result, capability.collectionPath).slice(0, MAX_ROWS_PER_RESOURCE);
          setFeeds((prev) => ({ ...prev, [capability.id]: { loading: false, rows, error: null } }));
        })
        .catch((err) => {
          if (cancelled) return;
          setFeeds((prev) => ({
            ...prev,
            [capability.id]: { loading: false, rows: [], error: err instanceof Error ? err.message : "불러오기 실패" },
          }));
        });
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [capabilities, config.apiBaseUrl, config.authToken]);

  if (capabilities.length === 0) {
    return <p className="text-sm text-danger">이 페이지에 표시할 목록 capability가 없습니다.</p>;
  }

  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {capabilities.map((capability) => {
        const state = feeds[capability.id] ?? { loading: true, rows: [], error: null };
        return (
          <div key={capability.id} className="rounded-md border border-line-strong bg-white/[0.02] p-4">
            <p className="mb-2 text-xs font-bold text-muted-soft">{capability.resourceName}</p>
            {state.loading && <p className="text-xs text-muted">불러오는 중...</p>}
            {state.error && <p className="text-[11px] text-danger">{state.error}</p>}
            {!state.loading && !state.error && state.rows.length === 0 && (
              <p className="text-xs text-muted">항목이 없습니다</p>
            )}
            <ul className="space-y-1">
              {state.rows.map((row, index) => (
                <li key={rowId(row) || index} className="truncate text-xs">
                  {summarizeRow(row)}
                </li>
              ))}
            </ul>
          </div>
        );
      })}
    </div>
  );
}
