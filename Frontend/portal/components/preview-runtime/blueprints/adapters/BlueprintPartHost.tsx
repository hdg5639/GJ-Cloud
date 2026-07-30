"use client";

import { useState, type ReactNode } from "react";
import type { Block } from "../../blueprint";
import { callCapability } from "../../api";
import type { PreviewCapability, PreviewPage, PreviewRuntimeConfig } from "../../types";
import type { BlueprintAction, BlueprintNavItem, BlueprintOption, BlueprintWorkflowStep } from "../core";
import { partDescriptor, partKind, renderBlueprintPart } from "./registry";

function navigationItems(page: PreviewPage, capabilities: PreviewCapability[]): BlueprintNavItem[] {
  const resources = Array.from(new Set(capabilities.map((capability) => capability.resourceName).filter(Boolean)));
  const labels = resources.length > 0 ? resources : [page.title];
  return labels.map((label, index) => ({
    id: `${page.id}-${index}`,
    label,
    active: index === 0,
  }));
}

function navigationNode(componentId: string | undefined, items: BlueprintNavItem[]): ReactNode {
  if (!componentId || componentId === "default-navigation") return null;
  return renderBlueprintPart(componentId, { items, activeId: items[0]?.id, onNavigate: () => undefined });
}

function layoutProps(
  componentId: string,
  page: PreviewPage,
  content: ReactNode,
  navigation: ReactNode,
  items: BlueprintNavItem[]
): Record<string, unknown> {
  const common = {
    title: page.title,
    description: `${page.title} Auto Preview`,
    children: content,
    main: content,
    primary: content,
    catalog: content,
    editor: content,
    master: content,
    topology: content,
    stage: content,
    navigation,
    header: null,
    toolbar: null,
    summary: null,
    aside: null,
    secondary: null,
    footer: null,
    overlay: null,
    controls: null,
    activity: null,
    cartSummary: null,
    categoryRail: navigation,
    inspector: null,
    preview: null,
    actions: null,
    detail: null,
    health: null,
    events: null,
    runbook: null,
    steps: [
      { id: "preview", label: "Preview", description: "Generated page", status: "ACTIVE" },
    ] satisfies BlueprintWorkflowStep[],
    currentStepId: "preview",
  };
  if (componentId === "admin-workspace-layout") {
    return { ...common, navigation: items, onNavigate: () => undefined };
  }
  if (componentId === "settings-workbench-layout") {
    return { ...common, sections: items, onSelect: () => undefined };
  }
  return common;
}

export function BlueprintPageChrome({
  blocks,
  page,
  capabilities,
  children,
}: {
  blocks: Block[];
  page: PreviewPage;
  capabilities: PreviewCapability[];
  children: ReactNode;
}) {
  const layoutId = blocks.find((block) => block.slot === "page.layout")?.componentId;
  const navigationId = blocks.find((block) => block.slot === "page.navigation")?.componentId;
  const themeId = blocks.find((block) => block.slot === "page.theme")?.componentId;
  const items = navigationItems(page, capabilities);
  const navigation = navigationNode(navigationId, items);

  let content = children;
  if (navigation && (!layoutId || layoutId === "default-layout")) {
    content = <div className="space-y-4">{navigation}{content}</div>;
  }
  if (layoutId && layoutId !== "default-layout") {
    content = renderBlueprintPart(layoutId, layoutProps(layoutId, page, content, navigation, items));
  }
  if (themeId && themeId !== "default-theme") {
    content = renderBlueprintPart(themeId, { children: content });
  }
  return <>{content}</>;
}

export function BlueprintActionPart({
  componentId,
  capabilities,
  onExecute,
}: {
  componentId: string;
  capabilities: PreviewCapability[];
  onExecute: (capability: PreviewCapability) => void | Promise<void>;
}) {
  const actions: BlueprintAction[] = capabilities.map((capability) => ({
    id: capability.id,
    label: capability.action ?? capability.resourceName ?? capability.id,
    tone: capability.risk === "DESTRUCTIVE" ? "danger" : "secondary",
  }));
  return <>{renderBlueprintPart(componentId, {
    actions,
    selectedCount: 0,
    onAction: (action: BlueprintAction) => {
      const capability = capabilities.find((candidate) => candidate.id === action.id);
      if (capability) void onExecute(capability);
    },
  })}</>;
}

