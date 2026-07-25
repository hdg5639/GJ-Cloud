"use client";

import { useState } from "react";
import { LoginForm } from "./LoginForm";
import { ResourceTable } from "./ResourceTable";
import { DetailPanel } from "./DetailPanel";
import { CreateEditModal } from "./CreateEditModal";
import { DeleteConfirmModal } from "./DeleteConfirmModal";
import { rowId } from "./api";
import { findCapabilityByType } from "./utils";
import type { PreviewCapability, PreviewPage, PreviewRuntimeConfig } from "./types";

// GamjaBox_2.0_Key_Features.md 3·7절 — 관련 API를 페이지 하나로 묶어서 보여준다. Blueprint
// Schema/Registry/Slot 시스템 없이, PageDraftGenerator가 만든 스켈레톤 종류(AUTH_PAGE/RESOURCE_LIST/
// LIST_DETAIL)별로 고정된 5개 패턴 컴포넌트만 조립한다.
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
  const [showCreate, setShowCreate] = useState(false);
  const [editTarget, setEditTarget] = useState<Record<string, unknown> | null>(null);
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  if (page.skeleton === "AUTH_PAGE") {
    const login = findCapabilityByType(capabilities, page, "LOGIN");
    if (!login) {
      return <p className="text-sm text-danger">이 페이지에 로그인 capability가 없습니다.</p>;
    }
    return <LoginForm capability={login} config={config} />;
  }

  const list = findCapabilityByType(capabilities, page, "LIST");
  const detail = findCapabilityByType(capabilities, page, "DETAIL");
  const create = findCapabilityByType(capabilities, page, "CREATE");
  const update = findCapabilityByType(capabilities, page, "UPDATE");
  const del = findCapabilityByType(capabilities, page, "DELETE");

  if (!list) {
    return <p className="text-sm text-danger">이 페이지에 목록 capability가 없습니다.</p>;
  }

  function refresh() {
    setRefreshKey((key) => key + 1);
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_360px]">
      <ResourceTable
        capability={list}
        config={config}
        refreshKey={refreshKey}
        onRowClick={detail || update || del ? (row) => setSelectedRow(row) : undefined}
        onCreateClick={create ? () => setShowCreate(true) : undefined}
      />

      {selectedRow && detail && (
        <div className="rounded-panel border border-line bg-panel p-4">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-sm font-bold">상세</h3>
            <div className="flex gap-3">
              {update && (
                <button type="button" className="text-xs font-bold text-brand-strong" onClick={() => setEditTarget(selectedRow)}>
                  수정
                </button>
              )}
              {del && (
                <button
                  type="button"
                  className="text-xs font-bold text-danger"
                  onClick={() => setDeleteTargetId(rowId(selectedRow))}
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
          open={showCreate}
          onClose={() => setShowCreate(false)}
          capability={create}
          config={config}
          onSuccess={refresh}
        />
      )}

      {update && (
        <CreateEditModal
          open={editTarget !== null}
          onClose={() => setEditTarget(null)}
          capability={update}
          config={config}
          initialValues={editTarget ?? undefined}
          onSuccess={refresh}
        />
      )}

      {del && (
        <DeleteConfirmModal
          open={deleteTargetId !== null}
          onClose={() => setDeleteTargetId(null)}
          capability={del}
          config={config}
          targetId={deleteTargetId ?? ""}
          onSuccess={() => {
            setSelectedRow(null);
            refresh();
          }}
        />
      )}
    </div>
  );
}
