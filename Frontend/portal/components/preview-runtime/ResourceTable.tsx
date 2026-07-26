"use client";

import { useEffect, useState } from "react";
import { Table, Th, Td } from "@/components/ui/table";
import { Input } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { PageLoader } from "@/components/ui/loader";
import { callCapability, extractArray, formatCellValue } from "./api";
import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

export function ResourceTable({
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
    // setState를 effect 본문에서 동기 호출하지 않고 마이크로태스크로 미룸(react-hooks/set-state-in-effect) —
    // 동작은 동일하되, 같은 커밋 내 즉시 재렌더 대신 다음 틱에서 로딩 상태로 전환된다.
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

  const columns = rows.length > 0 ? Object.keys(rows[0]) : [];

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
        <Table>
          <thead>
            <tr>
              {columns.map((column) => (
                <Th key={column}>{column}</Th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <tr
                key={index}
                onClick={() => onRowClick?.(row)}
                className={onRowClick ? "cursor-pointer hover:bg-white/[0.03]" : undefined}
              >
                {columns.map((column) => (
                  <Td key={column}>{formatCellValue(row[column])}</Td>
                ))}
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </div>
  );
}
