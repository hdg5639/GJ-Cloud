"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { PageLoader } from "@/components/ui/loader";
import { Table, Td, Th } from "@/components/ui/table";
import { callCapability, extractArray, formatCellValue, rowId } from "./api";
import { CreateEditModal } from "./CreateEditModal";
import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

// 자식 액션(삭제/수정) 경로는 부모+자식 두 파라미터를 가진다(예: /machines/{machineId}/ports/{portId}).
// 경로의 placeholder 이름을 뽑아 마지막(자식 자신)에는 행 id를, 앞쪽(부모 컨텍스트)에는 parentId를 채운다.
function buildRowActionParams(path: string, parentId: string, childId: string): Record<string, string> {
  const names = Array.from(path.matchAll(/\{([^}]+)\}/g), (match) => match[1]);
  const params: Record<string, string> = {};
  names.forEach((name, index) => {
    params[name] = index === names.length - 1 ? childId : parentId;
  });
  return params;
}

// Workflow Composition Phase 2 AC-6 — 선택된 부모 리소스의 하위 컬렉션을 조회하고, 대응 CREATE
// capability가 있으면 같은 부모 ID로 새 항목을 만든 뒤 목록을 다시 불러온다. 특정 VM/port 도메인을
// 하드코딩하지 않고 nested path + resourceName으로 Resolver가 묶은 capability를 그대로 사용한다.
export function ChildResourceList({
  listCapability,
  createCapability,
  deleteCapability,
  config,
  parentId,
  refreshKey,
}: {
  listCapability: PreviewCapability;
  createCapability?: PreviewCapability;
  deleteCapability?: PreviewCapability;
  config: PreviewRuntimeConfig;
  parentId: string;
  refreshKey?: number;
}) {
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [localRefreshKey, setLocalRefreshKey] = useState(0);

  async function handleDelete(row: Record<string, unknown>) {
    if (!deleteCapability) return;
    const childId = rowId(row);
    if (!window.confirm("이 항목을 삭제하시겠습니까?")) return;
    setDeletingId(childId);
    setError(null);
    try {
      await callCapability(config, deleteCapability, {
        pathParams: buildRowActionParams(deleteCapability.path, parentId, childId),
      });
      setLocalRefreshKey((key) => key + 1);
    } catch (err) {
      setError(err instanceof Error ? err.message : "삭제하지 못했습니다");
    } finally {
      setDeletingId(null);
    }
  }

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
    // config 객체 전체를 넣으면 부모가 매 렌더마다 새 config를 만들 때(onApiCall 로그 갱신 등)
    // effect가 무한 재발화한다 — 실제 데이터 소스인 apiBaseUrl/authToken만 의존한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [config.apiBaseUrl, config.authToken, listCapability, parentId, refreshKey, localRefreshKey]);

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
              <tr>
                {columns.map((column) => <Th key={column}>{column}</Th>)}
                {deleteCapability && <Th> </Th>}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, index) => (
                <tr key={index}>
                  {columns.map((column) => <Td key={column}>{formatCellValue(row[column])}</Td>)}
                  {deleteCapability && (
                    <Td>
                      <button
                        type="button"
                        className="text-xs font-bold text-danger disabled:opacity-40"
                        disabled={deletingId === rowId(row)}
                        onClick={() => handleDelete(row)}
                      >
                        {deletingId === rowId(row) ? "삭제 중…" : "삭제"}
                      </button>
                    </Td>
                  )}
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
