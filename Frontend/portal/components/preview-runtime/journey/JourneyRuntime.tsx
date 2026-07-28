"use client";

import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import { createPortal } from "react-dom";
import { Button } from "@/components/ui/button";
import { Field, Input, Textarea } from "@/components/ui/field";
import { isPasswordLikeField } from "../api";
import {
  partDescriptor,
  renderBlueprintPart,
} from "../blueprints/adapters/registry";
import { BlueprintModalFrame } from "../blueprints/modals";
import type { BlueprintOption, BlueprintRecord } from "../blueprints/core";
import type {
  JourneyExecutionResult,
  JourneySession,
  JourneyStep,
} from "./types";

const STEP_TRANSITION_MS = 220;

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function humanize(value: string): string {
  return value
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replaceAll("_", " ")
    .replaceAll("-", " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function mergeSubmission(
  previous: Record<string, unknown>,
  values: unknown[],
  keys: string[] = []
): Record<string, unknown> {
  if (values.length === 0) return previous;
  if (values.length === 1) {
    if (keys[0]) return { ...previous, [keys[0]]: values[0] };
    const record = asRecord(values[0]);
    return Object.keys(record).length > 0
      ? { ...previous, ...record }
      : { ...previous, [keys[0] ?? "value"]: values[0] };
  }
  return {
    ...previous,
    ...Object.fromEntries(values.map((value, index) => [keys[index] ?? `value${index + 1}`, value])),
  };
}

function JourneyForm({
  step,
  initialValues,
  open,
  onSubmit,
  onClose,
}: {
  step: JourneyStep;
  initialValues: Record<string, unknown>;
  open: boolean;
  onSubmit: (values: Record<string, unknown>) => void;
  onClose: () => void;
}) {
  const fields = step.fields ?? [];
  const [values, setValues] = useState<Record<string, string>>(() =>
    Object.fromEntries(fields.map((field) => [field, String(initialValues[field] ?? "")]))
  );

  function submit(event: FormEvent) {
    event.preventDefault();
    onSubmit(values);
  }

  return (
    <BlueprintModalFrame
      open={open}
      onClose={onClose}
      title={step.title}
      description={step.description}
      eyebrow="Guided journey"
      footer={(
        <>
          <Button type="button" onClick={onClose}>이전</Button>
          <Button type="submit" form={`journey-form-${step.id}`} variant="primary">계속</Button>
        </>
      )}
    >
      <form id={`journey-form-${step.id}`} onSubmit={submit}>
        {fields.length === 0 ? (
          <JourneySummary values={initialValues} emptyMessage="추가 입력 없이 다음 단계로 진행할 수 있습니다." />
        ) : fields.map((field) => (
          <Field key={field} label={humanize(field)} htmlFor={`journey-${step.id}-${field}`}>
            {/(description|reason|note|message|content|body)/i.test(field) ? (
              <Textarea
                id={`journey-${step.id}-${field}`}
                value={values[field] ?? ""}
                onChange={(event) => setValues((current) => ({ ...current, [field]: event.target.value }))}
              />
            ) : (
              <Input
                id={`journey-${step.id}-${field}`}
                type={isPasswordLikeField(field) ? "password" : "text"}
                value={values[field] ?? ""}
                onChange={(event) => setValues((current) => ({ ...current, [field]: event.target.value }))}
              />
            )}
          </Field>
        ))}
      </form>
    </BlueprintModalFrame>
  );
}

function JourneySummary({
  values,
  emptyMessage,
}: {
  values: Record<string, unknown>;
  emptyMessage: string;
}) {
  const entries = Object.entries(values).filter(([, value]) => value !== "" && value !== null && value !== undefined);
  if (entries.length === 0) {
    return <p className="rounded-[12px] border border-line bg-panel p-4 text-sm text-muted-soft">{emptyMessage}</p>;
  }
  return (
    <dl className="grid gap-2 sm:grid-cols-2">
      {entries.map(([key, value]) => (
        <div key={key} className="rounded-[12px] border border-line bg-panel p-3">
          <dt className="text-[10px] font-extrabold uppercase tracking-[0.12em] text-muted-soft">{humanize(key)}</dt>
          <dd className="mt-1 break-words text-sm font-semibold">{typeof value === "object" ? JSON.stringify(value) : String(value)}</dd>
        </div>
      ))}
    </dl>
  );
}

function ExecutionModal({
  step,
  open,
  error,
  onRetry,
  onBack,
  onCancel,
}: {
  step: JourneyStep;
  open: boolean;
  error: string | null;
  onRetry: () => void;
  onBack: () => void;
  onCancel: () => void;
}) {
  return (
    <BlueprintModalFrame
      open={open}
      onClose={error ? onBack : onCancel}
      title={error ? "작업을 완료하지 못했습니다" : step.title}
      description={error ?? step.description}
      eyebrow={error ? "Execution failed" : "Executing"}
      size="sm"
      footer={error ? (
        <>
          <Button onClick={onBack}>이전 단계</Button>
          <Button onClick={onCancel}>닫기</Button>
          <Button variant="primary" onClick={onRetry}>다시 시도</Button>
        </>
      ) : undefined}
    >
      {error ? (
        <div className="rounded-[13px] border border-danger-soft bg-danger/10 p-4 text-sm leading-6 text-danger">
          {error}
        </div>
      ) : (
        <div className="flex items-center gap-4 rounded-[13px] border border-line bg-panel p-5">
          <span className="h-9 w-9 animate-spin rounded-full border-[3px] border-line border-t-brand" aria-hidden />
          <div>
            <strong className="text-sm">API 작업을 실행하고 있습니다.</strong>
            <p className="mt-1 text-xs leading-5 text-muted-soft">창을 닫지 않아도 완료 결과가 이 흐름에 반영됩니다.</p>
          </div>
        </div>
      )}
    </BlueprintModalFrame>
  );
}

function SuccessModal({
  step,
  open,
  result,
  onClose,
}: {
  step: JourneyStep;
  open: boolean;
  result: JourneyExecutionResult | null;
  onClose: () => void;
}) {
  return (
    <BlueprintModalFrame
      open={open}
      onClose={onClose}
      title={step.title}
      description={result?.message ?? step.description}
      eyebrow="Journey complete"
      size="sm"
      footer={<Button variant="primary" onClick={onClose}>완료</Button>}
    >
      <div className="flex items-start gap-4 rounded-[13px] border border-brand/30 bg-brand/10 p-5">
        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-brand text-xl font-black text-[#0a0c08]">✓</span>
        <div>
          <strong className="text-sm">모든 단계를 완료했습니다.</strong>
          <p className="mt-1 text-xs leading-5 text-muted-soft">입력, 검토, 확인 및 API 실행이 정상적으로 끝났습니다.</p>
        </div>
      </div>
    </BlueprintModalFrame>
  );
}

function JourneyDock({
  session,
  step,
  historyLength,
  onBack,
  onCancel,
}: {
  session: JourneySession;
  step: JourneyStep;
  historyLength: number;
  onBack: () => void;
  onCancel: () => void;
}) {
  if (typeof document === "undefined") return null;
  const index = session.blueprint.steps.findIndex((candidate) => candidate.id === step.id);
  return createPortal(
    <aside className="pointer-events-none fixed left-1/2 top-3 z-[180] w-[min(92vw,620px)] -translate-x-1/2">
      <div className="pointer-events-auto flex items-center gap-3 rounded-[14px] border border-line bg-background/95 px-3 py-2 shadow-2xl backdrop-blur-md">
        <div className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-3 text-[10px] font-extrabold uppercase tracking-[0.12em] text-muted-soft">
            <span className="truncate">{session.blueprint.title}</span>
            <span>{index + 1} / {session.blueprint.steps.length}</span>
          </div>
          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-line">
            <div
              className="h-full rounded-full bg-brand transition-[width] duration-500 ease-out"
              style={{ width: `${((index + 1) / session.blueprint.steps.length) * 100}%` }}
            />
          </div>
        </div>
        {historyLength > 0 && step.type !== "EXECUTE" && step.type !== "SUCCESS" && (
          <Button size="small" onClick={onBack}>이전</Button>
        )}
        <Button size="small" onClick={onCancel}>{step.type === "SUCCESS" ? "닫기" : "전체 취소"}</Button>
      </div>
    </aside>,
    document.body
  );
}

function GeneratedJourneyStep({
  session,
  step,
  draft,
  open,
  onAdvance,
  onClose,
}: {
  session: JourneySession;
  step: JourneyStep;
  draft: Record<string, unknown>;
  open: boolean;
  onAdvance: (values?: Record<string, unknown>) => void;
  onClose: () => void;
}) {
  const componentId = step.componentId!;
  const targetName = session.targetId || session.capability.resourceName;
  const options: BlueprintOption[] = [
    { value: "default", label: "Default", description: "기본 설정을 사용합니다." },
    { value: "primary", label: "Primary", description: "주요 대상으로 지정합니다." },
    { value: "approved", label: "Approved", description: "승인 상태로 전환합니다." },
  ];
  const candidates = [
    { id: "current", title: "Current owner", subtitle: "현재 담당자", role: "OWNER" },
    { id: "operations", title: "Operations team", subtitle: "운영 담당 그룹", role: "ADMIN" },
  ];
  const advance = (keys: string[] = []) => (...values: unknown[]) =>
    onAdvance(mergeSubmission(draft, values, keys));
  const props: Record<string, unknown> = {
    open,
    onClose,
    onCancel: onClose,
    busy: false,
    submitting: false,
    initialValues: draft,
    context: draft as BlueprintRecord,
    options,
    actions: options,
    plans: options,
    regions: options,
    environments: options,
    strategies: options,
    roles: options,
    organizations: options,
    targetOptions: options,
    candidates,
    approvers: candidates,
    groups: [{ id: "default", label: "Default permissions", permissions: [] }],
    dependencies: session.capability.dependencies.length > 0
      ? session.capability.dependencies.map((id) => ({
          id,
          name: id,
          type: "Capability dependency",
          impact: "AFFECTED",
          description: "이 작업이 완료되면 연관 capability의 데이터가 변경될 수 있습니다.",
        }))
      : [{
          id: targetName,
          name: targetName,
          type: session.capability.resourceName,
          impact: session.capability.risk === "SAFE" ? "REVIEW" : "AFFECTED",
          description: "현재 선택한 대상과 이 대상에 연결된 화면 데이터가 새로고침됩니다.",
        }],
    availableFields: session.capability.fields,
    channels: options,
    statuses: options,
    currentStatus: String(draft.status ?? ""),
    currentOwnerId: String(draft.ownerId ?? ""),
    selectedCount: 1,
    targetName,
    sourceName: session.capability.resourceName,
    defaultName: session.capability.resourceName,
    expectedText: targetName,
    description: step.description,
    impact: [
      `${session.capability.method} ${session.capability.path}`,
      `${session.capability.risk} · ${session.capability.automationPolicy}`,
    ],
    actionLabel: session.capability.action ?? session.capability.resourceName,
    progress: null,
    request: draft,
    response: null,
    onConfirm: advance(),
    onComplete: advance(),
    onSubmit: advance(),
    onSave: advance(),
    onApply: advance(["action", "note"]),
    onAssign: advance(["owner"]),
    onChange: advance(["status", "note"]),
    onSchedule: advance(),
    onContinue: advance(),
    onDuplicate: advance(),
    onExport: advance(),
    onImport: advance(["file", "options"]),
    onCommit: advance(),
    onDeploy: advance(),
    onPublish: advance(),
    onInvite: advance(),
    onCopy: () => undefined,
    onValidate: async () => ({ valid: 0, invalid: 0 }),
  };
  const rendered = renderBlueprintPart(componentId, props);
  if (partDescriptor(componentId)?.overlayPresentation === "SELF_HOSTED") return <>{rendered}</>;
  return (
    <BlueprintModalFrame
      open={open}
      onClose={onClose}
      title={step.title}
      description={step.description}
      eyebrow="Guided journey"
      size="lg"
    >
      {rendered as ReactNode}
    </BlueprintModalFrame>
  );
}

export function JourneyRuntime({
  session,
  onExecute,
  onClose,
  onCompleted,
}: {
  session: JourneySession | null;
  onExecute: (session: JourneySession, values: Record<string, unknown>) => Promise<JourneyExecutionResult | void>;
  onClose: () => void;
  onCompleted?: (session: JourneySession) => void;
}) {
  const blueprint = session?.blueprint;
  const stepsById = useMemo(
    () => new Map(blueprint?.steps.map((step) => [step.id, step]) ?? []),
    [blueprint]
  );
  const [activeStepId, setActiveStepId] = useState(blueprint?.entryStepId ?? "");
  const [history, setHistory] = useState<string[]>([]);
  const [draft, setDraft] = useState<Record<string, unknown>>(session?.initialValues ?? {});
  const [presented, setPresented] = useState(Boolean(session));
  const [executionError, setExecutionError] = useState<string | null>(null);
  const [executionResult, setExecutionResult] = useState<JourneyExecutionResult | null>(null);
  const [executionAttempt, setExecutionAttempt] = useState(0);
  const executionKeyRef = useRef("");
  const transitionTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => {
    if (transitionTimerRef.current) clearTimeout(transitionTimerRef.current);
  }, []);

  const activeStep = stepsById.get(activeStepId);

  function transitionTo(nextStepId: string, pushHistory: boolean) {
    if (!activeStep) return;
    setPresented(false);
    if (transitionTimerRef.current) clearTimeout(transitionTimerRef.current);
    transitionTimerRef.current = setTimeout(() => {
      if (pushHistory) setHistory((current) => [...current, activeStep.id]);
      setActiveStepId(nextStepId);
      requestAnimationFrame(() => setPresented(true));
    }, STEP_TRANSITION_MS);
  }

  function closeJourney(completed = false) {
    setPresented(false);
    if (transitionTimerRef.current) clearTimeout(transitionTimerRef.current);
    transitionTimerRef.current = setTimeout(() => {
      if (completed && session) onCompleted?.(session);
      onClose();
    }, STEP_TRANSITION_MS);
  }

  function advance(values: Record<string, unknown> = {}) {
    if (!activeStep?.nextStepId) return;
    setDraft((current) => ({ ...current, ...values }));
    transitionTo(activeStep.nextStepId, true);
  }

  function back() {
    const previous = history.at(-1);
    if (!previous) {
      closeJourney();
      return;
    }
    setPresented(false);
    if (transitionTimerRef.current) clearTimeout(transitionTimerRef.current);
    transitionTimerRef.current = setTimeout(() => {
      setHistory((current) => current.slice(0, -1));
      setExecutionError(null);
      setActiveStepId(previous);
      requestAnimationFrame(() => setPresented(true));
    }, STEP_TRANSITION_MS);
  }

  useEffect(() => {
    if (!session || activeStep?.type !== "EXECUTE") return;
    const executionKey = `${session.id}:${activeStep.id}:${executionAttempt}`;
    if (executionKeyRef.current === executionKey) return;
    executionKeyRef.current = executionKey;
    setExecutionError(null);
    let cancelled = false;
    void onExecute(session, draft)
      .then((result) => {
        if (cancelled) return;
        setExecutionResult(result ?? null);
        if (activeStep.nextStepId) transitionTo(activeStep.nextStepId, true);
      })
      .catch((cause) => {
        if (!cancelled) setExecutionError(cause instanceof Error ? cause.message : "작업 실행에 실패했습니다.");
      });
    return () => {
      cancelled = true;
    };
    // draft는 EXECUTE 진입 시점의 스냅샷이며 실행 도중 입력 변경은 없다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeStepId, executionAttempt, session]);

  if (!session || !activeStep) return null;

  let content: ReactNode;
  if (activeStep.type === "EXECUTE") {
    content = (
      <ExecutionModal
        step={activeStep}
        open={presented}
        error={executionError}
        onRetry={() => setExecutionAttempt((attempt) => attempt + 1)}
        onBack={back}
        onCancel={() => closeJourney()}
      />
    );
  } else if (activeStep.type === "SUCCESS") {
    content = (
      <SuccessModal
        step={activeStep}
        open={presented}
        result={executionResult}
        onClose={() => closeJourney(true)}
      />
    );
  } else if (activeStep.componentId) {
    content = (
      <GeneratedJourneyStep
        session={session}
        step={activeStep}
        draft={draft}
        open={presented}
        onAdvance={advance}
        onClose={back}
      />
    );
  } else {
    content = (
      <JourneyForm
        key={activeStep.id}
        step={activeStep}
        initialValues={draft}
        open={presented}
        onSubmit={advance}
        onClose={back}
      />
    );
  }

  return (
    <>
      {content}
      <JourneyDock
        session={session}
        step={activeStep}
        historyLength={history.length}
        onBack={back}
        onCancel={() => closeJourney(activeStep.type === "SUCCESS")}
      />
    </>
  );
}
