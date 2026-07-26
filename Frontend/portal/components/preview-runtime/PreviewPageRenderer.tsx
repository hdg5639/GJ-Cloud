"use client";

import { useEffect, useRef, useState } from "react";
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
import { ChildResourceList } from "./ChildResourceList";
import { AsyncFlowProgressModal, type FlowRunView } from "./AsyncFlowProgressModal";
import { callCapability, rowId } from "./api";
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
import type { PreviewNavigationRule, PreviewPagePlan } from "@/lib/types";
import {
  createFlowContext,
  executeFlow,
  resolveExpression,
  type FlowExecutionResult,
} from "./flow/flowExecutor";
import { createCapabilityBindingCaller } from "./flow/runtime";
import type { ApiBinding, FlowBlueprint } from "./flow/types";

type OverlayState =
  | { kind: "NONE" }
  | { kind: "CREATE" }
  | { kind: "UPDATE"; row: Record<string, unknown> }
  | { kind: "DELETE"; id: string };

type NavigationType = PreviewNavigationRule["type"];

function flowTitle(flow: FlowBlueprint, capability?: PreviewCapability): string {
  return capability?.action ?? capability?.resourceName ?? flow.trigger?.actionId ?? flow.id;
}

function asStringRecord(values: Record<string, unknown>): Record<string, string> {
  return Object.fromEntries(
    Object.entries(values)
      .filter(([, value]) => value !== null && value !== undefined)
      .map(([key, value]) => [key, String(value)])
  );
}

