"use client";

import { useMemo, useState } from "react";
import { createPortal } from "react-dom";
import type {
  PreviewCompiledScenario,
  PreviewScenarioStageExecution,
} from "@/lib/types";
import type { ExperienceAction, ExperienceScreen } from "./productExperience";
import type { ProductExperienceTheme } from "./productTheme";
import type { ScenarioState } from "./runtime";

type InspectorTab = "FLOW" | "EVIDENCE" | "STATE";

const STATUS_LABEL: Record<string, string> = {
  IDLE: "대기",
  WAITING_INPUT: "입력 필요",
  RUNNING: "실행 중",
  SUCCESS: "성공",
  FAILED: "실패",
  SKIPPED: "건너뜀",
  CANCELLED: "취소됨",
};

const STATUS_CLASS: Record<string, string> = {
  IDLE: "border-white/10 text-white/35",
  WAITING_INPUT: "border-white/20 bg-white/[0.06] text-[var(--px-hero-muted)]",
  RUNNING: "border-white/20 bg-white/[0.08] text-[var(--px-hero-ink)]",
  SUCCESS: "border-success/30 bg-success/10 text-success",
  FAILED: "border-danger/30 bg-danger/10 text-danger",
  SKIPPED: "border-white/10 bg-white/[0.03] text-white/40",
  CANCELLED: "border-white/10 bg-white/[0.03] text-white/45",
};

function isSensitiveKey(key: string): boolean {
  return /(password|secret|token|authorization|api.?key|cookie)/i.test(key);
}

function redact(value: unknown, key = "", seen = new WeakSet<object>()): unknown {
  if (isSensitiveKey(key) && value !== null && value !== undefined && value !== "") return "••••••";
  if (Array.isArray(value)) return value.map((item) => redact(item, key, seen));
  if (!value || typeof value !== "object") return value;
  if (seen.has(value)) return "[Circular]";
  seen.add(value);
  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>).map(([childKey, childValue]) => [
      childKey,
      redact(childValue, childKey, seen),
    ])
  );
}

function JsonBlock({ value, empty = "기록된 값이 없습니다." }: { value: unknown; empty?: string }) {
  if (value === null || value === undefined || (typeof value === "object" && Object.keys(value).length === 0)) {
    return <p className="rounded-[12px] border border-white/10 bg-black/20 p-3 text-xs text-white/35">{empty}</p>;
  }
  return (
    <pre className="max-h-72 overflow-auto whitespace-pre-wrap break-all rounded-[12px] border border-white/10 bg-black/25 p-3 font-mono text-[11px] leading-5 text-white/70">
      {JSON.stringify(redact(value), null, 2)}
    </pre>
  );
}

function Label({ children }: { children: string }) {
  return <p className="mb-2 text-[9px] font-black uppercase tracking-[.15em] text-white/35">{children}</p>;
}