function asBody(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

export function BlueprintOverlayPart({
  componentId,
  open,
  onClose,
  capability,
  config,
  initialValues,
  targetId,
  onSuccess,
  onSubmitOverride,
}: {
  componentId: string;
  open: boolean;
  onClose: () => void;
  capability: PreviewCapability;
  config: PreviewRuntimeConfig;
  initialValues?: Record<string, unknown>;
  targetId?: string;
  onSuccess: () => void;
  onSubmitOverride?: (values: Record<string, string>) => Promise<boolean | void>;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(value: unknown = initialValues ?? {}) {
    setBusy(true);
    setError(null);
    try {
      const body = asBody(value);
      if (onSubmitOverride) {
        const stringBody = Object.fromEntries(Object.entries(body).map(([key, item]) => [key, String(item ?? "")]));
        const completed = await onSubmitOverride(stringBody);
        if (completed === false) return;
      } else {
        await callCapability(config, capability, {
          pathParams: targetId ? { id: targetId } : {},
          body: capability.type === "DELETE" ? undefined : body,
        });
      }
      onSuccess();
      onClose();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "요청에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  const options: BlueprintOption[] = [
    { value: "default", label: "Default" },
    { value: "primary", label: "Primary" },
  ];
  const callback = (...values: unknown[]) => submit(values[0]);
  const kind = partKind(componentId);
  const props: Record<string, unknown> = {
    open,
    onClose,
    onCancel: onClose,
    busy,
    submitting: busy,
    initialValues,
    context: initialValues,
    options,
    actions: options,
    plans: options,
    regions: options,
    environments: options,
    strategies: options,
    roles: options,
    organizations: options,
    targetOptions: options,
    candidates: [],
    approvers: [],
    groups: [],
    dependencies: [],
    availableFields: capability.fields,
    channels: options,
    statuses: options,
    currentStatus: String(initialValues?.status ?? ""),
    currentOwnerId: String(initialValues?.ownerId ?? ""),
    selectedCount: 1,
    targetName: targetId || capability.resourceName,
    sourceName: capability.resourceName,
    defaultName: capability.resourceName,
    expectedText: targetId || capability.resourceName,
    description: error ?? `${capability.resourceName} 작업을 실행합니다.`,
    impact: error ? [error] : [],
    actionLabel: capability.action ?? capability.resourceName,
    progress: null,
    request: initialValues,
    response: null,
    onConfirm: callback,
    onComplete: callback,
    onSubmit: callback,
    onSave: callback,
    onApply: callback,
    onAssign: callback,
    onChange: callback,
    onSchedule: callback,
    onContinue: callback,
    onDuplicate: callback,
    onExport: callback,
    onImport: callback,
    onCommit: callback,
    onDeploy: callback,
    onPublish: callback,
    onInvite: callback,
    // 파트가 선언하는 나머지 상호작용 핸들러 — 백에 없으면 버튼이 죽는다. 오버레이에서는 대상 작업을
    // 실행/제출하는 것이 기본 동작이다.
    onAction: callback,
    onSelect: callback,
    onNavigate: onClose,
    onPrimaryAction: callback,
    onCardClick: callback,
    onEventClick: callback,
    onAddCard: callback,
    onAcknowledge: callback,
    onOpen: callback,
    onPermissionChange: callback,
    onStepClick: () => undefined,
    onLabel: (value: unknown) => String(value ?? ""),
    onCopy: (value: unknown) => { void navigator.clipboard?.writeText(String(value ?? targetId ?? capability.resourceName)); },
    onValidate: async () => ({ valid: capability.fields.length, invalid: 0 }),
  };
  if (!open || !kind) return null;
  const rendered = renderBlueprintPart(componentId, props);
  if (partDescriptor(componentId)?.overlayPresentation === "SELF_HOSTED") return <>{rendered}</>;
  return (
    <div className="fixed inset-0 z-[120] grid place-items-center bg-black/70 p-4" role="dialog" aria-modal="true">
      <div className="max-h-[92vh] w-full max-w-5xl overflow-auto rounded-[18px] border border-line bg-panel p-5 shadow-2xl">
        <div className="mb-4 flex justify-end">
          <button type="button" className="text-sm font-bold text-muted-soft hover:text-foreground" onClick={onClose}>
            닫기
          </button>
        </div>
        {rendered}
      </div>
    </div>
  );
}

export function BlueprintFeedbackPart({
  componentId,
  details,
  onAction,
}: {
  componentId?: string;
  details?: string;
  onAction?: () => void;
}) {
  if (!componentId || componentId === "default-feedback") return null;
  return <>{renderBlueprintPart(componentId, { details, onAction })}</>;
}
