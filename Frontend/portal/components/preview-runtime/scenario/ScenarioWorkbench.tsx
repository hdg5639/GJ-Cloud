"use client";

import { useMemo, useRef, useState } from "react";
import type {
  PreviewCompiledScenario,
  PreviewCompiledScenarioStage,
  PreviewScenarioStageExecution,
} from "@/lib/types";
import type { PreviewCapability, PreviewRuntimeConfig } from "../types";
import { extractArray, rowId } from "../api";
import { buildScenarioRequest, emptyExecution, runApiStage, type ScenarioRequest, type ScenarioState } from "./runtime";

const ROLE_LABEL: Record<string, string> = {
  AUTHENTICATE: "인증",
  DISCOVER: "탐색",
  SELECT: "선택",
  INSPECT: "조회",
  PREPARE: "입력",
  REVIEW: "검토",
  COMMIT: "실행",
  TRACK: "추적",
  VERIFY: "검증",
  COMPLETE: "완료",
};

const STATUS_STYLE: Record<string, string> = {
  IDLE: "border-line text-muted",
  WAITING_INPUT: "border-[#e8b657]/40 bg-[#e8b657]/10 text-[#e8b657]",
  RUNNING: "border-brand/40 bg-brand/10 text-brand-strong",
  SUCCESS: "border-success/40 bg-success/10 text-success",
  FAILED: "border-danger/40 bg-danger/10 text-danger",
  SKIPPED: "border-line bg-white/[0.03] text-muted-soft",
  CANCELLED: "border-line bg-white/[0.03] text-muted",
};

function displayValue(value: unknown): string {
  if (value === null || value === undefined) return "";
  if (typeof value === "string") return value;
  return JSON.stringify(value);
}

function parseInput(value: string): unknown {
  const trimmed = value.trim();
  if (!trimmed) return "";
  if (trimmed === "true") return true;
  if (trimmed === "false") return false;
  if (/^-?\d+(\.\d+)?$/.test(trimmed)) return Number(trimmed);
  if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
    try {
      return JSON.parse(trimmed);
    } catch {
      return value;
    }
  }
  return value;
}

function executionLabel(status: string): string {
  return {
    IDLE: "대기",
    WAITING_INPUT: "입력 필요",
    RUNNING: "실행 중",
    SUCCESS: "성공",
    FAILED: "실패",
    SKIPPED: "건너뜀",
    CANCELLED: "취소됨",
  }[status] ?? status;
}

function isSensitiveKey(key: string): boolean {
  const normalized = key.toLowerCase();
  return normalized.includes("password")
    || normalized.includes("secret")
    || normalized.includes("token")
    || normalized.includes("authorization")
    || normalized === "pw"
    || normalized === "pwd";
}

function redactSensitive(value: unknown, key = "", seen = new WeakSet<object>()): unknown {
  if (isSensitiveKey(key) && value !== null && value !== undefined && value !== "") return "••••••";
  if (Array.isArray(value)) return value.map((item) => redactSensitive(item, key, seen));
  if (!value || typeof value !== "object") return value;
  if (seen.has(value)) return "[Circular]";
  seen.add(value);
  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>).map(([childKey, childValue]) => [
      childKey,
      redactSensitive(childValue, childKey, seen),
    ])
  );
}

