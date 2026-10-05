"use client";

import {
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import type {
  PreviewInputSchema,
  PreviewCompiledScenario,
  PreviewPagePlan,
  PreviewScenarioDiagnostic,
  PreviewCompiledScenarioStage,
  PreviewScenarioStageExecution,
} from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { BlueprintModalFrame } from "../blueprints/modals";
import {
  callCapability,
  extractArray,
  rowId,
  unwrapEnvelope,
} from "../api";
import type { PreviewCapability, PreviewRuntimeConfig } from "../types";
import {
  composeProductExperience,
  validateProductExperience,
  type ExperienceAction,
  type ExperienceOverlay,
  type ExperienceScreen,
} from "./productExperience";
import {
  buildScenarioExecutionPath,
  emptyExecution,
  missingRequiredStageInputs,
  preflightScenarioExecution,
  resolveSelectionOutputs,
  parseScenarioInput,
  runApiStage,
  type ScenarioState,
} from "./runtime";
import { SchemaField, ResourceDetails, resourceImage } from "./SchemaFields";
import { ProductExperienceInspector } from "./ProductExperienceInspector";
import {
  selectProductExperienceTheme,
  type ProductExperienceTheme,
} from "./productTheme";

type Row = Record<string, unknown>;

function executionTimestamp(): number {
  return Date.now();
}

function textValue(value: unknown): string {
  if (value === null || value === undefined) return "";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return "";
}

function titleOf(row: Row, index = 0): string {
  const keys = ["title", "name", "subject", "label", "summary", "email", "username"];
  for (const key of keys) {
    const value = textValue(row[key]);
    if (value) return value;
  }
  return `새로운 항목 ${index + 1}`;
}

function subtitleOf(row: Row): string {
  const keys = ["description", "content", "message", "category", "status", "type"];
  for (const key of keys) {
    const value = textValue(row[key]);
    if (value) return value;
  }
  return "자세한 내용을 확인하고 다음 작업을 이어갈 수 있습니다.";
}

function ProductActionButton({
  action,
  onClick,
}: {
  action: ExperienceAction;
  onClick: () => void;
}) {
  const style = action.tone === "PRIMARY"
    ? "border-[var(--px-accent)] bg-[var(--px-accent)] text-[var(--px-on-accent)] hover:border-[var(--px-accent-hover)] hover:bg-[var(--px-accent-hover)]"
    : action.tone === "DANGER"
      ? "border-[color-mix(in_srgb,var(--px-danger)_35%,transparent)] bg-[var(--px-danger-bg)] text-[var(--px-danger)] hover:border-[var(--px-danger)]"
      : "border-[var(--px-line)] bg-[var(--px-surface)] text-[var(--px-ink)] hover:bg-[var(--px-surface-soft)]";
  return (
    <button
      type="button"
      className={`inline-flex min-h-10 items-center gap-2 rounded-full border px-4 text-sm font-bold shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md ${style}`}
      onClick={onClick}
    >
      <span aria-hidden className="text-base leading-none">{action.icon}</span>
      {action.label}
    </button>
  );
}

function Card({
  row,
  index,
  selected,
  onSelect,
  visual = true,
}: {
  row: Row;
  index: number;
  selected: boolean;
  onSelect: () => void;
  visual?: boolean;
}) {
  const image = resourceImage(row);
  return (
    <button
      type="button"
      onClick={onSelect}
      className={`group overflow-hidden rounded-[14px] border bg-[var(--px-surface)] text-left shadow-[var(--px-shadow-sm)] transition-all duration-300 hover:-translate-y-1 hover:shadow-[var(--px-shadow-md)] ${
        selected ? "border-[var(--px-accent)] ring-2 ring-[color-mix(in_srgb,var(--px-accent)_14%,transparent)]" : "border-[var(--px-line)]"
      }`}
    >
      {visual && image && (
        // eslint-disable-next-line @next/next/no-img-element -- shared with the standalone Vite runtime
        <img src={image} alt="" loading="lazy" referrerPolicy="no-referrer" className="h-44 w-full object-cover" />
      )}
      <div className="p-4">
        <h3 className="line-clamp-1 text-[15px] font-black text-[var(--px-ink)]">{titleOf(row, index)}</h3>
        <p className="mt-1 line-clamp-2 min-h-10 text-xs leading-5 text-[var(--px-muted)]">{subtitleOf(row)}</p>
        <div className="mt-4 flex items-center justify-between gap-2">
          <span className="text-xs font-extrabold text-[var(--px-ink)]">
            {typeof row.price === "number" ? `${row.price.toLocaleString()} ${textValue(row.currency)}` : textValue(row.author) || textValue(row.updatedAt) || "자세히 보기"}
          </span>
          {Boolean(row.status) && <span className="rounded-full bg-[var(--px-tint)] px-2.5 py-1 text-[10px] font-bold text-[var(--px-accent)]">{textValue(row.status)}</span>}
        </div>
      </div>
    </button>
  );
}

function ScreenContent({
  screen,
  rows,
  selected,
  onSelect,
}: {
  screen: ExperienceScreen;
  rows: Row[];
  selected: Row | null;
  onSelect: (row: Row) => void;
}) {
  return (
    <div className={screen.kind === "FEED" ? "mx-auto grid max-w-3xl gap-4" : "grid gap-4 sm:grid-cols-2 lg:grid-cols-3"}>
      {rows.map((row, index) => (
        <Card
          key={rowId(row)}
          row={row}
          index={index}
          selected={selected ? rowId(selected) === rowId(row) : false}
          onSelect={() => onSelect(row)}
          visual={screen.kind !== "FEED"}
        />
      ))}
    </div>
  );
}

function DetailOverlay({
  row,
  open,
  loading,
  error,
  actions,
  theme,
  onClose,
  onAction,
}: {
  row: Row | null;
  open: boolean;
  loading: boolean;
  error: string | null;
  actions: ExperienceAction[];
  theme: ProductExperienceTheme;
  onClose: () => void;
  onAction: (action: ExperienceAction) => void;
}) {
  if (!row) return null;
  return (
    <BlueprintModalFrame
      open={open}
      onClose={onClose}
      title={titleOf(row)}
      description={subtitleOf(row)}
      eyebrow="상세"
      size="lg"
      style={theme.style}
      themeId={theme.blueprintThemeId}
    >
      {loading && <p role="status" className="mb-4 text-sm text-muted">상세 정보를 불러오는 중…</p>}
      {error && <p role="alert" className="mb-4 text-sm text-danger">{error}</p>}
      <ResourceDetails value={row} />
      {actions.length > 0 && (
        <div className="mt-5 flex flex-wrap gap-2 border-t border-line pt-5">
          {actions.map((action) => (
            <Button
              key={action.id}
              disabled={loading || Boolean(error)}
              variant={action.tone === "DANGER" ? "danger" : action.tone === "PRIMARY" ? "primary" : "secondary"}
              onClick={() => onAction(action)}
            >
              {action.label}
            </Button>
          ))}
        </div>
      )}
    </BlueprintModalFrame>
  );
}

function overlayFields(
  overlay: ExperienceOverlay,
  scenario: PreviewCompiledScenario,
  capabilities: PreviewCapability[]
): string[] {
  const stages = scenario.stages.filter((stage) => overlay.stageIds.includes(stage.id));
  const local = stages.flatMap((stage) => ["ENTRY", "PREPARE", "CONFIGURE", "SELECT_CONTEXT"].includes(stage.role)
    ? stage.outputs : stage.role === "AUTHENTICATE" ? capabilities.find(cap => cap.id === stage.capabilityId)?.fields ?? [] : []);
  return Array.from(new Set(local)).filter(field => !/^(collection|authenticatedCollection|selectedId|selectedRecord|selectedResource|verifiedResource|authToken|createdId|trackedStatus)$/i.test(field));
}

function inputContract(field: string, capabilities: PreviewCapability[]): { schema?: PreviewInputSchema; required: boolean } {
  const capability = capabilities.find(cap => cap.inputSchema?.properties[field]);
  return { schema: capability?.inputSchema?.properties[field], required: capability?.inputSchema ? capability.inputSchema.required.includes(field) : true };
}

function parseProductInput(value: string, schema?: PreviewInputSchema): unknown {
  if (schema?.type === "string") return value;
  return parseScenarioInput(value);
}

function ActionOverlay({ action, overlay, scenario, capabilities, draft, selected, state, busy, error,
  onDraft, onClose, onContinue, onExecute, onBack }: {
  action: ExperienceAction; overlay: ExperienceOverlay; scenario: PreviewCompiledScenario;
  capabilities: PreviewCapability[]; draft: Record<string, string>; selected: Row | null; state: ScenarioState;
  busy: boolean; error: string | null; theme: ProductExperienceTheme;
  onDraft: (field: string, value: string) => void; onClose: () => void; onContinue: () => void; onExecute: () => void; onBack?: () => void;
}) {
  const related = capabilities.filter(cap => scenario.stages.some(stage => stage.capabilityId === cap.id));
  const boundIds = new Set(scenario.stages.filter(stage => stage.role === "SELECT").flatMap(stage => stage.outputs));
  const fields = overlayFields(overlay, scenario, related).filter(field =>
    (!boundIds.has(field) || state[field] === undefined) && !(field.endsWith("Id") && state[field] !== undefined && state[field] === state.selectedId));
  const result = overlay.kind === "RESULT_TOAST";
  const review = overlay.kind === "REVIEW_MODAL" || overlay.kind === "DANGER_CONFIRM";
  const detail = overlay.kind === "DETAIL_DRAWER";
  const formId = `experience-form-${overlay.id}`;
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => { headingRef.current?.focus(); }, [overlay.id]);
  return <section className="min-w-0" aria-label={overlay.title}>
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
      <div><h1 ref={headingRef} tabIndex={-1} className="text-2xl font-bold outline-none">{result ? "완료되었습니다" : overlay.title}</h1><p className="mt-1 text-sm text-[var(--px-muted)]">{selected ? titleOf(selected) : action.label}</p></div>
      <Button type="button" disabled={busy} onClick={onClose}>{result ? "목록으로" : "목록으로 돌아가기"}</Button>
    </div>
    {error && <div role="alert" className="mb-5 border-l-2 border-danger bg-danger/10 p-4 text-sm text-danger">{error}</div>}
    {overlay.kind === "DANGER_CONFIRM" && <p className="mb-4 text-sm text-danger">이 작업은 데이터를 삭제하거나 중요한 상태를 변경합니다. 대상을 확인해주세요.</p>}
    <div className="grid min-w-0 gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(240px,.5fr)]">
      <div className="min-w-0">
        {result ? <div role="status"><ResourceDetails value={state.verifiedResource ?? state.lastResponse ?? state.selectedResource} /></div>
          : review ? <><ResourceDetails value={state.lastResponse ?? selected} /><div className="mt-5 border-t border-[var(--px-line)] pt-4"><ResourceDetails value={Object.fromEntries(Object.entries(draft).map(([key, value]) => [key, parseProductInput(value, inputContract(key, related).schema)]))} /></div></>
          : <form id={formId} onSubmit={event => { event.preventDefault(); onContinue(); }} className="min-w-0 space-y-4">
            {detail && selected && <div className="mb-6"><ResourceDetails value={state.selectedResource ?? selected} /></div>}
            {fields.map(field => { const contract = inputContract(field, related); const raw = draft[field];
              let value: unknown = raw ?? state[field];
              if (raw !== undefined && contract.schema?.type !== "string") value = parseScenarioInput(raw);
              return <SchemaField key={field} name={field} schema={contract.schema} required={contract.required} value={value}
                onChange={next => onDraft(field, typeof next === "object" ? JSON.stringify(next) : String(next))} />;
            })}
          </form>}
      </div>
      <aside className="min-w-0 self-start border-t border-[var(--px-line)] pt-4 lg:border-l lg:border-t-0 lg:pl-6 lg:pt-0">
        <h2 className="text-sm font-semibold">{result ? "저장된 결과" : review ? "내용 확인" : "선택한 항목"}</h2>
        <p className="mt-2 text-sm leading-6 text-[var(--px-muted)]">{selected ? titleOf(selected) : action.label}</p>
        {onBack && !busy && !error && <Button type="button" size="small" className="mt-4" onClick={onBack}>입력 수정</Button>}
        {!result && <div className="mt-6"><Button type={review ? "button" : "submit"} form={review ? undefined : formId}
          variant={overlay.kind === "DANGER_CONFIRM" ? "danger-solid" : "primary"} disabled={busy}
          onClick={review ? onExecute : undefined}>{busy ? "처리 중…" : error && review ? "다시 시도" : overlay.submitLabel}</Button></div>}
      </aside>
    </div>
  </section>;
}

