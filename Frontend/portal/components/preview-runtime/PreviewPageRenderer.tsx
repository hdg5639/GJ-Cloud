"use client";

import { useState } from "react";
import { LoginForm } from "./LoginForm";
import { ResourceTable } from "./ResourceTable";
import { ResourceCardGrid } from "./ResourceCardGrid";
import { DetailPanel } from "./DetailPanel";
import { CreateEditModal } from "./CreateEditModal";
import { FormDrawer } from "./FormDrawer";
import { DeleteConfirmModal } from "./DeleteConfirmModal";
import { TypedConfirmModal } from "./TypedConfirmModal";
import { DashboardView } from "./DashboardView";
import { RecentActivityDashboard } from "./RecentActivityDashboard";
import { QuickActionButtonGroup } from "./QuickActionButtonGroup";
import { FullDetailPage } from "./FullDetailPage";
import { rowId } from "./api";
import { findCapabilityById } from "./utils";
import {
  findCapabilityForBlock,
  findCreateEditBlock,
  findDashboardBlock,
  findDeleteBlock,
  findDetailBlock,
  findListBlock,
} from "./blueprint";
import type { Block } from "./blueprint";
import type { PreviewCapability, PreviewPage, PreviewRuntimeConfig } from "./types";
import { createFlowContext, executeFlow } from "./flow/flowExecutor";
import { createCapabilityBindingCaller } from "./flow/runtime";
import type { ApiBinding, FlowBlueprint } from "./flow/types";

// auto-preview-design/08-compatibility-rules.md §6 Slot 규칙 3 "Overlay 최대 동시 활성 Instance
// 1개" — showCreate/editTarget/deleteTargetId를 독립된 상태 3개로 관리하면 이론상 여러 개가 동시에
// 켜질 수 있어(상세 패널에서 수정 클릭 후 삭제 클릭 등) 하나의 판별 유니언으로 묶어 상호 배타를
// 코드로 보장한다.
type OverlayState =
  | { kind: "NONE" }
  | { kind: "CREATE" }
  | { kind: "UPDATE"; row: Record<string, unknown> }
  | { kind: "DELETE"; id: string };