export function ScenarioWorkbench({
  scenarios,
  capabilities,
  config,
}: {
  scenarios: PreviewCompiledScenario[];
  capabilities: PreviewCapability[];
  config: PreviewRuntimeConfig;
}) {
  const available = useMemo(
    () => scenarios.filter((scenario) => scenario.status !== "UNSUPPORTED" && scenario.stages.length > 0),
    [scenarios]
  );
  const [selectedId, setSelectedId] = useState(available[0]?.id ?? "");
  const scenario = available.find((candidate) => candidate.id === selectedId) ?? available[0] ?? null;
  const [scenarioState, setScenarioState] = useState<ScenarioState>({});
  const stateRef = useRef<ScenarioState>({});
  const initialExecutions = scenario
    ? Object.fromEntries(scenario.stages.map((stage) => [stage.id, emptyExecution(stage)]))
    : {};
  const [executions, setExecutions] = useState<Record<string, PreviewScenarioStageExecution>>(initialExecutions);
  const executionsRef = useRef<Record<string, PreviewScenarioStageExecution>>(initialExecutions);
  const [activeStageId, setActiveStageId] = useState<string | null>(scenario?.entryStageId ?? null);
  const [inputDrafts, setInputDrafts] = useState<Record<string, string>>({});
  const [rawBodyDrafts, setRawBodyDrafts] = useState<Record<string, string>>({});
  const [reviewedStages, setReviewedStages] = useState<Record<string, boolean>>({});
  const [selectedExecutionId, setSelectedExecutionId] = useState<string | null>(null);
  const [inspectorOpen, setInspectorOpen] = useState(true);
  const [running, setRunning] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  function replaceState(next: ScenarioState) {
    stateRef.current = next;
    setScenarioState(next);
  }

  function replaceExecutions(next: Record<string, PreviewScenarioStageExecution>) {
    executionsRef.current = next;
    setExecutions(next);
  }

  function recordExecution(execution: PreviewScenarioStageExecution) {
    const next = { ...executionsRef.current, [execution.stageId]: execution };
    replaceExecutions(next);
    setSelectedExecutionId(execution.stageId);
  }

  function selectScenario(nextId: string) {
    const nextScenario = available.find((candidate) => candidate.id === nextId);
    const nextExecutions = nextScenario
      ? Object.fromEntries(nextScenario.stages.map((stage) => [stage.id, emptyExecution(stage)]))
      : {};
    abortRef.current?.abort();
    setSelectedId(nextId);
    replaceState({});
    replaceExecutions(nextExecutions);
    setInputDrafts({});
    setRawBodyDrafts({});
    setReviewedStages({});
    setActiveStageId(nextScenario?.entryStageId ?? null);
    setSelectedExecutionId(nextScenario?.entryStageId ?? null);
    setRunning(false);
  }

  if (!scenario) {
    return (
      <section className="rounded-panel border border-line bg-panel p-5">
        <p className="text-sm font-bold">실행 가능한 시나리오가 없습니다.</p>
        <p className="mt-1 text-xs text-muted">엔드포인트 뷰에서 개별 API를 확인할 수 있습니다.</p>
      </section>
    );
  }

  const stageById = new Map(scenario.stages.map((stage) => [stage.id, stage]));
  const activeStage = activeStageId ? stageById.get(activeStageId) ?? null : null;
  const selectedExecution = selectedExecutionId ? executions[selectedExecutionId] ?? null : null;
  const completedCount = Object.values(executions).filter((execution) =>
    execution.status === "SUCCESS" || execution.status === "SKIPPED"
  ).length;
  const progress = scenario.stages.length > 0 ? Math.round((completedCount / scenario.stages.length) * 100) : 0;

  function localInputKeys(stage: PreviewCompiledScenarioStage): string[] {
    if (stage.role === "PREPARE" || stage.role === "CONFIGURE" || stage.role === "SELECT_CONTEXT") {
      return stage.outputs.length > 0 ? stage.outputs : stage.inputs;
    }
    return [];
  }

  function markWaiting(stage: PreviewCompiledScenarioStage, message: string) {
    recordExecution({
      ...emptyExecution(stage),
      status: "WAITING_INPUT",
      error: message,
    });
    setActiveStageId(stage.id);
  }

  async function executeStage(stage: PreviewCompiledScenarioStage): Promise<boolean> {
    setActiveStageId(stage.id);
    if (stage.role === "PREPARE" || stage.role === "CONFIGURE" || stage.role === "SELECT_CONTEXT") {
      const keys = localInputKeys(stage);
      const values = Object.fromEntries(keys.map((key) => [key, parseInput(inputDrafts[`${stage.id}:${key}`] ?? "")]));
      if (keys.some((key) => values[key] === "" || values[key] === undefined)) {
        markWaiting(stage, "필수 입력값을 채워주세요.");
        return false;
      }
      replaceState({ ...stateRef.current, ...values });
      recordExecution({
        ...emptyExecution(stage),
        status: "SUCCESS",
        extractedOutputs: redactSensitive(values) as Record<string, unknown>,
        durationMs: 0,
      });
      return true;
    }
    if (stage.role === "SELECT") {
      if (!stateRef.current.selectedId) {
        markWaiting(stage, "목록에서 다음 단계에 사용할 항목을 선택해주세요.");
        return false;
      }
      recordExecution({
        ...emptyExecution(stage),
        status: "SUCCESS",
        extractedOutputs: { selectedId: stateRef.current.selectedId },
        durationMs: 0,
      });
      return true;
    }
    if (stage.role === "REVIEW") {
      if (!reviewedStages[stage.id]) {
        markWaiting(stage, "요청 내용과 위험도를 확인한 뒤 승인해주세요.");
        return false;
      }
      recordExecution({ ...emptyExecution(stage), status: "SUCCESS", durationMs: 0 });
      return true;
    }
    if (stage.role === "COMPLETE" || !stage.capabilityId) {
      recordExecution({ ...emptyExecution(stage), status: "SUCCESS", durationMs: 0 });
      return true;
    }

    const capability = capabilities.find((candidate) => candidate.id === stage.capabilityId);
    if (!capability) {
      recordExecution({ ...emptyExecution(stage), status: "FAILED", error: "연결된 capability를 찾지 못했습니다." });
      return false;
    }

    let requestOverride: Partial<ScenarioRequest> | undefined;
    const rawBody = rawBodyDrafts[stage.id];
    if (rawBody !== undefined) {
      try {
        const parsed = JSON.parse(rawBody);
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
          throw new Error("JSON 객체가 아닙니다.");
        }
        requestOverride = { body: parsed as Record<string, unknown> };
      } catch (error) {
        markWaiting(stage, `요청 본문 JSON을 확인해주세요: ${error instanceof Error ? error.message : "파싱 실패"}`);
        return false;
      }
    }

    recordExecution({ ...emptyExecution(stage), status: "RUNNING", startedAt: Date.now() });
    const controller = new AbortController();
    abortRef.current = controller;
    let result = await runApiStage({
      stage,
      capability,
      state: stateRef.current,
      config,
      signal: controller.signal,
      requestOverride,
    });

    if (stage.role === "TRACK" && result.execution.status === "FAILED" && !controller.signal.aborted) {
      for (let attempt = 0; attempt < 19 && result.execution.status === "FAILED"; attempt += 1) {
        await new Promise<void>((resolve) => window.setTimeout(resolve, 3000));
        if (controller.signal.aborted) break;
        result = await runApiStage({
          stage,
          capability,
          state: stateRef.current,
          config,
          signal: controller.signal,
          requestOverride,
        });
      }
    }

    replaceState(result.nextState);
    if (stage.role === "AUTHENTICATE" && typeof result.nextState.authToken === "string") {
      config.onAuthTokenChange(result.nextState.authToken);
    }
    recordExecution(result.execution);
    return result.execution.status === "SUCCESS";
  }

  async function runFrom(stageId: string) {
    setRunning(true);
    let current: string | undefined = stageId;
    let guard = 0;
    try {
      while (current && guard <= scenario.stages.length) {
        const stage = stageById.get(current);
        if (!stage) break;
        const succeeded = await executeStage(stage);
        if (!succeeded) break;
        current = stage.nextStageIds[0];
        setActiveStageId(current ?? null);
        guard += 1;
      }
    } finally {
      setRunning(false);
      abortRef.current = null;
    }
  }

  function skipStage(stage: PreviewCompiledScenarioStage) {
    if (!stage.optional) return;
    recordExecution({ ...emptyExecution(stage), status: "SKIPPED", durationMs: 0 });
    setActiveStageId(stage.nextStageIds[0] ?? null);
  }

  function resetScenario() {
    abortRef.current?.abort();
    replaceState({});
    replaceExecutions(Object.fromEntries(scenario.stages.map((stage) => [stage.id, emptyExecution(stage)])));
    setInputDrafts({});
    setRawBodyDrafts({});
    setReviewedStages({});
    setActiveStageId(scenario.entryStageId);
    setSelectedExecutionId(scenario.entryStageId);
    setRunning(false);
  }

  const selectionSource = (scenarioState.collection ?? scenarioState.authenticatedCollection) as unknown;
  const selectableRows = extractArray(selectionSource);

  return (
    <section className="relative overflow-hidden rounded-panel border border-line bg-panel">
      <div className="border-b border-line p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="text-[11px] font-extrabold tracking-[.12em] text-brand-strong">SCENARIO RUNTIME</p>
            <h2 className="mt-1 text-lg font-extrabold">{scenario.name}</h2>
            <p className="mt-1 max-w-2xl text-sm text-muted">{scenario.goal}</p>
          </div>
          <select
            className="h-9 rounded-md border border-line-strong bg-background px-3 text-xs font-bold"
            value={scenario.id}
            onChange={(event) => selectScenario(event.target.value)}
          >
            {available.map((candidate) => (
              <option key={candidate.id} value={candidate.id}>{candidate.name}</option>
            ))}
          </select>
        </div>
        <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-white/[0.06]">
          <div className="h-full rounded-full bg-brand transition-[width] duration-500" style={{ width: `${progress}%` }} />
        </div>
        <div className="mt-2 flex items-center justify-between text-[11px] text-muted">
          <span>{completedCount}/{scenario.stages.length} 단계 · 신뢰도 {Math.round(scenario.confidence * 100)}%</span>
          <span>{progress}%</span>
        </div>
      </div>

      <div className={`grid ${inspectorOpen ? "lg:grid-cols-[minmax(0,1fr)_390px]" : ""}`}>
        <div className="space-y-3 p-4">
          {scenario.stages.map((stage, index) => {
            const execution = executions[stage.id] ?? emptyExecution(stage);
            const isActive = activeStage?.id === stage.id;
            const keys = localInputKeys(stage);
            const capability = stage.capabilityId
              ? capabilities.find((candidate) => candidate.id === stage.capabilityId)
              : null;
            const generatedBody = capability ? buildScenarioRequest(stage, scenarioState).body : {};
            const bodyEditable = capability
              && capability.method !== "GET"
              && capability.method !== "DELETE";
            return (
              <article
                key={stage.id}
                className={`rounded-xl border p-4 transition-all ${
                  isActive ? "border-brand/50 bg-brand/[0.04] shadow-[0_0_0_1px_rgba(186,255,74,.08)]" : "border-line bg-white/[0.015]"
                }`}
              >
                <button
                  type="button"
                  className="flex w-full items-start gap-3 text-left"
                  onClick={() => {
                    setActiveStageId(stage.id);
                    setSelectedExecutionId(stage.id);
                  }}
                >
                  <span className="flex size-7 shrink-0 items-center justify-center rounded-full border border-line-strong bg-background text-[11px] font-black">
                    {index + 1}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex flex-wrap items-center gap-2">
                      <span className="text-xs font-extrabold text-brand-strong">{ROLE_LABEL[stage.role] ?? stage.role}</span>
                      <span className={`rounded-full border px-2 py-0.5 text-[10px] font-bold ${STATUS_STYLE[execution.status]}`}>
                        {executionLabel(execution.status)}
                      </span>
                      {stage.risk !== "SAFE" && (
                        <span className="rounded-full bg-danger/10 px-2 py-0.5 text-[10px] font-bold text-danger">{stage.risk}</span>
                      )}
                    </span>
                    <span className="mt-1 block text-sm font-bold">{stage.intent}</span>
                    {stage.operationId && <span className="mt-1 block font-mono text-[10px] text-muted-soft">{stage.operationId}</span>}
                  </span>
                </button>

                {isActive && (
                  <div className="mt-4 border-t border-line pt-4">
                    {keys.length > 0 && (
                      <div className="grid gap-3 sm:grid-cols-2">
                        {keys.map((key) => (
                          <label key={key} className="text-xs font-bold text-muted">
                            {key}
                            <input
                              className="mt-1.5 h-9 w-full rounded-md border border-line-strong bg-background px-3 text-sm text-foreground outline-none focus:border-brand/60"
                              value={inputDrafts[`${stage.id}:${key}`] ?? displayValue(scenarioState[key])}
                              onChange={(event) =>
                                setInputDrafts((previous) => ({ ...previous, [`${stage.id}:${key}`]: event.target.value }))
                              }
                              placeholder={`${key} 값`}
                            />
                          </label>
                        ))}
                      </div>
                    )}
                    {stage.role === "SELECT" && (
                      <div>
                        {selectableRows.length > 0 ? (
                          <select
                            className="h-10 w-full rounded-md border border-line-strong bg-background px-3 text-sm"
                            value={displayValue(scenarioState.selectedId)}
                            onChange={(event) => replaceState({ ...stateRef.current, selectedId: event.target.value })}
                          >
                            <option value="">항목을 선택하세요</option>
                            {selectableRows.map((row) => {
                              const id = rowId(row);
                              const label = String(row.name ?? row.title ?? row.label ?? id);
                              return <option key={id} value={id}>{label}</option>;
                            })}
                          </select>
                        ) : (
                          <p className="rounded-md border border-[#e8b657]/25 bg-[#e8b657]/10 p-3 text-xs text-[#e8b657]">
                            먼저 목록 조회 단계를 실행해야 선택할 수 있습니다.
                          </p>
                        )}
                      </div>
                    )}
                    {stage.role === "REVIEW" && (
                      <label className="flex items-start gap-2 rounded-md border border-line-strong bg-background p-3 text-xs text-muted">
                        <input
                          type="checkbox"
                          className="mt-0.5 accent-[var(--brand)]"
                          checked={reviewedStages[stage.id] ?? false}
                          onChange={(event) => setReviewedStages((previous) => ({ ...previous, [stage.id]: event.target.checked }))}
                        />
                        요청 본문과 대상, 상태 변경 위험도를 확인했습니다.
                      </label>
                    )}
                    {bodyEditable && (
                      <details className="mt-3 rounded-md border border-line-strong bg-background p-3">
                        <summary className="cursor-pointer text-xs font-bold text-muted">원본 요청 본문 편집</summary>
                        <p className="mt-2 text-[11px] text-muted-soft">
                          자동 binding 결과를 JSON으로 덮어쓸 수 있습니다. 실패 후 수정해 같은 단계부터 재시도할 수 있습니다.
                        </p>
                        <textarea
                          className="mt-2 min-h-36 w-full rounded-md border border-line bg-black/25 p-3 font-mono text-[11px] text-foreground outline-none focus:border-brand/60"
                          value={rawBodyDrafts[stage.id] ?? JSON.stringify(generatedBody, null, 2)}
                          onChange={(event) => setRawBodyDrafts((previous) => ({
                            ...previous,
                            [stage.id]: event.target.value,
                          }))}
                          spellCheck={false}
                        />
                      </details>
                    )}
                    {execution.error && <p className="mt-3 text-xs font-bold text-danger">{execution.error}</p>}
                    <div className="mt-4 flex flex-wrap gap-2">
                      <button
                        type="button"
                        className="rounded-md bg-brand px-3 py-2 text-xs font-extrabold text-black disabled:opacity-40"
                        disabled={running}
                        onClick={() => runFrom(stage.id)}
                      >
                        {execution.status === "FAILED" ? "이 단계부터 재시도" : "이 단계부터 실행"}
                      </button>
                      {stage.optional && (
                        <button type="button" className="rounded-md border border-line-strong px-3 py-2 text-xs font-bold" onClick={() => skipStage(stage)}>
                          건너뛰기
                        </button>
                      )}
                      {running && (
                        <button
                          type="button"
                          className="rounded-md border border-danger/40 px-3 py-2 text-xs font-bold text-danger"
                          onClick={() => abortRef.current?.abort()}
                        >
                          실행 취소
                        </button>
                      )}
                    </div>
                  </div>
                )}
              </article>
            );
          })}
          <div className="flex flex-wrap gap-2 pt-1">
            <button
              type="button"
              className="rounded-md bg-brand px-4 py-2.5 text-xs font-extrabold text-black disabled:opacity-40"
              disabled={running || !scenario.entryStageId}
              onClick={() => scenario.entryStageId && runFrom(scenario.entryStageId)}
            >
              시나리오 실행
            </button>
            <button type="button" className="rounded-md border border-line-strong px-4 py-2.5 text-xs font-bold" onClick={resetScenario}>
              초기화
            </button>
            <button
              type="button"
              className="rounded-md border border-line-strong px-4 py-2.5 text-xs font-bold lg:hidden"
              onClick={() => setInspectorOpen((value) => !value)}
            >
              Inspector
            </button>
          </div>
        </div>

        {inspectorOpen && (
          <aside className="border-t border-line bg-background/70 p-4 lg:border-l lg:border-t-0">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-[10px] font-extrabold tracking-[.12em] text-muted-soft">DEVELOPER INSPECTOR</p>
                <h3 className="mt-1 text-sm font-extrabold">{selectedExecution?.stageId ?? "단계를 선택하세요"}</h3>
              </div>
              <button type="button" className="hidden text-xs font-bold text-muted lg:block" onClick={() => setInspectorOpen(false)}>
                접기
              </button>
            </div>
            {selectedExecution ? (
              <div className="mt-4 space-y-3 text-xs">
                <InspectorBlock title="요청" value={selectedExecution.request} />
                <InspectorBlock
                  title="입력 Binding"
                  value={scenario.stages.find((stage) => stage.id === selectedExecution.stageId)?.inputBindings ?? []}
                />
                <InspectorBlock title="응답 헤더" value={selectedExecution.responseHeaders} />
                <InspectorBlock title="응답" value={selectedExecution.response} />
                <InspectorBlock title="추출된 상태" value={selectedExecution.extractedOutputs} />
                <InspectorBlock title="검증 결과" value={selectedExecution.assertions} />
                <div className="grid grid-cols-2 gap-2 rounded-md border border-line p-3 text-muted">
                  <span>상태</span><strong className="text-right text-foreground">{executionLabel(selectedExecution.status)}</strong>
                  <span>HTTP</span><strong className="text-right text-foreground">{selectedExecution.method ?? "—"}</strong>
                  <span>소요시간</span><strong className="text-right text-foreground">{selectedExecution.durationMs === null ? "—" : `${selectedExecution.durationMs}ms`}</strong>
                </div>
                {selectedExecution.url && (
                  <p className="break-all rounded-md border border-line bg-black/20 p-3 font-mono text-[10px] text-muted">{selectedExecution.url}</p>
                )}
              </div>
            ) : (
              <p className="mt-4 text-xs text-muted">단계를 선택하면 요청·응답·상태 추출·검증 결과가 동기화되어 표시됩니다.</p>
            )}
            <details className="mt-4 rounded-md border border-line p-3">
              <summary className="cursor-pointer text-xs font-bold">Scenario State</summary>
              <pre className="mt-3 max-h-72 overflow-auto whitespace-pre-wrap break-all font-mono text-[10px] text-muted">
                {JSON.stringify(redactSensitive(scenarioState), null, 2)}
              </pre>
            </details>
          </aside>
        )}
      </div>
      {!inspectorOpen && (
        <button
          type="button"
          className="absolute right-4 top-4 hidden rounded-md border border-line-strong bg-panel px-3 py-2 text-xs font-bold lg:block"
          onClick={() => setInspectorOpen(true)}
        >
          Inspector 열기
        </button>
      )}
    </section>
  );
}

function InspectorBlock({ title, value }: { title: string; value: unknown }) {
  return (
    <details open className="rounded-md border border-line bg-black/20 p-3">
      <summary className="cursor-pointer font-bold text-muted">{title}</summary>
      <pre className="mt-2 max-h-56 overflow-auto whitespace-pre-wrap break-all font-mono text-[10px] text-foreground">
        {JSON.stringify(redactSensitive(value), null, 2)}
      </pre>
    </details>
  );
}
