"use client";

import type { PollStatus } from "./flow/types";

export type FlowRunStatus = "RUNNING" | "SUCCESS" | "ERROR" | "CANCELLED" | "TIMEOUT" | "STOPPED";

export interface FlowRunView {
  flowId: string;
  title: string;
  status: FlowRunStatus;
  stepStatuses: Record<string, PollStatus>;
  message: string | null;
}

const STATUS_LABEL: Record<FlowRunStatus, string> = {
  RUNNING: "작업 실행 중",
  SUCCESS: "작업 완료",
  ERROR: "작업 실패",
  CANCELLED: "작업 취소됨",
  TIMEOUT: "대기 시간 초과",
  STOPPED: "조건에 따라 중단됨",
};

const POLL_LABEL: Record<PollStatus, string> = {
  PENDING: "대기",
  RUNNING: "확인 중",
  SUCCESS: "완료",
  FAILURE: "실패",
  TIMEOUT: "시간 초과",
  CANCELLED: "취소",
};

export function AsyncFlowProgressModal({
  run,
  onCancel,
  onClose,
}: {
  run: FlowRunView | null;
  onCancel: () => void;
  onClose: () => void;
}) {
  if (!run) return null;
  const finished = run.status !== "RUNNING";

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/65 p-4">
      <div className="w-full max-w-md rounded-panel border border-line-strong bg-panel p-5 shadow-2xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-[10px] font-extrabold uppercase tracking-[0.16em] text-brand-strong">Workflow</p>
            <h3 className="mt-1 text-base font-extrabold">{run.title}</h3>
            <p className={`mt-1 text-xs ${run.status === "ERROR" || run.status === "TIMEOUT" ? "text-danger" : "text-muted"}`}>
              {STATUS_LABEL[run.status]}
            </p>
          </div>
          {run.status === "RUNNING" && (
            <span className="inline-flex size-5 animate-spin rounded-full border-2 border-line-strong border-t-brand" />
          )}
        </div>

        {Object.keys(run.stepStatuses).length > 0 && (
          <div className="mt-4 space-y-2 rounded-md border border-line bg-black/10 p-3">
            {Object.entries(run.stepStatuses).map(([stepId, status]) => (
              <div key={stepId} className="flex items-center justify-between gap-3 text-xs">
                <span className="truncate font-mono text-muted">{stepId}</span>
                <span className={status === "FAILURE" || status === "TIMEOUT" ? "font-bold text-danger" : "font-bold text-foreground"}>
                  {POLL_LABEL[status]}
                </span>
              </div>
            ))}
          </div>
        )}

        {run.message && (
          <p className={`mt-4 rounded-md border p-3 text-xs ${run.status === "ERROR" || run.status === "TIMEOUT" ? "border-danger-soft bg-danger/10 text-danger" : "border-line bg-white/[0.03] text-muted"}`}>
            {run.message}
          </p>
        )}

        <div className="mt-5 flex justify-end gap-2">
          {!finished ? (
            <button type="button" className="rounded-md border border-line-strong px-3 py-2 text-xs font-bold text-muted hover:text-foreground" onClick={onCancel}>
              취소
            </button>
          ) : (
            <button type="button" className="rounded-md bg-brand px-3 py-2 text-xs font-extrabold text-black" onClick={onClose}>
              확인
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
