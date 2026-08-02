"use client";

import { useEffect, useRef, useState } from "react";
import { LoginForm } from "./LoginForm";
import { ResourceTable } from "./ResourceTable";
import { ResourceCardGrid } from "./ResourceCardGrid";
import { DetailPanel } from "./DetailPanel";
import { DashboardView } from "./DashboardView";
import { RecentActivityDashboard } from "./RecentActivityDashboard";
import { QuickActionButtonGroup } from "./QuickActionButtonGroup";
import { FullDetailPage } from "./FullDetailPage";
import { ChildResourceList } from "./ChildResourceList";
import { AsyncFlowProgressModal, type FlowRunView } from "./AsyncFlowProgressModal";
import {
  CollectionAdapter,
  DetailBlueprintAdapter,
  DashboardAdapter,
  isCollectionPart,
  isDetailPart,
  isDashboardPart,
  isActionPart,
} from "./blueprints/adapters";
import {
  BlueprintActionPart,
  BlueprintPageChrome,
} from "./blueprints/adapters/BlueprintPartHost";
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
import {
  createJourneyBlueprint,
  JourneyRuntime,
  type JourneyExecutionResult,
  type JourneyMode,
  type JourneySession,
} from "./journey";

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
  pages = [],
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
  pages?: PreviewPage[];
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
  const [journey, setJourney] = useState<JourneySession | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [flowRun, setFlowRun] = useState<FlowRunView | null>(null);
  const flowAbortRef = useRef<AbortController | null>(null);
  const journeyNavigationRef = useRef<{
    sessionId: string;
    pageId: string | null;
    parameters: Record<string, unknown>;
    type: NavigationType;
  } | null>(null);
  const feedbackComponentId = blocks.find((block) => block.slot === "page.feedback")?.componentId;

  useEffect(() => {
    return () => flowAbortRef.current?.abort();
  }, []);

  useEffect(() => {
    flowAbortRef.current?.abort();
    flowAbortRef.current = null;
    journeyNavigationRef.current = null;
    setFlowRun(null);
    setJourney(null);
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
    const dashboardContent = dashboardBlock && isDashboardPart(dashboardBlock.componentId) ? (
        <DashboardAdapter componentId={dashboardBlock.componentId} capabilities={listCapabilities} config={config} refreshKey={refreshKey} feedbackComponentId={feedbackComponentId} />
      ) : dashboardBlock?.componentId === "recent-activity-dashboard" ? (
      <RecentActivityDashboard capabilities={listCapabilities} config={config} />
    ) : (
      <DashboardView capabilities={listCapabilities} config={config} />
    );
    return (
      <BlueprintPageChrome blocks={blocks} page={page} pages={pages} onNavigate={(pageId) => performNavigation(pageId, {}, "OPEN_PAGE")}>
        {dashboardContent}
      </BlueprintPageChrome>
    );
  }

  const listBlock = findListBlock(blocks);
  const list = listBlock ? findCapabilityById(capabilities, listBlock.capabilityIds[0]) : undefined;
  const detailBlock = findDetailBlock(blocks);
  const detail = detailBlock ? findCapabilityById(capabilities, detailBlock.capabilityIds[0]) : undefined;
  const isDetailBlueprintPart = isDetailPart(detailBlock?.componentId ?? "");
  const isFullDetailPage = page.skeleton === "RESOURCE_DETAIL" || detailBlock?.componentId === "full-detail-page"
    || isDetailBlueprintPart;
  const createBlock = findCreateEditBlock(blocks, "CREATE");
  const create = createBlock ? findCapabilityById(capabilities, createBlock.capabilityIds[0]) : undefined;
  const updateBlock = findCreateEditBlock(blocks, "UPDATE");
  const update = updateBlock ? findCapabilityById(capabilities, updateBlock.capabilityIds[0]) : undefined;
  const deleteBlock = findDeleteBlock(blocks);
  const del = deleteBlock ? findCapabilityById(capabilities, deleteBlock.capabilityIds[0]) : undefined;
  const commandBlock = blocks.find((block) =>
    block.slot === "page.actions"
    && (block.componentId === "quick-action-button-group" || isActionPart(block.componentId))
  );
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
    capability?: PreviewCapability,
    showProgress = true,
    navigate?: (targetPageId: string, parameters: Record<string, unknown>) => void
  ): Promise<FlowExecutionResult> {
    flowAbortRef.current?.abort();
    const controller = new AbortController();
    flowAbortRef.current = controller;
    if (showProgress) {
      setFlowRun({
        flowId: flow.id,
        title: flowTitle(flow, capability),
        status: "RUNNING",
        stepStatuses: {},
        message: null,
      });
    }

    try {
      const result = await executeFlow(flow, bindings, createFlowContext(seed), {
        signal: controller.signal,
        callBinding: createCapabilityBindingCaller(capabilities, config, controller.signal),
        navigate: navigate ?? ((targetPageId, parameters) => performNavigation(targetPageId, parameters, "OPEN_PAGE")),
        onMessage: (kind, message) => showProgress &&
          setFlowRun((current) => current ? { ...current, message, status: kind === "ERROR" ? "ERROR" : current.status } : current),
        onPollStatusChange: (stepId, status) => showProgress &&
          setFlowRun((current) => current ? {
            ...current,
            stepStatuses: { ...current.stepStatuses, [stepId]: status },
          } : current),
        onRefreshBindingError: (bindingId, error) => showProgress &&
          setFlowRun((current) => current ? {
            ...current,
            message: `${bindingId} 새로고침 실패: ${error instanceof Error ? error.message : String(error)}`,
          } : current),
      });
      if (showProgress) setFlowRun((current) => current ? { ...current, status: result.status } : current);
      return result;
    } catch (error) {
      if (showProgress) {
        setFlowRun((current) => current ? {
          ...current,
          status: controller.signal.aborted ? "CANCELLED" : "ERROR",
          message: error instanceof Error ? error.message : "워크플로우 실행에 실패했습니다.",
        } : current);
      }
      throw error;
    } finally {
      if (flowAbortRef.current === controller) flowAbortRef.current = null;
    }
  }

  function beginJourney(
    mode: JourneyMode,
    capability: PreviewCapability,
    componentId?: string,
    initialValues: Record<string, unknown> = {},
    targetId = ""
  ) {
    flowAbortRef.current?.abort();
    setFlowRun(null);
    setJourney({
      id: `${page.id}:${capability.id}:${Date.now()}`,
      blueprint: createJourneyBlueprint({ pageId: page.id, mode, capability, componentId }),
      capability,
      targetId,
      initialValues,
    });
  }

  async function executeJourney(
    session: JourneySession,
    values: Record<string, unknown>
  ): Promise<JourneyExecutionResult> {
    const capability = session.capability;
    const flow = flows.find(
      (candidate) => candidate.trigger?.pageId === page.id && candidate.trigger?.actionId === capability.id
    );
    if (flow) {
      const result = await runFlow(
        flow,
        {
          form: values,
          route: {
            ...routeParameters,
            selected: session.targetId,
            id: session.targetId,
          },
          row: Object.keys(session.initialValues).length > 0 ? session.initialValues : effectiveRow,
          context: { journeyId: session.blueprint.id, journeyMode: session.blueprint.mode },
        },
        capability,
        false,
        (targetPageId, parameters) => {
          journeyNavigationRef.current = {
            sessionId: session.id,
            pageId: targetPageId,
            parameters,
            type: "OPEN_PAGE",
          };
        }
      );
      if (result.status !== "SUCCESS") {
        throw new Error(result.status === "CANCELLED"
          ? "작업이 취소되었습니다."
          : `워크플로우가 ${result.status} 상태로 종료되었습니다.`);
      }
    } else {
      await callCapability(config, capability, {
        pathParams: session.targetId
          ? { ...routeParameters, selected: session.targetId, id: session.targetId }
          : routeParameters,
        body: capability.type === "DELETE" || capability.method.toUpperCase() === "GET" ? undefined : values,
      });
    }

    if (session.blueprint.mode === "DELETE" && page.skeleton === "RESOURCE_DETAIL") {
      journeyNavigationRef.current = {
        sessionId: session.id,
        pageId: null,
        parameters: {},
        type: "GO_BACK",
      };
    }
    refresh();
    return {
      message: `${capability.action ?? capability.resourceName} 작업이 완료되었습니다.`,
    };
  }

  function renderChildResources(parentId: string) {
    if (!parentId || childBlocks.length === 0) return null;
    return childBlocks.map((block) => {
      const childCapabilities = block.capabilityIds
        .map((id) => findCapabilityById(capabilities, id))
        .filter((capability): capability is PreviewCapability => capability !== undefined);
      const childList = childCapabilities.find((capability) => capability.type === "LIST");
      const childCreate = childCapabilities.find((capability) => capability.type === "CREATE");
      const childDelete = childCapabilities.find((capability) => capability.type === "DELETE");
      if (!childList) return null;
      return (
        <ChildResourceList
          key={block.instanceId}
          listCapability={childList}
          createCapability={childCreate}
          deleteCapability={childDelete}
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
          <button
            type="button"
            className="text-xs font-bold text-brand-strong"
            onClick={() => beginJourney("UPDATE", update, updateBlock?.componentId, row, routeTargetId || rowId(row))}
          >
            수정
          </button>
        )}
        {del && (
          <button
            type="button"
            className="text-xs font-bold text-danger"
            onClick={() => beginJourney("DELETE", del, deleteBlock?.componentId, row, routeTargetId || rowId(row))}
          >
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
            {commandBlock && isActionPart(commandBlock.componentId) ? (
              <BlueprintActionPart
                componentId={commandBlock.componentId}
                capabilities={commandCapabilities}
                onExecute={(capability) => beginJourney("COMMAND", capability, undefined, targetRow, id)}
              />
            ) : (
              <QuickActionButtonGroup
                capabilities={commandCapabilities}
                config={config}
                targetId={id}
                onSuccess={refresh}
                onExecute={async (capability) => {
                  beginJourney("COMMAND", capability, undefined, targetRow, id);
                  return false;
                }}
              />
            )}
          </div>
        )}
        {detail && (isDetailBlueprintPart && detailBlock ? (
          <DetailBlueprintAdapter componentId={detailBlock.componentId} capability={detail} config={config} id={id} refreshKey={refreshKey} feedbackComponentId={feedbackComponentId} />
        ) : isFullDetailPage ? (
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
      <BlueprintPageChrome blocks={blocks} page={page} pages={pages} onNavigate={(pageId) => performNavigation(pageId, {}, "OPEN_PAGE")}>
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
            {listBlock && isCollectionPart(listBlock.componentId) ? (
              <CollectionAdapter
                componentId={listBlock.componentId}
                capability={list!}
                config={config}
                refreshKey={refreshKey}
                onRowClick={detail || update || del || commandBlock || pagePlan?.navigationRules.length ? handleRowSelection : undefined}
                onCreateClick={create ? () => beginJourney("CREATE", create, createBlock?.componentId) : undefined}
                feedbackComponentId={feedbackComponentId}
              />
            ) : listBlock?.componentId === "resource-card-grid" ? (
              <ResourceCardGrid
                capability={list!}
                config={config}
                refreshKey={refreshKey}
                onRowClick={detail || update || del || commandBlock || pagePlan?.navigationRules.length ? handleRowSelection : undefined}
                onCreateClick={create ? () => beginJourney("CREATE", create, createBlock?.componentId) : undefined}
              />
            ) : (
              <ResourceTable
                capability={list!}
                config={config}
                refreshKey={refreshKey}
                onRowClick={detail || update || del || commandBlock || pagePlan?.navigationRules.length ? handleRowSelection : undefined}
                onCreateClick={create ? () => beginJourney("CREATE", create, createBlock?.componentId) : undefined}
              />
            )}
            {selectedRow && (detail || commandCapabilities.length > 0) && renderDetail(selectedRow, false)}
          </div>
        )}
      </BlueprintPageChrome>

      <JourneyRuntime
        key={journey?.id ?? "journey-idle"}
        session={journey}
        onExecute={executeJourney}
        onCompleted={(completedSession) => {
          if (completedSession.blueprint.mode === "DELETE") onSelectRow(null);
          const pendingNavigation = journeyNavigationRef.current;
          if (pendingNavigation?.sessionId === completedSession.id) {
            performNavigation(
              pendingNavigation.pageId,
              pendingNavigation.parameters,
              pendingNavigation.type
            );
            journeyNavigationRef.current = null;
          }
        }}
        onClose={() => {
          flowAbortRef.current?.abort();
          journeyNavigationRef.current = null;
          setJourney(null);
        }}
      />

      <AsyncFlowProgressModal
        run={flowRun}
        onCancel={() => flowAbortRef.current?.abort()}
        onClose={() => setFlowRun(null)}
      />
    </div>
  );
}