export function ProductExperienceRuntime({
  scenarios,
  capabilities,
  config,
  pagePlans = [],
}: {
  pagePlans?: PreviewPagePlan[];
  diagnostics?: PreviewScenarioDiagnostic[];
  scenarios: PreviewCompiledScenario[];
  capabilities: PreviewCapability[];
  config: PreviewRuntimeConfig;
}) {
  const graph = useMemo(
    () => composeProductExperience(scenarios, capabilities, pagePlans),
    [scenarios, capabilities, pagePlans]
  );
  const theme = useMemo(
    () => selectProductExperienceTheme(graph.archetype, scenarios, capabilities),
    [graph.archetype, scenarios, capabilities]
  );
  const validationErrors = useMemo(
    () => validateProductExperience(graph, scenarios),
    [graph, scenarios]
  );
  const [activeScreenId, setActiveScreenId] = useState(() => {
    if (typeof window === "undefined") return graph.defaultScreenId;
    const requested = new URLSearchParams(window.location.search).get("experience");
    return graph.screens.some((screen) => screen.id === requested) ? requested! : graph.defaultScreenId;
  });
  const [activeCollectionId, setActiveCollectionId] = useState<string | null>(null);
  const [executedScenarioId, setExecutedScenarioId] = useState<string | null>(null);
  const [rowsByCapability, setRowsByCapability] = useState<Record<string, Row[]>>({});
  const [collectionErrors, setCollectionErrors] = useState<Record<string, string>>({});
  const [loadingCollections, setLoadingCollections] = useState(false);
  const [selected, setSelected] = useState<Row | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const detailAbortRef = useRef<AbortController | null>(null);
  const [activeActionId, setActiveActionId] = useState<string | null>(null);
  const [lastActionId, setLastActionId] = useState<string | null>(null);
  const [overlayIndex, setOverlayIndex] = useState(0);
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [scenarioState, setScenarioState] = useState<ScenarioState>({});
  const stateRef = useRef<ScenarioState>({});
  const [executions, setExecutions] = useState<Record<string, PreviewScenarioStageExecution>>({});
  const [executionTimeline, setExecutionTimeline] = useState<PreviewScenarioStageExecution[]>([]);
  const [currentStageId, setCurrentStageId] = useState<string | null>(null);
  const [rawBodyDrafts, setRawBodyDrafts] = useState<Record<string, string>>({});
  const [inspectorOpen, setInspectorOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [searchOpen, setSearchOpen] = useState(false);
  const [screenQuery, setScreenQuery] = useState("");
  const abortRef = useRef<AbortController | null>(null);
  const failedStepRef = useRef<{ scenarioId: string; stageId: string } | null>(null);

  const listCapabilities = useMemo(
    () => capabilities.filter((capability) => capability.type === "LIST" && capability.risk === "SAFE"),
    [capabilities]
  );

  async function loadCollections(signal?: AbortSignal) {
    if (!config.apiBaseUrl.trim() || listCapabilities.length === 0) return;
    setLoadingCollections(true);
    try {
      const settled = await Promise.allSettled(
        listCapabilities.map(async (capability) => {
          const response = await callCapability(config, capability, { signal });
          return [capability.id, extractArray(response, capability.collectionPath)] as const;
        })
      );
      const next = Object.fromEntries(
        settled
          .filter((result): result is PromiseFulfilledResult<readonly [string, Row[]]> => result.status === "fulfilled")
          .map((result) => result.value)
      );
      const errors = Object.fromEntries(
        settled.flatMap((result, index) => result.status === "rejected"
          ? [[listCapabilities[index].id, result.reason instanceof Error ? result.reason.message : String(result.reason)]]
          : [])
      );
      if (!signal?.aborted) {
        setRowsByCapability(next);
        setCollectionErrors(errors);
      }
    } finally {
      if (!signal?.aborted) setLoadingCollections(false);
    }
  }

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => void loadCollections(controller.signal), 0);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
    // URL이나 목록 capability 구성이 달라졌을 때만 초기 데이터를 다시 읽는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [config.apiBaseUrl, config.authToken, listCapabilities]);

  useEffect(() => () => { abortRef.current?.abort(); detailAbortRef.current?.abort(); }, []);

  useEffect(() => {
    const syncScreenFromHistory = () => {
      const requested = new URLSearchParams(window.location.search).get("experience");
      if (requested && graph.screens.some((screen) => screen.id === requested)) {
        abortRef.current?.abort(); detailAbortRef.current?.abort();
        setBusy(false); setActiveActionId(null); setDetailOpen(false); setSelected(null);
        setDraft({}); setActionError(null); setActiveCollectionId(null);
        const next = { ...stateRef.current }; delete next.selectedId; delete next.selectedRecord;
        stateRef.current = next; setScenarioState(next);
        setActiveScreenId(requested);
      }
    };
    window.addEventListener("popstate", syncScreenFromHistory);
    return () => window.removeEventListener("popstate", syncScreenFromHistory);
  }, [graph.screens]);

  const activeScreen = graph.screens.find((screen) => screen.id === activeScreenId)
    ?? graph.screens[0];
  const screenLists = (activeScreen?.capabilityIds ?? []).flatMap((id) => listCapabilities.filter((capability) => capability.id === id));
  const activeCollection = screenLists.find((capability) => capability.id === activeCollectionId) ?? screenLists[0];
  const liveRows = activeCollection ? rowsByCapability[activeCollection.id] ?? [] : [];
  const screenErrors = screenLists.flatMap((capability) => collectionErrors[capability.id] ? [collectionErrors[capability.id]] : []);
  const visibleRows = liveRows;
  const normalizedQuery = screenQuery.trim().toLowerCase();
  const rows = normalizedQuery
    ? visibleRows.filter((row, index) => `${titleOf(row, index)} ${subtitleOf(row)}`.toLowerCase().includes(normalizedQuery))
    : visibleRows;
  const screenActions = graph.actions.filter((action) => action.screenId === activeScreen?.id);
  const needsSelection = (action: ExperienceAction) => scenarios.find(scenario => scenario.id === action.scenarioId)?.stages.some(stage => stage.role === "SELECT" && stage.inputs.some(input => input === "collection" || input === "authenticatedCollection"));
  const primaryActions = screenActions.filter(action => !needsSelection(action));
  const contextualActions = screenActions.filter(needsSelection);
  const activeAction = graph.actions.find((action) => action.id === activeActionId) ?? null;
  const activeScenario = scenarios.find((scenario) => scenario.id === activeAction?.scenarioId) ?? null;
  const inspectedAction = activeAction
    ?? graph.actions.find((action) => action.id === lastActionId && action.screenId === activeScreen?.id)
    ?? screenActions[0]
    ?? null;
  const inspectedScenario = scenarios.find((scenario) => scenario.id === inspectedAction?.scenarioId) ?? null;
  const actionOverlays = activeAction
    ? activeAction.overlayIds
      .map((id) => graph.overlays.find((overlay) => overlay.id === id))
      .filter((overlay): overlay is ExperienceOverlay => Boolean(overlay))
    : [];
  const activeOverlay = actionOverlays[overlayIndex] ?? null;

  function navigateScreen(screenId: string) {
    if (busy || screenId === activeScreenId) return;
    closeAction();
    detailAbortRef.current?.abort();
    const query = new URLSearchParams(window.location.search);
    query.set("experience", screenId);
    window.history.pushState(null, "", `${window.location.pathname}?${query.toString()}`);
    setActiveScreenId(screenId);
    setSelected(null);
    setActiveCollectionId(null);
    const nextState = { ...stateRef.current };
    delete nextState.selectedId;
    delete nextState.selectedRecord;
    stateRef.current = nextState;
    setScenarioState(nextState);
    setScreenQuery("");
    setSearchOpen(false);
    setDetailOpen(false);
  }

  async function selectRow(row: Row, open = true) {
    detailAbortRef.current?.abort();
    setSelected(row);
    setDetailError(null);
    const next = { ...stateRef.current, selectedId: rowId(row), selectedRecord: row };
    stateRef.current = next;
    setScenarioState(next);
    if (open && activeScreen.kind !== "INBOX") setDetailOpen(true);
    const detail = capabilities.find(cap => cap.type === "DETAIL" && cap.risk === "SAFE" && cap.resourceName === activeCollection?.resourceName);
    const parameters = [...(detail?.path ?? "").matchAll(/\{([^}]+)\}/g)].map(match => match[1]);
    if (!detail || parameters.length !== 1) { setLoadingDetail(false); return; }
    const controller = new AbortController(); detailAbortRef.current = controller;
    setLoadingDetail(true);
    try {
      const response = await callCapability(config, detail, { pathParams: { [parameters[0]]: rowId(row) }, signal: controller.signal });
      if (controller.signal.aborted) return;
      const record = unwrapEnvelope(response);
      if (!record || typeof record !== "object" || Array.isArray(record)) throw new Error("상세 응답에서 항목을 확인할 수 없습니다. Inspector에서 응답 구조를 확인해주세요.");
      const resolved = { ...row, ...record };
      setSelected(resolved);
      const current = { ...stateRef.current, selectedRecord: resolved, selectedResource: record };
      stateRef.current = current; setScenarioState(current);
    } catch (cause) {
      if (!controller.signal.aborted) setDetailError(cause instanceof Error ? cause.message : "상세 정보를 불러오지 못했습니다.");
    } finally {
      if (detailAbortRef.current === controller) { detailAbortRef.current = null; setLoadingDetail(false); }
    }
  }

  function openAction(action: ExperienceAction) {
    if (busy) return;
    failedStepRef.current = null;
    const scenario = scenarios.find((candidate) => candidate.id === action.scenarioId);
    const allowedState = new Set([...(scenario?.scenarioState ?? []), "authToken"]);
    const produced = new Set((scenario?.stages ?? []).filter(stage => stage.capabilityId || stage.role === "SELECT").flatMap(stage => [...stage.outputs, ...stage.outputBindings.map(binding => binding.to)]));
    const scopedState = Object.fromEntries(
      Object.entries(stateRef.current).filter(([key]) => allowedState.has(key) && (key === "authToken" || !produced.has(key)))
    );
    const actionSelection = needsSelection(action) ? selected : null;
    if (actionSelection) {
      scopedState.selectedId = rowId(actionSelection);
      scopedState.selectedRecord = actionSelection;
    } else {
      delete scopedState.selectedId;
      delete scopedState.selectedRecord;
      delete scopedState.selectedResource;
    }
    setSelected(actionSelection);
    setDetailOpen(false);
    setActiveActionId(action.id);
    setLastActionId(action.id);
    setOverlayIndex(0);
    const generated: Record<string, string> = {};
    for (const stage of scenario?.stages ?? []) for (const key of stage.outputs) {
      if (["PREPARE", "CONFIGURE", "SELECT_CONTEXT"].includes(stage.role) && activeCollection && actionSelection
          && key.toLowerCase() === `${activeCollection.resourceName.replace(/s$/, "")}id`.toLowerCase()
          && stage.inputs.includes("selectedId")) scopedState[key] = rowId(actionSelection);
      const contract = inputContract(key, capabilities.filter(cap => scenario?.stages.some(stage => stage.capabilityId === cap.id)));
      if (contract.schema?.type === "array" && !contract.required && !scopedState[key]) generated[key] = "[]";
    }
    stateRef.current = scopedState; setScenarioState(scopedState);
    setDraft(generated);
    setActionError(null);
  }

  function closeAction() {
    abortRef.current?.abort();
    setActiveActionId(null);
    setOverlayIndex(0);
    setDraft({});
    setActionError(null);
    setBusy(false);
  }

  function recordExecution(execution: PreviewScenarioStageExecution) {
    setExecutions((current) => ({ ...current, [execution.stageId]: execution }));
    if (execution.status !== "IDLE") {
      setExecutionTimeline((current) => [...current, execution].slice(-80));
    }
  }

  function saveLocalStages(stages: PreviewCompiledScenarioStage[]) {
    const parsed = Object.fromEntries(Object.entries(draft).map(([key, value]) => [key, parseProductInput(value, inputContract(key, capabilities.filter(cap => activeScenario?.stages.some(stage => stage.capabilityId === cap.id))).schema)]));
    const next = { ...stateRef.current, ...parsed };
    for (const stage of stages) {
      for (const output of stage.outputs) {
        if (next[output] === undefined && parsed[output] !== undefined) next[output] = parsed[output];
      }
    }
    stateRef.current = next;
    setScenarioState(next);
  }

  function advanceOverlay() {
    if (!activeOverlay || !activeScenario || busy) return;
    const localStages = activeScenario.stages.filter(stage => activeOverlay.stageIds.includes(stage.id));
    saveLocalStages(localStages);
    void executeAction();
  }

  async function executeAction(startStageId?: string) {
    const executionAction = activeAction ?? inspectedAction;
    const executionScenario = activeScenario ?? inspectedScenario;
    if (!executionAction || !executionScenario || busy || abortRef.current) return;
    if (executedScenarioId !== executionScenario.id) { setExecutions({}); setExecutionTimeline([]); }
    setExecutedScenarioId(executionScenario.id);
    const resumeStageId = startStageId ?? (failedStepRef.current?.scenarioId === executionScenario.id
      ? failedStepRef.current.stageId : undefined);
    const executionPath = buildScenarioExecutionPath(executionScenario, resumeStageId);
    if (activeOverlay) executionPath.stages = executionPath.stages.filter(stage => activeOverlay.stageIds.includes(stage.id));
    if (executionPath.error) {
      setActionError(executionPath.error);
      return;
    }
    setBusy(true);
    setActionError(null);
    const controller = new AbortController();
    abortRef.current = controller;
    let nextState = { ...stateRef.current };
    if (selected) {
      nextState.selectedId = rowId(selected);
      nextState.selectedRecord = selected;
    }
    nextState = {
      ...nextState,
      ...Object.fromEntries(Object.entries(draft).map(([key, value]) => [key, parseProductInput(value, inputContract(key, capabilities.filter(cap => executionScenario.stages.some(stage => stage.capabilityId === cap.id))).schema)])),
    };
    const relatedCapabilities = capabilities.filter(cap => executionScenario.stages.some(stage => stage.capabilityId === cap.id));
    const optionalInputs = new Set(relatedCapabilities.flatMap(cap => cap.inputSchema ? Object.keys(cap.inputSchema.properties).filter(key =>
      !cap.inputSchema!.required.includes(key) && !executionScenario.stages.some(stage => stage.inputBindings.some(binding => binding.required && binding.source === `$scenario.${key}`))) : []));
    const preflightErrors = preflightScenarioExecution(executionPath.stages, nextState, optionalInputs);
    if (preflightErrors.length > 0) {
      setActionError(`실행 전 검증 실패: ${preflightErrors.join(" ")}`);
      if (abortRef.current === controller) {
        setBusy(false);
        abortRef.current = null;
      }
      return;
    }
    let failedStage: PreviewCompiledScenarioStage | null = null;
    let failureRecorded = false;
    try {
      for (const stage of executionPath.stages) {
        setCurrentStageId(stage.id);
        failedStage = stage;
        failureRecorded = false;
        if (controller.signal.aborted) throw new Error("작업을 취소했습니다.");
        if (stage.role === "PREPARE" || stage.role === "CONFIGURE" || stage.role === "SELECT_CONTEXT" || stage.role === "REVIEW") {
          recordExecution({ ...emptyExecution(stage), status: "SUCCESS", durationMs: 0, completedAt: executionTimestamp() });
          continue;
        }
        if (stage.role === "SELECT") {
          const outputs = resolveSelectionOutputs(stage, nextState);
          nextState = { ...nextState, ...outputs };
          stateRef.current = nextState;
          setScenarioState({ ...nextState });
          recordExecution({
            ...emptyExecution(stage), status: "SUCCESS", extractedOutputs: outputs,
            durationMs: 0, completedAt: executionTimestamp(),
          });
          continue;
        }
        if (stage.role === "COMPLETE" || !stage.capabilityId) {
          recordExecution({ ...emptyExecution(stage), status: "SUCCESS", durationMs: 0, completedAt: executionTimestamp() });
          continue;
        }
        const capability = capabilities.find((candidate) => candidate.id === stage.capabilityId);
        if (!capability) throw new Error("연결된 API 작업을 찾지 못했습니다.");
        const missingInputs = missingRequiredStageInputs(stage, nextState);
        if (missingInputs.length > 0) {
          throw new Error(`필수 입력 연결이 준비되지 않았습니다: ${missingInputs.join(", ")}`);
        }
        let requestOverride;
        const rawBody = rawBodyDrafts[stage.id];
        if (rawBody?.trim()) {
          const parsed = JSON.parse(rawBody);
          if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
            throw new Error("원시 요청 본문은 JSON 객체여야 합니다.");
          }
          requestOverride = { body: parsed as Record<string, unknown> };
        }
        recordExecution({ ...emptyExecution(stage), status: "RUNNING", method: capability.method, startedAt: executionTimestamp() });
        const stageConfig: PreviewRuntimeConfig = {
          ...config,
          authToken: typeof nextState.authToken === "string" ? nextState.authToken : config.authToken,
        };
        let result = await runApiStage({
          stage,
          capability,
          state: nextState,
          config: stageConfig,
          signal: controller.signal,
          requestOverride,
        });
        recordExecution(result.execution);
        failureRecorded = result.execution.status !== "SUCCESS";
        if (stage.role === "TRACK" && result.execution.status === "FAILED") {
          for (let attempt = 0; attempt < 3 && result.execution.status === "FAILED"; attempt += 1) {
            await new Promise<void>((resolve) => window.setTimeout(resolve, 1500));
            recordExecution({ ...emptyExecution(stage), status: "RUNNING", method: capability.method, startedAt: executionTimestamp() });
            result = await runApiStage({
              stage,
              capability,
              state: nextState,
              config: stageConfig,
              signal: controller.signal,
              requestOverride,
            });
            recordExecution(result.execution);
            failureRecorded = result.execution.status !== "SUCCESS";
          }
        }
        if (result.execution.status !== "SUCCESS") {
          throw new Error(result.execution.error ?? `${stage.intent} 작업에 실패했습니다.`);
        }
        nextState = { ...result.nextState, lastResponse: result.execution.response };
        stateRef.current = nextState;
        setScenarioState({ ...nextState });
        const collection = extractArray(result.execution.response, capability.collectionPath);
        if (capability.type === "LIST") {
          setRowsByCapability((current) => ({ ...current, [capability.id]: collection }));
        }
        if (stage.role === "AUTHENTICATE" && typeof nextState.authToken === "string") {
          config.onAuthTokenChange(nextState.authToken);
        }
      }
      stateRef.current = nextState;
      setScenarioState(nextState);
      failedStepRef.current = null;
      await loadCollections(controller.signal);
      const executionOverlays = executionAction.overlayIds
        .map((id) => graph.overlays.find((overlay) => overlay.id === id))
        .filter((overlay): overlay is ExperienceOverlay => Boolean(overlay));
      const resultIndex = executionOverlays.findIndex((overlay) => overlay.kind === "RESULT_TOAST");
      if (activeAction && activeOverlay && overlayIndex < executionOverlays.length - 1) setOverlayIndex(overlayIndex + 1);
      else if (activeAction && resultIndex >= 0) setOverlayIndex(resultIndex);
      else {
        if (activeAction) closeAction();
        setToast(`${executionAction.label} 작업을 완료했습니다.`);
      }
    } catch (cause) {
      if (!controller.signal.aborted) {
        const detail = cause instanceof Error ? cause.message : "작업을 완료하지 못했습니다.";
        if (failedStage) failedStepRef.current = { scenarioId: executionScenario.id, stageId: failedStage.id };
        const message = failedStage ? `${failedStage.intent}: ${detail}` : detail;
        setActionError(message);
        if (failedStage && !failureRecorded) {
          recordExecution({
            ...emptyExecution(failedStage),
            status: "FAILED",
            error: message,
            completedAt: executionTimestamp(),
          });
        }

      }
    } finally {
      if (abortRef.current === controller) {
        setBusy(false);
        abortRef.current = null;
      }
    }
  }

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 3600);
    return () => window.clearTimeout(timer);
  }, [toast]);

  if (!activeScreen) return null;

  return (
    <section
      className="relative overflow-hidden rounded-[14px] border border-[var(--px-line)] bg-[var(--px-bg)] text-[var(--px-ink)] "
      style={theme.style}
      data-product-theme={theme.id}
      data-blueprint-theme={theme.blueprintThemeId}
    >
      <header className="sticky top-0 z-20 border-b border-[var(--px-line)] bg-[color-mix(in_srgb,var(--px-surface)_95%,transparent)] px-5 backdrop-blur-xl md:px-8">
        <div className="mx-auto flex min-h-[64px] max-w-[1380px] flex-wrap items-center gap-2 py-3 sm:gap-4">
          <button
            type="button"
            className="flex shrink-0 items-center gap-3"
            onClick={() => navigateScreen(graph.defaultScreenId)}
          >
            <span className="grid h-9 w-9 place-items-center rounded-[12px] bg-[var(--px-accent)] text-sm font-black text-[var(--px-on-accent)]">
              {graph.productName.slice(0, 1)}
            </span>
            <strong className="hidden text-base font-black tracking-[-.02em] text-[var(--px-ink)] sm:block">{graph.productName}</strong>
          </button>
          <nav className="order-3 flex w-full min-w-0 items-center gap-1 overflow-x-auto py-2 sm:order-none sm:w-auto sm:flex-1" aria-label="서비스 메뉴">
            {graph.screens.map((screen) => (
              <button
                type="button"
                key={screen.id}
                disabled={busy}
                onClick={() => navigateScreen(screen.id)}
                className={`shrink-0 rounded-full px-3.5 py-2 text-xs font-bold transition-colors ${
                  activeScreen.id === screen.id ? "bg-[var(--px-tint)] text-[var(--px-accent)]" : "text-[var(--px-muted)] hover:bg-[var(--px-surface-soft)] hover:text-[var(--px-ink)]"
                }`}
              >
                {screen.label}
              </button>
            ))}
          </nav>
          <div className="ml-auto flex shrink-0 items-center gap-2">
            <button
              type="button"
              onClick={() => setInspectorOpen(true)}
              className="inline-flex rounded-full border border-[var(--px-line)] px-3 py-2 text-[10px] font-bold text-[var(--px-muted)]"
            >
              Inspector
            </button>
            <button type="button" aria-label="화면 검색" aria-pressed={searchOpen} onClick={() => setSearchOpen((value) => !value)} className="grid h-9 w-9 place-items-center rounded-full border border-[var(--px-line)] bg-[var(--px-surface)] text-xs">⌕</button>

          </div>
        </div>
        {searchOpen && (
          <div className="mx-auto max-w-[1380px] pb-4">
            <Input autoFocus value={screenQuery} onChange={(event) => setScreenQuery(event.target.value)} className="border-[var(--px-line)] bg-[var(--px-surface)] text-[var(--px-ink)]" placeholder={`${activeScreen.label}에서 검색`} />
          </div>
        )}
      </header>

      <main className="mx-auto min-h-[440px] max-w-[1380px] px-5 py-8 md:px-8 md:py-8">
      {activeAction && activeScenario && activeOverlay && (
        <ActionOverlay
          action={activeAction}
          overlay={activeOverlay}
          scenario={activeScenario}
          capabilities={capabilities}
          draft={draft}
          selected={selected}
          state={scenarioState}
          busy={busy}
          error={actionError}
          theme={theme}
          onDraft={(field, value) => setDraft((current) => ({ ...current, [field]: value }))}
          onClose={closeAction}
          onContinue={advanceOverlay}
          onExecute={() => void executeAction()}
          onBack={overlayIndex > 0 && ["REVIEW_MODAL", "DANGER_CONFIRM"].includes(activeOverlay.kind)
            && ["FORM_MODAL", "DETAIL_DRAWER"].includes(actionOverlays[overlayIndex - 1]?.kind)
            && !activeOverlay.stageIds.some(id => executions[id]?.status === "SUCCESS" && activeScenario.stages.find(stage => stage.id === id)?.role === "COMMIT")
            ? () => { failedStepRef.current = null; setActionError(null); setOverlayIndex(overlayIndex - 1); } : undefined}
        />
      )}

        {!activeAction && <>
        <div className="mb-8 flex flex-wrap items-end justify-between gap-5">
          <div>
            <p className="text-xs font-bold text-[var(--px-muted)]">{activeScreen.label}</p>
            <h1 className="mt-2 text-2xl font-bold tracking-[-.02em] text-[var(--px-ink)] md:text-4xl">{activeScreen.title}</h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-[var(--px-muted)]">{activeScreen.description}</p>
          </div>
          <div className="flex max-w-3xl flex-wrap justify-end gap-2">
            {primaryActions.map((action) => (
              <ProductActionButton key={action.id} action={action} onClick={() => openAction(action)} />
            ))}
            <button
              type="button"
              disabled={loadingCollections}
              onClick={() => void loadCollections()}
              className="grid h-10 w-10 place-items-center rounded-full border border-[var(--px-line)] bg-[var(--px-surface)] text-sm text-[var(--px-muted)] shadow-sm disabled:opacity-50"
              aria-label="새로고침"
            >
              {loadingCollections ? "…" : "↻"}
            </button>
          </div>
        </div>

        {validationErrors.length > 0 && (
          <div className="mb-5 rounded-[16px] border border-[color-mix(in_srgb,var(--px-danger)_35%,transparent)] bg-[var(--px-danger-bg)] p-4 text-xs font-bold text-[var(--px-danger)]">
            화면 구성 검증 실패: {validationErrors.join("; ")}
          </div>
        )}

        {screenErrors.length > 0 && (
          <div className="mb-5 rounded-[16px] border border-amber-400/35 bg-amber-400/10 p-4 text-xs leading-5 text-amber-700 dark:text-amber-200">
            <strong className="block font-black">서버 목록을 불러오지 못했습니다</strong>
            인증·주소·CORS와 요청·응답을 확인한 뒤 새로고침하세요. 실제 서버 응답만 표시합니다.
            <span className="mt-1 block break-words opacity-80">{screenErrors.join(" · ")}</span>
          </div>
        )}

        {normalizedQuery && rows.length === 0 && (
          <div className="mb-5 rounded-[16px] border border-[var(--px-line)] bg-[var(--px-surface)] p-5 text-sm text-[var(--px-muted)]">
            “{screenQuery}”와 일치하는 항목이 없습니다.
          </div>
        )}

        <div className="min-w-0">
        <div className="min-w-0">
        {screenLists.length > 1 && <div className="mb-4 flex flex-wrap gap-2" role="group" aria-label="조회할 데이터">
          {screenLists.map((capability) => <button type="button" key={capability.id}
            aria-pressed={activeCollection?.id === capability.id} disabled={busy}
            className="rounded-lg border border-[var(--px-line)] px-3 py-2 text-xs aria-pressed:bg-[var(--px-tint)]"
            onClick={() => { setActiveCollectionId(capability.id); setSelected(null); setDetailOpen(false);
              const next = { ...stateRef.current }; delete next.selectedId; delete next.selectedRecord;
              stateRef.current = next; setScenarioState(next);
            }}>{capability.resourceName} · {capability.operationId || capability.id}</button>)}
        </div>}
        {rows.length > 0 ? (
          <ScreenContent
            screen={activeScreen}
            rows={rows}
            selected={selected}
            onSelect={selectRow}
          />
        ) : (
          <div className="grid min-h-72 place-items-center rounded-[24px] border border-dashed border-[var(--px-line)] bg-[var(--px-surface)] text-sm text-[var(--px-muted)]">
            <div className="max-w-md p-5 text-center" role="status">
              <strong className="block">{loadingCollections ? "실제 서버 데이터를 불러오는 중" : screenErrors.length > 0 ? "목록 요청 실패" : screenLists.length === 0 ? "표시할 목록이 없습니다" : "서버가 빈 목록을 반환했습니다"}</strong>
              <p className="mt-2 text-xs leading-5">{screenLists.length === 0 ? "위 행동을 선택해 내용을 입력하세요." : "테스트 데이터를 생성하거나 입력·인증을 확인한 뒤 다시 조회하세요."}</p>
            </div>
          </div>
        )}
        </div>
        </div>
        </>}
      </main>

      <footer className="border-t border-[var(--px-line)] bg-[color-mix(in_srgb,var(--px-surface)_62%,transparent)] px-8 py-5">
        <div className="mx-auto flex max-w-[1380px] flex-wrap items-center justify-between gap-3 text-[10px] font-bold text-[var(--px-subtle)]">
          <span>프리뷰 · 실행한 변경은 연결된 백엔드에 반영됩니다.</span>
          <span>서버 기능과 사용자 흐름을 확인하는 테스트 화면</span>
        </div>
      </footer>

      <DetailOverlay
        row={selected}
        open={detailOpen}
        loading={loadingDetail}
        error={detailError}
        actions={contextualActions.filter(action => scenarios.find(scenario => scenario.id === action.scenarioId)?.stages.some(stage => stage.role === "COMMIT" || stage.role === "AUTHENTICATE"))}
        theme={theme}
        onClose={() => { detailAbortRef.current?.abort(); setDetailOpen(false); }}
        onAction={openAction}
      />


      <ProductExperienceInspector
        open={inspectorOpen}
        screen={activeScreen}
        action={inspectedAction}
        scenario={inspectedScenario}
        scenarioState={scenarioState}
        executions={executedScenarioId === inspectedScenario?.id ? executions : {}}
        timeline={executedScenarioId === inspectedScenario?.id ? executionTimeline : []}
        currentStageId={executedScenarioId === inspectedScenario?.id ? currentStageId : null}
        running={busy}
        selectedRecord={selected}
        rawBodyDrafts={rawBodyDrafts}
        theme={theme}
        onRawBodyChange={(stageId, value) => setRawBodyDrafts((current) => ({ ...current, [stageId]: value }))}
        onRetry={(stageId) => void executeAction(stageId)}
        onCancel={() => abortRef.current?.abort()}
        onClose={() => setInspectorOpen(false)}
      />

      {toast && (
        <div className="fixed bottom-6 left-1/2 z-[220] flex -translate-x-1/2 items-center gap-3 rounded-full border border-[var(--px-line-strong)] bg-[var(--px-hero)] px-5 py-3 text-sm font-bold text-[var(--px-hero-ink)] shadow-2xl">
          <span className="grid h-6 w-6 place-items-center rounded-full bg-[var(--px-success)] text-xs text-[var(--px-on-accent)]">✓</span>
          {toast}
        </div>
      )}

      <span className="sr-only">{Object.keys(scenarioState).length}개의 사용자 흐름 상태가 유지되고 있습니다.</span>
    </section>
  );
}