// GamjaBox_2.0_Key_Features.md 3·7절 — 관련 API를 페이지 하나로 묶어서 보여준다. Direction Recovery
// Change Request §13.1 — 이 컴포넌트는 어떤 Block이 존재해야 하는지(조립 규칙)를 스스로 판단하지
// 않는다. Block은 백엔드(PreviewBlockResolver+BlueprintCompiler)가 이미 계산해 `blocks` prop으로
// 넘어오고, 여기서는 그 Block마다 어떤 React 컴포넌트로 그릴지만 결정한다.
// Workflow Composition Phase 2 Change Request §7 "Navigation Requirements" — 상세 선택 상태를
// 이 컴포넌트가 스스로 들고 있지 않고(controlled) 호출 측(마법사)이 URL 쿼리파라미터와 동기화해
// 넘겨준다. 그래야 새로고침·브라우저 뒤로/앞으로가기·직접 URL 진입이 실제로 동작한다.
export function PreviewPageRenderer({
  page,
  capabilities,
  blocks,
  config,
  selectedRow,
  onSelectRow,
  flows = [],
  bindings = [],
}: {
  page: PreviewPage;
  capabilities: PreviewCapability[];
  blocks: Block[];
  config: PreviewRuntimeConfig;
  selectedRow: Record<string, unknown> | null;
  onSelectRow: (row: Record<string, unknown> | null) => void;
  // Workflow Composition Phase 2 Change Request §22 7번 — RuleBasedFlowGenerator가 만든 workflow.
  // 아직 CREATE 패턴만 실제로 실행한다(§14 WP-8의 첫 배선, FlowExecutor를 처음 실사용).
  flows?: FlowBlueprint[];
  bindings?: ApiBinding[];
}) {
  const [overlay, setOverlay] = useState<OverlayState>({ kind: "NONE" });
  const [refreshKey, setRefreshKey] = useState(0);

  if (page.skeleton === "AUTH_PAGE") {
    const login = findCapabilityForBlock(blocks, capabilities, "login-form");
    if (!login) {
      return <p className="text-sm text-danger">이 페이지에 로그인 capability가 없습니다.</p>;
    }
    return <LoginForm capability={login} config={config} />;
  }

  if (page.skeleton === "DASHBOARD") {
    const dashboardBlock = findDashboardBlock(blocks);
    const listCapabilities = (dashboardBlock?.capabilityIds ?? [])
      .map((id) => findCapabilityById(capabilities, id))
      .filter((c): c is PreviewCapability => c !== undefined);
    return dashboardBlock?.componentId === "recent-activity-dashboard" ? (
      <RecentActivityDashboard capabilities={listCapabilities} config={config} />
    ) : (
      <DashboardView capabilities={listCapabilities} config={config} />
    );
  }

  const listBlock = findListBlock(blocks);
  const list = listBlock ? findCapabilityById(capabilities, listBlock.capabilityIds[0]) : undefined;
  const detailBlock = findDetailBlock(blocks);
  const detail = detailBlock ? findCapabilityById(capabilities, detailBlock.capabilityIds[0]) : undefined;
  const isFullDetailPage = detailBlock?.componentId === "full-detail-page";
  const createBlock = findCreateEditBlock(blocks, "CREATE");
  const create = createBlock ? findCapabilityById(capabilities, createBlock.capabilityIds[0]) : undefined;
  const updateBlock = findCreateEditBlock(blocks, "UPDATE");
  const update = updateBlock ? findCapabilityById(capabilities, updateBlock.capabilityIds[0]) : undefined;
  const deleteBlock = findDeleteBlock(blocks);
  const del = deleteBlock ? findCapabilityById(capabilities, deleteBlock.capabilityIds[0]) : undefined;
  const commandBlock = blocks.find((b) => b.componentId === "quick-action-button-group");
  const commandCapabilities = (commandBlock?.capabilityIds ?? [])
    .map((id) => findCapabilityById(capabilities, id))
    .filter((c): c is PreviewCapability => c !== undefined);

  if (!list) {
    return <p className="text-sm text-danger">이 페이지에 목록 capability가 없습니다.</p>;
  }

  function refresh() {
    setRefreshKey((key) => key + 1);
  }

  // §14 WP-8 "FlowExecutor를 실제로 배선" — 이 페이지의 CREATE 액션에 RuleBasedFlowGenerator가 만든
  // flow가 있으면(trigger.pageId===이 페이지, trigger.actionId===create.id) CreateEditModal/FormDrawer가
  // 직접 API를 부르지 않고 폼 값만 넘겨, 여기서 flow 전체(API_CALL→NAVIGATE)를 실행한다. NAVIGATE는
  // 지금 생성기가 항상 자기 자신(같은 페이지) + selected 파라미터로만 만들어서 onSelectRow 호출로
  // 충분하다 — 다른 페이지로 이동하는 NAVIGATE는 아직 생성되지 않아 처리하지 않는다(알려진 범위).
  const createFlow = create
    ? flows.find((flow) => flow.trigger?.pageId === page.id && flow.trigger?.actionId === create.id)
    : undefined;

  async function runCreateFlow(flow: FlowBlueprint, formValues: Record<string, string>) {
    const ctx = createFlowContext({ form: formValues });
    await executeFlow(flow, bindings, ctx, {
      callBinding: createCapabilityBindingCaller(capabilities, config),
      navigate: (_pageId, parameters) => {
        const selected = parameters.selected != null ? String(parameters.selected) : null;
        onSelectRow(selected ? { id: selected } : null);
      },
    });
    refresh();
  }

  // 두 레이아웃(side-detail-panel/full-detail-page)이 공유하는 수정/삭제 버튼 — 선택된 row를 파라미터로
  // 명시적으로 받아, 클로저로 selectedRow(| null 타입)를 그대로 참조할 때 생기는 null 체크 문제를 피한다.
  function renderHeaderActions(row: Record<string, unknown>) {
    return (
      <div className="flex gap-3">
        {update && (
          <button
            type="button"
            className="text-xs font-bold text-brand-strong"
            onClick={() => setOverlay({ kind: "UPDATE", row })}
          >
            수정
          </button>
        )}
        {del && (
          <button
            type="button"
            className="text-xs font-bold text-danger"
            onClick={() => setOverlay({ kind: "DELETE", id: rowId(row) })}
          >
            삭제
          </button>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {selectedRow && detail && isFullDetailPage ? (
        <div className="rounded-panel border border-line bg-panel p-4">
          <div className="mb-3 flex items-center justify-between">
            <button
              type="button"
              className="text-xs font-bold text-brand-strong"
              onClick={() => onSelectRow(null)}
            >
              ← 목록으로
            </button>
            {renderHeaderActions(selectedRow)}
          </div>
          {commandCapabilities.length > 0 && (
            <div className="mb-3">
              <QuickActionButtonGroup
                capabilities={commandCapabilities}
                config={config}
                targetId={rowId(selectedRow)}
                onSuccess={refresh}
              />
            </div>
          )}
          <FullDetailPage capability={detail} config={config} id={rowId(selectedRow)} refreshKey={refreshKey} />
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-[1fr_360px]">
          {listBlock?.componentId === "resource-card-grid" ? (
            <ResourceCardGrid
              capability={list}
              config={config}
              refreshKey={refreshKey}
              onRowClick={detail || update || del || commandBlock ? (row) => onSelectRow(row) : undefined}
              onCreateClick={create ? () => setOverlay({ kind: "CREATE" }) : undefined}
            />
          ) : (
            <ResourceTable
              capability={list}
              config={config}
              refreshKey={refreshKey}
              onRowClick={detail || update || del || commandBlock ? (row) => onSelectRow(row) : undefined}
              onCreateClick={create ? () => setOverlay({ kind: "CREATE" }) : undefined}
            />
          )}

          {selectedRow && (detail || commandCapabilities.length > 0) && (
            <div className="rounded-panel border border-line bg-panel p-4">
              <div className="mb-3 flex items-center justify-between">
                <h3 className="text-sm font-bold">상세</h3>
                {renderHeaderActions(selectedRow)}
              </div>
              {commandCapabilities.length > 0 && (
                <div className="mb-3">
                  <QuickActionButtonGroup
                    capabilities={commandCapabilities}
                    config={config}
                    targetId={rowId(selectedRow)}
                    onSuccess={refresh}
                  />
                </div>
              )}
              {detail && <DetailPanel capability={detail} config={config} id={rowId(selectedRow)} refreshKey={refreshKey} />}
            </div>
          )}
        </div>
      )}

      {create && (createBlock?.componentId === "form-drawer" ? (
        <FormDrawer
          open={overlay.kind === "CREATE"}
          onClose={() => setOverlay({ kind: "NONE" })}
          capability={create}
          config={config}
          onSuccess={refresh}
          onSubmitOverride={createFlow ? (values) => runCreateFlow(createFlow, values) : undefined}
        />
      ) : (
        <CreateEditModal
          open={overlay.kind === "CREATE"}
          onClose={() => setOverlay({ kind: "NONE" })}
          capability={create}
          config={config}
          onSuccess={refresh}
          onSubmitOverride={createFlow ? (values) => runCreateFlow(createFlow, values) : undefined}
        />
      ))}

      {update && (updateBlock?.componentId === "form-drawer" ? (
        <FormDrawer
          open={overlay.kind === "UPDATE"}
          onClose={() => setOverlay({ kind: "NONE" })}
          capability={update}
          config={config}
          initialValues={overlay.kind === "UPDATE" ? overlay.row : undefined}
          onSuccess={refresh}
        />
      ) : (
        <CreateEditModal
          open={overlay.kind === "UPDATE"}
          onClose={() => setOverlay({ kind: "NONE" })}
          capability={update}
          config={config}
          initialValues={overlay.kind === "UPDATE" ? overlay.row : undefined}
          onSuccess={refresh}
        />
      ))}

      {del && deleteBlock?.componentId === "typed-confirm-modal" ? (
        <TypedConfirmModal
          open={overlay.kind === "DELETE"}
          onClose={() => setOverlay({ kind: "NONE" })}
          capability={del}
          config={config}
          targetId={overlay.kind === "DELETE" ? overlay.id : ""}
          onSuccess={() => {
            onSelectRow(null);
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
              onSelectRow(null);
              setOverlay({ kind: "NONE" });
              refresh();
            }}
          />
        )
      )}
    </div>
  );
}