export function PreviewPageRenderer({
  page,
  pagePlan,
  capabilities,
  blocks,
  config,
  selectedRow,
  onSelectRow,
  routeParameters = {},
  onNavigate,
  flows = [],
  bindings = [],
}: {
  page: PreviewPage;
  pagePlan?: PreviewPagePlan;
  capabilities: PreviewCapability[];
  blocks: Block[];
  config: PreviewRuntimeConfig;
  selectedRow: Record<string, unknown> | null;
  onSelectRow: (row: Record<string, unknown> | null) => void;
  routeParameters?: Record<string, string>;
  onNavigate?: (pageId: string | null, parameters: Record<string, string>, type: NavigationType) => void;
  flows?: FlowBlueprint[];
  bindings?: ApiBinding[];
}) {
  const [overlay, setOverlay] = useState<OverlayState>({ kind: "NONE" });
  const [refreshKey, setRefreshKey] = useState(0);
  const [flowRun, setFlowRun] = useState<FlowRunView | null>(null);
  const flowAbortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    return () => flowAbortRef.current?.abort();
  }, []);

  useEffect(() => {
    flowAbortRef.current?.abort();
    flowAbortRef.current = null;
    setFlowRun(null);
    setOverlay({ kind: "NONE" });
  }, [page.id]);

  if (page.skeleton === "AUTH_PAGE") {
    const login = findCapabilityForBlock(blocks, capabilities, "login-form");
    if (!login) return <p className="text-sm text-danger">이 페이지에 로그인 capability가 없습니다.</p>;
    return <LoginForm capability={login} config={config} />;
  }

  if (page.skeleton === "DASHBOARD") {
    const dashboardBlock = findDashboardBlock(blocks);
    const listCapabilities = (dashboardBlock?.capabilityIds ?? [])
      .map((id) => findCapabilityById(capabilities, id))
      .filter((capability): capability is PreviewCapability => capability !== undefined);
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
  const isFullDetailPage = page.skeleton === "RESOURCE_DETAIL" || detailBlock?.componentId === "full-detail-page";
  const createBlock = findCreateEditBlock(blocks, "CREATE");
  const create = createBlock ? findCapabilityById(capabilities, createBlock.capabilityIds[0]) : undefined;
  const updateBlock = findCreateEditBlock(blocks, "UPDATE");
  const update = updateBlock ? findCapabilityById(capabilities, updateBlock.capabilityIds[0]) : undefined;
  const deleteBlock = findDeleteBlock(blocks);
  const del = deleteBlock ? findCapabilityById(capabilities, deleteBlock.capabilityIds[0]) : undefined;
  const commandBlock = blocks.find((block) => block.componentId === "quick-action-button-group");
  const commandCapabilities = (commandBlock?.capabilityIds ?? [])
    .map((id) => findCapabilityById(capabilities, id))
    .filter((capability): capability is PreviewCapability => capability !== undefined);
  const childBlocks = blocks.filter((block) => block.componentId === "child-resource-list");

  const routeTargetId =
    pagePlan?.routeParameters.map((parameter) => routeParameters[parameter.name]).find(Boolean) ??
    routeParameters.selected ??
    routeParameters.id ??
    (selectedRow ? rowId(selectedRow) : "");
  const effectiveRow = selectedRow ?? (routeTargetId ? { id: routeTargetId, ...routeParameters } : null);

  if (!list && page.skeleton !== "RESOURCE_DETAIL") {
    return <p className="text-sm text-danger">이 페이지에 목록 capability가 없습니다.</p>;
  }
  if (page.skeleton === "RESOURCE_DETAIL" && (!detail || !routeTargetId)) {
    return (
      <div className="rounded-panel border border-line bg-panel p-5 text-sm text-muted">
        {!detail ? "이 상세 페이지에 DETAIL capability가 없습니다." : "상세 페이지에 필요한 경로 파라미터가 없습니다."}
      </div>
    );
  }

  function refresh() {
    setRefreshKey((key) => key + 1);
  }

  function performNavigation(pageId: string | null, parameters: Record<string, unknown>, type: NavigationType = "OPEN_PAGE") {
    const stringParameters = asStringRecord(parameters);
    if (onNavigate) {
      onNavigate(pageId, stringParameters, type);
      return;
    }
    const selected = stringParameters.selected ?? stringParameters.id ?? Object.values(stringParameters)[0];
    onSelectRow(selected ? { id: selected } : null);
  }

  function applyNavigationRule(rule: PreviewNavigationRule, row: Record<string, unknown>) {
    const context = createFlowContext({ row, route: routeParameters });
    const parameters: Record<string, unknown> = {};
    for (const [name, expression] of Object.entries(rule.parameters ?? {})) {
      parameters[name] = resolveExpression(expression, context);
    }
    performNavigation(rule.targetPageId, parameters, rule.type);
  }

  function handleRowSelection(row: Record<string, unknown>) {
    const rule = pagePlan?.navigationRules.find((candidate) => candidate.trigger === "row.select");
    if (rule) {
      applyNavigationRule(rule, row);
    } else {
      onSelectRow(row);
    }
  }

  async function runFlow(
    flow: FlowBlueprint,
    seed: Parameters<typeof createFlowContext>[0],
    capability?: PreviewCapability
  ): Promise<FlowExecutionResult> {
    flowAbortRef.current?.abort();
    const controller = new AbortController();
    flowAbortRef.current = controller;
    setFlowRun({
      flowId: flow.id,
      title: flowTitle(flow, capability),
      status: "RUNNING",
      stepStatuses: {},
      message: null,
    });

    try {
      const result = await executeFlow(flow, bindings, createFlowContext(seed), {
        signal: controller.signal,
        callBinding: createCapabilityBindingCaller(capabilities, config, controller.signal),
        navigate: (targetPageId, parameters) => performNavigation(targetPageId, parameters, "OPEN_PAGE"),
        onMessage: (kind, message) =>
          setFlowRun((current) => current ? { ...current, message, status: kind === "ERROR" ? "ERROR" : current.status } : current),
        onPollStatusChange: (stepId, status) =>
          setFlowRun((current) => current ? {
            ...current,
            stepStatuses: { ...current.stepStatuses, [stepId]: status },
          } : current),
        onRefreshBindingError: (bindingId, error) =>
          setFlowRun((current) => current ? {
            ...current,
            message: `${bindingId} 새로고침 실패: ${error instanceof Error ? error.message : String(error)}`,
          } : current),
      });
      setFlowRun((current) => current ? { ...current, status: result.status } : current);
      return result;
    } catch (error) {
      setFlowRun((current) => current ? {
        ...current,
        status: controller.signal.aborted ? "CANCELLED" : "ERROR",
        message: error instanceof Error ? error.message : "워크플로우 실행에 실패했습니다.",
      } : current);
      throw error;
    } finally {
      if (flowAbortRef.current === controller) flowAbortRef.current = null;
    }
  }

  const createFlow = create
    ? flows.find((flow) => flow.trigger?.pageId === page.id && flow.trigger?.actionId === create.id)
    : undefined;

  async function runCreateFlow(flow: FlowBlueprint, formValues: Record<string, string>): Promise<boolean> {
    const result = await runFlow(flow, { form: formValues, route: routeParameters }, create);
    if (result.status !== "SUCCESS") return false;
    refresh();
    return true;
  }

  async function runCommandFlow(capability: PreviewCapability, targetId: string): Promise<boolean> {
    const flow = flows.find(
      (candidate) => candidate.trigger?.pageId === page.id && candidate.trigger?.actionId === capability.id
    );
    if (!flow) {
      await callCapability(config, capability, { pathParams: { ...routeParameters, id: targetId } });
      return true;
    }
    const result = await runFlow(
      flow,
      {
        route: { ...routeParameters, selected: targetId, id: targetId },
        row: effectiveRow,
      },
      capability
    );
    return result.status === "SUCCESS";
  }

  function renderChildResources(parentId: string) {
    if (!parentId || childBlocks.length === 0) return null;
    return childBlocks.map((block) => {
      const childCapabilities = block.capabilityIds
        .map((id) => findCapabilityById(capabilities, id))
        .filter((capability): capability is PreviewCapability => capability !== undefined);
      const childList = childCapabilities.find((capability) => capability.type === "LIST");
      const childCreate = childCapabilities.find((capability) => capability.type === "CREATE");
      if (!childList) return null;
      return (
        <ChildResourceList
          key={block.instanceId}
          listCapability={childList}
          createCapability={childCreate}
          config={config}
          parentId={parentId}
          refreshKey={refreshKey}
        />
      );
    });
  }

  function renderHeaderActions(row: Record<string, unknown>) {
    return (
      <div className="flex gap-3">
        {update && (
          <button type="button" className="text-xs font-bold text-brand-strong" onClick={() => setOverlay({ kind: "UPDATE", row })}>
            수정
          </button>
        )}
        {del && (
          <button type="button" className="text-xs font-bold text-danger" onClick={() => setOverlay({ kind: "DELETE", id: rowId(row) })}>
            삭제
          </button>
        )}
      </div>
    );
  }

  function renderDetail(targetRow: Record<string, unknown>, standalone: boolean) {
    const id = routeTargetId || rowId(targetRow);
    return (
      <div className="rounded-panel border border-line bg-panel p-4">
        <div className="mb-3 flex items-center justify-between">
          {standalone ? (
            <button type="button" className="text-xs font-bold text-brand-strong" onClick={() => performNavigation(null, {}, "GO_BACK")}>
              ← 이전으로
            </button>
          ) : (
            <h3 className="text-sm font-bold">상세</h3>
          )}
          {renderHeaderActions(targetRow)}
        </div>
        {commandCapabilities.length > 0 && (
          <div className="mb-3">
            <QuickActionButtonGroup
              capabilities={commandCapabilities}
              config={config}
              targetId={id}
              onSuccess={refresh}
              onExecute={(capability) => runCommandFlow(capability, id)}
            />
          </div>
        )}
        {detail && (isFullDetailPage ? (
          <FullDetailPage capability={detail} config={config} id={id} refreshKey={refreshKey} />
        ) : (
          <DetailPanel capability={detail} config={config} id={id} refreshKey={refreshKey} />
        ))}
        {renderChildResources(id)}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {page.skeleton === "RESOURCE_DETAIL" && effectiveRow ? (
        renderDetail(effectiveRow, true)
      ) : selectedRow && detail && isFullDetailPage ? (
        <div>
          <div className="mb-3">
            <button type="button" className="text-xs font-bold text-brand-strong" onClick={() => onSelectRow(null)}>
              ← 목록으로
            </button>
          </div>
          {renderDetail(selectedRow, false)}
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-[1fr_360px]">
          {listBlock?.componentId === "resource-card-grid" ? (
            <ResourceCardGrid
              capability={list!}
              config={config}
              refreshKey={refreshKey}
              onRowClick={detail || update || del || commandBlock || pagePlan?.navigationRules.length ? handleRowSelection : undefined}
              onCreateClick={create ? () => setOverlay({ kind: "CREATE" }) : undefined}
            />
          ) : (
            <ResourceTable
              capability={list!}
              config={config}
              refreshKey={refreshKey}
              onRowClick={detail || update || del || commandBlock || pagePlan?.navigationRules.length ? handleRowSelection : undefined}
              onCreateClick={create ? () => setOverlay({ kind: "CREATE" }) : undefined}
            />
          )}
          {selectedRow && (detail || commandCapabilities.length > 0) && renderDetail(selectedRow, false)}
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
          pathParamId={routeTargetId || undefined}
          onSuccess={refresh}
        />
      ) : (
        <CreateEditModal
          open={overlay.kind === "UPDATE"}
          onClose={() => setOverlay({ kind: "NONE" })}
          capability={update}
          config={config}
          initialValues={overlay.kind === "UPDATE" ? overlay.row : undefined}
          pathParamId={routeTargetId || undefined}
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
            if (page.skeleton === "RESOURCE_DETAIL") {
              performNavigation(null, {}, "GO_BACK");
            }
            onSelectRow(null);
            setOverlay({ kind: "NONE" });
            refresh();
          }}
        />
      ) : del ? (
        <DeleteConfirmModal
          open={overlay.kind === "DELETE"}
          onClose={() => setOverlay({ kind: "NONE" })}
          capability={del}
          config={config}
          targetId={overlay.kind === "DELETE" ? overlay.id : ""}
          onSuccess={() => {
            if (page.skeleton === "RESOURCE_DETAIL") {
              performNavigation(null, {}, "GO_BACK");
            }
            onSelectRow(null);
            setOverlay({ kind: "NONE" });
            refresh();
          }}
        />
      ) : null}

      <AsyncFlowProgressModal
        run={flowRun}
        onCancel={() => flowAbortRef.current?.abort()}
        onClose={() => setFlowRun(null)}
      />
    </div>
  );
}