export function ProductExperienceInspector({
  open,
  screen,
  action,
  scenario,
  scenarioState,
  executions,
  timeline,
  currentStageId,
  running,
  selectedRecord,
  rawBodyDrafts,
  theme,
  onRawBodyChange,
  onRetry,
  onCancel,
  onClose,
}: {
  open: boolean;
  screen: ExperienceScreen;
  action: ExperienceAction | null;
  scenario: PreviewCompiledScenario | null;
  scenarioState: ScenarioState;
  executions: Record<string, PreviewScenarioStageExecution>;
  timeline: PreviewScenarioStageExecution[];
  currentStageId: string | null;
  running: boolean;
  selectedRecord: Record<string, unknown> | null;
  rawBodyDrafts: Record<string, string>;
  theme: ProductExperienceTheme;
  onRawBodyChange: (stageId: string, value: string) => void;
  onRetry: (stageId: string) => void;
  onCancel: () => void;
  onClose: () => void;
}) {
  const [tab, setTab] = useState<InspectorTab>("FLOW");
  const [selectedStageId, setSelectedStageId] = useState<string | null>(null);
  const stages = scenario?.stages ?? [];
  const latestExecution = timeline.at(-1) ?? null;
  const effectiveStageId = selectedStageId
    ?? currentStageId
    ?? latestExecution?.stageId
    ?? stages[0]?.id
    ?? null;
  const activeStage = stages.find((stage) => stage.id === effectiveStageId) ?? null;
  const activeExecution = effectiveStageId ? executions[effectiveStageId] ?? null : null;
  const bodyDraft = effectiveStageId
    ? rawBodyDrafts[effectiveStageId]
      ?? (activeExecution?.request?.body ? JSON.stringify(activeExecution.request.body, null, 2) : "")
    : "";
  const completed = useMemo(
    () => Object.values(executions).filter((execution) => execution.status === "SUCCESS").length,
    [executions]
  );

  if (!open || typeof document === "undefined") return null;

  return createPortal(
    <div
      className="fixed inset-0 z-[240]"
      style={theme.style}
      data-product-theme={theme.id}
      data-blueprint-theme={theme.blueprintThemeId}
    >
      <button
        type="button"
        className="absolute inset-0 bg-black/45 backdrop-blur-[2px]"
        onClick={onClose}
        aria-label="Inspector 닫기"
      />
      <aside className="absolute inset-y-0 right-0 flex w-[min(94vw,560px)] flex-col border-l border-white/10 bg-[var(--px-hero)] text-[var(--px-hero-ink)] shadow-[var(--px-shadow-lg)]">
        <header className="border-b border-white/10 px-5 py-4">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-[9px] font-black uppercase tracking-[.18em] text-[var(--px-hero-muted)]">Live test inspector</p>
              <h2 className="mt-1 text-lg font-black">{action?.label ?? screen.title}</h2>
              <p className="mt-1 text-xs text-white/45">
                {scenario?.name ?? "제품 화면을 조작하면 실행 증거가 여기에 기록됩니다."}
              </p>
            </div>
            <button
              type="button"
              className="grid h-9 w-9 shrink-0 place-items-center rounded-[10px] border border-white/10 text-lg text-white/55 hover:bg-white/5 hover:text-white"
              onClick={onClose}
              aria-label="닫기"
            >
              ×
            </button>
          </div>
          <div className="mt-4 grid grid-cols-3 gap-2">
            <div className="rounded-[12px] border border-white/10 bg-white/[0.03] p-3">
              <strong className="text-lg">{completed}</strong>
              <p className="mt-0.5 text-[9px] font-bold text-white/35">완료 단계</p>
            </div>
            <div className="rounded-[12px] border border-white/10 bg-white/[0.03] p-3">
              <strong className="text-lg">{timeline.length}</strong>
              <p className="mt-0.5 text-[9px] font-bold text-white/35">API 시도</p>
            </div>
            <div className="rounded-[12px] border border-white/10 bg-white/[0.03] p-3">
              <strong className="text-lg">{Object.keys(scenarioState).length}</strong>
              <p className="mt-0.5 text-[9px] font-bold text-white/35">공유 상태</p>
            </div>
          </div>
        </header>

        <nav className="grid grid-cols-3 border-b border-white/10 px-4 pt-2">
          {([
            ["FLOW", "실행 흐름"],
            ["EVIDENCE", "요청·응답"],
            ["STATE", "공유 상태"],
          ] as Array<[InspectorTab, string]>).map(([id, label]) => (
            <button
              type="button"
              key={id}
              onClick={() => setTab(id)}
              className={`border-b-2 px-3 py-3 text-xs font-extrabold transition-colors ${
                tab === id ? "border-[var(--px-hero-muted)] text-[var(--px-hero-ink)]" : "border-transparent text-white/40 hover:text-white/70"
              }`}
            >
              {label}
            </button>
          ))}
        </nav>

        <div className="min-h-0 flex-1 overflow-y-auto p-5">
          {tab === "FLOW" && (
            <div className="space-y-5">
              <div>
                <Label>Current context</Label>
                <div className="rounded-[14px] border border-white/10 bg-white/[0.03] p-4">
                  <p className="text-xs font-bold text-white/40">{screen.label}</p>
                  <strong className="mt-1 block text-sm">{screen.title}</strong>
                  {selectedRecord && <p className="mt-2 truncate text-xs text-white/45">선택 ID · {String(selectedRecord.id ?? selectedRecord.uuid ?? "선택됨")}</p>}
                </div>
              </div>

              <div>
                <Label>Scenario stages</Label>
                {stages.length === 0 ? (
                  <p className="rounded-[12px] border border-white/10 bg-white/[0.02] p-4 text-xs text-white/40">
                    화면의 기능 버튼을 실행하면 연결된 시나리오 단계가 표시됩니다.
                  </p>
                ) : (
                  <ol className="space-y-2">
                    {stages.map((stage, index) => {
                      const execution = executions[stage.id];
                      const status = execution?.status ?? "IDLE";
                      const selected = stage.id === effectiveStageId;
                      return (
                        <li key={stage.id}>
                          <button
                            type="button"
                            onClick={() => {
                              setSelectedStageId(stage.id);
                              setTab("EVIDENCE");
                            }}
                            className={`flex w-full items-start gap-3 rounded-[13px] border p-3 text-left transition-colors ${
                              selected ? "border-white/25 bg-white/[0.06]" : "border-white/10 bg-white/[0.02] hover:bg-white/[0.04]"
                            }`}
                          >
                            <span className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-white/[0.06] text-[10px] font-black text-white/50">{index + 1}</span>
                            <span className="min-w-0 flex-1">
                              <span className="block text-[9px] font-black tracking-[.08em] text-white/35">{stage.role}</span>
                              <strong className="mt-1 block text-xs leading-5">{stage.intent}</strong>
                            </span>
                            <span className={`shrink-0 rounded-full border px-2 py-1 text-[9px] font-bold ${STATUS_CLASS[status]}`}>
                              {STATUS_LABEL[status]}
                            </span>
                          </button>
                        </li>
                      );
                    })}
                  </ol>
                )}
              </div>
            </div>
          )}

          {tab === "EVIDENCE" && (
            <div className="space-y-5">
              {activeStage ? (
                <>
                  <div>
                    <Label>Selected stage</Label>
                    <select
                      value={activeStage.id}
                      onChange={(event) => setSelectedStageId(event.target.value)}
                      className="h-11 w-full appearance-none rounded-[11px] border border-white/10 bg-white/[0.04] px-3 text-xs font-bold text-white outline-none"
                    >
                      {stages.map((stage) => (
                        <option key={stage.id} value={stage.id}>{stage.role} · {stage.intent}</option>
                      ))}
                    </select>
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    <div className="rounded-[12px] border border-white/10 bg-white/[0.03] p-3">
                      <p className="text-[9px] font-bold text-white/35">OPERATION</p>
                      <strong className="mt-1 block truncate text-xs">{activeStage.operationId ?? "LOCAL"}</strong>
                    </div>
                    <div className="rounded-[12px] border border-white/10 bg-white/[0.03] p-3">
                      <p className="text-[9px] font-bold text-white/35">DURATION</p>
                      <strong className="mt-1 block text-xs">{activeExecution?.durationMs !== null && activeExecution?.durationMs !== undefined ? `${activeExecution.durationMs}ms` : "—"}</strong>
                    </div>
                  </div>

                  <div>
                    <Label>Request target</Label>
                    <div className="rounded-[12px] border border-white/10 bg-black/20 p-3 font-mono text-[11px] leading-5 text-white/65">
                      <strong className="mr-2 text-[var(--px-hero-muted)]">{activeExecution?.method ?? "—"}</strong>
                      {activeExecution?.url ?? "아직 요청하지 않았습니다."}
                    </div>
                  </div>

                  <div>
                    <Label>Path · query · headers</Label>
                    <JsonBlock value={activeExecution?.request ? {
                      path: activeExecution.request.path,
                      query: activeExecution.request.query,
                      headers: activeExecution.request.headers,
                    } : null} />
                  </div>

                  <div>
                    <Label>Binding contract</Label>
                    <JsonBlock value={{
                      inputs: activeStage.inputBindings,
                      outputs: activeStage.outputBindings,
                    }} />
                  </div>

                  <div>
                    <Label>Raw request body</Label>
                    <textarea
                      value={bodyDraft}
                      onChange={(event) => onRawBodyChange(activeStage.id, event.target.value)}
                      className="min-h-40 w-full rounded-[12px] border border-white/10 bg-black/25 p-3 font-mono text-[11px] leading-5 text-white/75 outline-none focus:border-white/30"
                      placeholder="{ }"
                      spellCheck={false}
                    />
                    <p className="mt-2 text-[10px] leading-4 text-white/30">수정한 JSON 본문은 이 단계부터 재시도할 때 사용됩니다.</p>
                  </div>

                  <div>
                    <Label>Response</Label>
                    <JsonBlock value={activeExecution?.response} empty="아직 받은 응답이 없습니다." />
                  </div>

                  <div>
                    <Label>Response headers</Label>
                    <JsonBlock value={activeExecution?.responseHeaders} empty="아직 받은 응답 헤더가 없습니다." />
                  </div>

                  <div>
                    <Label>Extracted outputs</Label>
                    <JsonBlock value={activeExecution?.extractedOutputs} empty="추출된 시나리오 출력값이 없습니다." />
                  </div>

                  <div>
                    <Label>Assertions</Label>
                    {activeExecution?.assertions.length ? (
                      <div className="space-y-2">
                        {activeExecution.assertions.map((assertion, index) => (
                          <div key={`${assertion.type}-${index}`} className={`rounded-[12px] border p-3 ${assertion.passed ? "border-success/25 bg-success/5" : "border-danger/25 bg-danger/5"}`}>
                            <div className="flex justify-between gap-3">
                              <strong className="text-xs">{assertion.type}</strong>
                              <span className={`text-[9px] font-black ${assertion.passed ? "text-success" : "text-danger"}`}>{assertion.passed ? "PASS" : "FAIL"}</span>
                            </div>
                            <p className="mt-1 text-[10px] leading-4 text-white/45">{assertion.message}</p>
                          </div>
                        ))}
                      </div>
                    ) : <JsonBlock value={null} empty="실행된 검증이 없습니다." />}
                  </div>

                  {activeExecution?.error && (
                    <div className="rounded-[12px] border border-danger/30 bg-danger/10 p-3 text-xs leading-5 text-danger">
                      {activeExecution.error}
                    </div>
                  )}
                </>
              ) : (
                <p className="rounded-[12px] border border-white/10 bg-white/[0.02] p-4 text-xs text-white/40">
                  기능을 실행한 뒤 요청과 응답을 확인할 수 있습니다.
                </p>
              )}
            </div>
          )}

          {tab === "STATE" && (
            <div className="space-y-5">
              <div>
                <Label>Scenario state</Label>
                <JsonBlock value={scenarioState} empty="아직 공유된 시나리오 상태가 없습니다." />
              </div>
              <div>
                <Label>Selected record</Label>
                <JsonBlock value={selectedRecord} empty="제품 화면에서 항목을 선택해 주세요." />
              </div>
              <div>
                <Label>Recent execution timeline</Label>
                <div className="space-y-2">
                  {timeline.slice(-10).reverse().map((execution, index) => (
                    <button
                      type="button"
                      key={`${execution.stageId}-${execution.startedAt}-${index}`}
                      onClick={() => {
                        setSelectedStageId(execution.stageId);
                        setTab("EVIDENCE");
                      }}
                      className="flex w-full items-center justify-between gap-3 rounded-[11px] border border-white/10 bg-white/[0.02] px-3 py-2 text-left hover:bg-white/[0.04]"
                    >
                      <span className="truncate text-[10px] font-bold text-white/55">{execution.operationId ?? execution.stageId}</span>
                      <span className={`rounded-full border px-2 py-1 text-[8px] font-black ${STATUS_CLASS[execution.status]}`}>{STATUS_LABEL[execution.status]}</span>
                    </button>
                  ))}
                  {timeline.length === 0 && <JsonBlock value={null} empty="아직 실행 기록이 없습니다." />}
                </div>
              </div>
            </div>
          )}
        </div>

        <footer className="flex flex-wrap items-center gap-2 border-t border-white/10 bg-black/15 p-4">
          <div className="flex gap-2">
            {running && (
              <button
                type="button"
                className="min-h-[34px] rounded-[10px] border border-danger/35 bg-danger/10 px-3 text-xs font-bold text-danger hover:bg-danger/15"
                onClick={onCancel}
              >
                실행 취소
              </button>
            )}
            {activeStage && (
              <button
                type="button"
                className="min-h-[34px] rounded-[10px] border border-white/20 bg-white/[0.08] px-3 text-xs font-bold text-[var(--px-hero-ink)] hover:bg-white/[0.12] disabled:cursor-not-allowed disabled:opacity-50"
                disabled={running}
                onClick={() => onRetry(activeStage.id)}
              >
                이 단계부터 재시도
              </button>
            )}
          </div>
        </footer>
      </aside>
    </div>,
    document.body
  );
}
