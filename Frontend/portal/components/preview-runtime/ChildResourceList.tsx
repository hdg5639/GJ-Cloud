"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { PageLoader } from "@/components/ui/loader";
import { Table, Td, Th } from "@/components/ui/table";
import { callCapability, extractArray, formatCellValue } from "./api";
import { CreateEditModal } from "./CreateEditModal";
import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

// Workflow Composition Phase 2 AC-6 — 선택된 부모 리소스의 하위 컬렉션을 조회하고, 대응 CREATE
// capability가 있으면 같은 부모 ID로 새 항목을 만든 뒤 목록을 다시 불러온다. 특정 VM/port 도메인을
// 하드코딩하지 않고 nested path + resourceName으로 Resolver가 묶은 capability를 그대로 사용한다.
export function ChildResourceList({
  listCapability,
  createCapability,
  config,
  parentId,
  refreshKey,
}: {
  listCapability: PreviewCapability;
  createCapability?: PreviewCapability;
  config: PreviewRuntimeConfig;
  parentId: string;
  refreshKey?: number;
}) {
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [localRefreshKey, setLocalRefreshKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    Promise.resolve().then(async () => {
      if (cancelled || !parentId) return;
      setLoading(true);
      setError(null);
      try {
        const result = await callCapability(config, listCapability, { pathParams: { id: parentId } });
        if (!cancelled) setRows(extractArray(result, listCapability.collectionPath));
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : "하위 리소스를 불러오지 못했습니다");
      } finally {
        if (!cancelled) setLoading(false);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [config, listCapability, parentId, refreshKey, localRefreshKey]);

  const columns = rows.length > 0 ? Object.keys(rows[0]) : [];

  return (
    <section className="mt-4 rounded-panel border border-line-strong bg-black/[0.08] p-4">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div>
          <p className="text-[10px] font-bold uppercase tracking-widest text-muted-soft">Related resources</p>
          <h4 className="mt-0.5 text-sm font-extrabold">{listCapability.resourceName}</h4>
        </div>
        {createCapability && (
          <Button type="button" variant="secondary" size="small" onClick={() => setCreateOpen(true)}>
            + 추가
          </Button>
        )}
      </div>

      {loading ? (
        <PageLoader label="하위 리소스 불러오는 중" />
      ) : error ? (
        <p className="text-xs text-danger">{error}</p>
      ) : rows.length === 0 ? (
        <p className="py-5 text-center text-xs text-muted-soft">연결된 항목이 없습니다</p>
      ) : (
        <div className="overflow-x-auto">
          <Table>
            <thead>
              <tr>{columns.map((column) => <Th key={column}>{column}</Th>)}</tr>
            </thead>
            <tbody>
              {rows.map((row, index) => (
                <tr key={index}>
                  {columns.map((column) => <Td key={column}>{formatCellValue(row[column])}</Td>)}
                </tr>
              ))}
            </tbody>
          </Table>
        </div>
      )}

      {createCapability && (
        <CreateEditModal
          open={createOpen}
          onClose={() => setCreateOpen(false)}
          capability={createCapability}
          config={config}
          pathParamId={parentId}
          onSuccess={() => setLocalRefreshKey((key) => key + 1)}
        />
      )}
    </section>
  );
}
