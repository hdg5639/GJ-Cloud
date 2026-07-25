"use client";

import { useState } from "react";
import { LoginForm } from "./LoginForm";
import { ResourceTable } from "./ResourceTable";
import { ResourceCardGrid } from "./ResourceCardGrid";
import { DetailPanel } from "./DetailPanel";
import { CreateEditModal } from "./CreateEditModal";
import { DeleteConfirmModal } from "./DeleteConfirmModal";
import { TypedConfirmModal } from "./TypedConfirmModal";
import { DashboardView } from "./DashboardView";
import { rowId } from "./api";
import { findCapabilityById } from "./utils";
import { compileBlocks, findCapabilityForBlock, findDeleteBlock, findListBlock, resolveBlocks } from "./blueprint";
import type { PreviewCapability, PreviewPage, PreviewRuntimeConfig } from "./types";

// auto-preview-design/08-compatibility-rules.md §6 Slot 규칙 3 "Overlay 최대 동시 활성 Instance
// 1개" — showCreate/editTarget/deleteTargetId를 독립된 상태 3개로 관리하면 이론상 여러 개가 동시에
// 켜질 수 있어(상세 패널에서 수정 클릭 후 삭제 클릭 등) 하나의 판별 유니언으로 묶어 상호 배타를
// 코드로 보장한다.
type OverlayState =
  | { kind: "NONE" }
  | { kind: "CREATE" }
  | { kind: "UPDATE"; row: Record<string, unknown> }
  | { kind: "DELETE"; id: string };

// GamjaBox_2.0_Key_Features.md 3·7절 — 관련 API를 페이지 하나로 묶어서 보여준다. Blueprint
// Schema/Registry/Slot 시스템 없이, resolveBlocks가 스켈레톤 종류(AUTH_PAGE/RESOURCE_LIST/
// LIST_DETAIL/DASHBOARD)별로 만들어주는 Block 목록을 순회해 고정된 패턴 컴포넌트만 조립한다.
export function PreviewPageRenderer({
  page,
  capabilities,
  config,
}: {
  page: PreviewPage;
  capabilities: PreviewCapability[];
  config: PreviewRuntimeConfig;
}) {
  const [selectedRow, setSelectedRow] = useState<Record<string, unknown> | null>(null);
  const [overlay, setOverlay] = useState<OverlayState>({ kind: "NONE" });
  const [refreshKey, setRefreshKey] = useState(0);

  const blocks = compileBlocks(resolveBlocks(page, capabilities), config.purpose);

  if (page.skeleton === "AUTH_PAGE") {
    const login = findCapabilityForBlock(blocks, capabilities, "login-form");
    if (!login) {
      return <p className="text-sm text-danger">이 페이지에 로그인 capability가 없습니다.</p>;
    }
    return <LoginForm capability={login} config={config} />;
  }

  if (page.skeleton === "DASHBOARD") {
    const dashboardBlock = blocks.find((b) => b.componentId === "dashboard-view");
    const listCapabilities = (dashboardBlock?.capabilityIds ?? [])
      .map((id) => findCapabilityById(capabilities, id))
      .filter((c): c is PreviewCapability => c !== undefined);
    return <DashboardView capabilities={listCapabilities} config={config} />;
  }

  const listBlock = findListBlock(blocks);
  const list = listBlock ? findCapabilityById(capabilities, listBlock.capabilityIds[0]) : undefined;
  const detail = findCapabilityForBlock(blocks, capabilities, "detail-panel");
  const create = findCapabilityForBlock(blocks, capabilities, "create-edit-modal", "CREATE");
  const update = findCapabilityForBlock(blocks, capabilities, "create-edit-modal", "UPDATE");
  const deleteBlock = findDeleteBlock(blocks);
  const del = deleteBlock ? findCapabilityById(capabilities, deleteBlock.capabilityIds[0]) : undefined;

  if (!list) {
    return <p className="text-sm text-danger">이 페이지에 목록 capability가 없습니다.</p>;
  }

  function refresh() {
    setRefreshKey((key) => key + 1);
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_360px]">
      {listBlock?.componentId === "resource-card-grid" ? (
        <ResourceCardGrid
          capability={list}
          config={config}
          refreshKey={refreshKey}
          onRowClick={detail || update || del ? (row) => setSelectedRow(row) : undefined}
          onCreateClick={create ? () => setOverlay({ kind: "CREATE" }) : undefined}
        />
      ) : (
        <ResourceTable
          capability={list}
          config={config}
          refreshKey={refreshKey}
          onRowClick={detail || update || del ? (row) => setSelectedRow(row) : undefined}
          onCreateClick={create ? () => setOverlay({ kind: "CREATE" }) : undefined}
        />
      )}

      {selectedRow && detail && (
        <div className="rounded-panel border border-line bg-panel p-4">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-sm font-bold">상세</h3>
            <div className="flex gap-3">
              {update && (
                <button
                  type="button"
                  className="text-xs font-bold text-brand-strong"
                  onClick={() => setOverlay({ kind: "UPDATE", row: selectedRow })}
                >
                  수정
                </button>
              )}
              {del && (
                <button
                  type="button"
                  className="text-xs font-bold text-danger"
                  onClick={() => setOverlay({ kind: "DELETE", id: rowId(selectedRow) })}
                >
                  삭제
                </button>
              )}
            </div>
          </div>
          <DetailPanel capability={detail} config={config} id={rowId(selectedRow)} />
        </div>
      )}

      {create && (
        <CreateEditModal
          open={overlay.kind === "CREATE"}
          onClose={() => setOverlay({ kind: "NONE" })}
          capability={create}
          config={config}
          onSuccess={refresh}
        />
      )}

      {update && (
        <CreateEditModal
          open={overlay.kind === "UPDATE"}
          onClose={() => setOverlay({ kind: "NONE" })}
          capability={update}
          config={config}
          initialValues={overlay.kind === "UPDATE" ? overlay.row : undefined}
          onSuccess={refresh}
        />
      )}

      {del && deleteBlock?.componentId === "typed-confirm-modal" ? (
        <TypedConfirmModal
          open={overlay.kind === "DELETE"}
          onClose={() => setOverlay({ kind: "NONE" })}
          capability={del}
          config={config}
          targetId={overlay.kind === "DELETE" ? overlay.id : ""}
          onSuccess={() => {
            setSelectedRow(null);
            setOverlay({ kind: "NONE" });
            refresh();
          }}
        />
      ) : (
        del && (
          <DeleteConfirmModal
            open={overlay.kind === "DELETE"}
            onClose={() => setOverlay({ kind: "NONE" })}
            capability={del}
            config={config}
            targetId={overlay.kind === "DELETE" ? overlay.id : ""}
            onSuccess={() => {
              setSelectedRow(null);
              setOverlay({ kind: "NONE" });
              refresh();
            }}
          />
        )
      )}
    </div>
  );
}
