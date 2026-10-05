"use client";

import type { PreviewCompiledScenario, PreviewScenarioDiagnostic, PreviewScenarioStageExecution } from "@/lib/types";
import type { PreviewCapability } from "../types";

export function UserFlowTrace({ scenario, capabilities, diagnostics, executions, onInspect }: {
  scenario: PreviewCompiledScenario | null;
  capabilities: PreviewCapability[];
  diagnostics: PreviewScenarioDiagnostic[];
  executions: Record<string, PreviewScenarioStageExecution>;
  onInspect: () => void;
}) {
  const issues = [...(scenario?.diagnostics ?? []), ...diagnostics.filter((item) =>
    item.scenarioId === null || item.scenarioId === scenario?.id
  )].filter((item, index, all) => item.status !== "SUPPORTED"
    && all.findIndex((other) => other.message === item.message && other.stageId === item.stageId) === index);
  return (
    <aside className="min-w-0 border-t border-[var(--px-line)] pt-5 lg:border-l lg:border-t-0 lg:pl-5 lg:pt-0" aria-label="사용자 흐름과 API 연결">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-sm font-bold">이 흐름에서 확인할 것</h2>
        <button type="button" onClick={onInspect} className="shrink-0 text-xs font-bold text-[var(--px-accent)] underline underline-offset-4">요청·응답 보기</button>
      </div>
      {scenario ? <>
        <p className="mt-2 text-xs leading-5 text-[var(--px-muted)]">{scenario.actor} · {scenario.goal}</p>
        <p className="mt-2 text-[11px] text-[var(--px-muted)]">{scenario.status === "EXECUTABLE" ? "연결 검증 완료 · 실제 동작은 테스트로 확인" : scenario.status === "UNSUPPORTED" ? "현재 계약으로 실행할 수 없는 흐름" : "일부 연결은 확인이 필요한 흐름"}</p>
        <ol className="mt-4 space-y-0">
          {scenario.stages.map((stage, index) => {
            const capability = capabilities.find((item) => item.id === stage.capabilityId);
            const execution = executions[stage.id];
            const stageIssues = issues.filter((item) => item.stageId === stage.id);
            const status = execution?.status === "SUCCESS" ? "통과" : execution?.status === "FAILED" ? "실패"
              : execution?.status === "RUNNING" ? "호출 중" : stageIssues.length > 0 ? "연결 확인" : "미실행";
            return <li key={stage.id} className="border-b border-[var(--px-line)] py-3">
              <div className="flex items-start gap-2 text-xs">
                <span className="shrink-0 text-[var(--px-subtle)]">{index + 1}.</span>
                <strong className="min-w-0 flex-1 break-words font-medium">{stage.intent}</strong>
                <span className={execution?.status === "FAILED" ? "shrink-0 text-[var(--px-danger)]" : "shrink-0 text-[var(--px-muted)]"}>{status}</span>
              </div>
              <p className="mt-1 break-all pl-4 font-mono text-[10px] leading-4 text-[var(--px-muted)]">{capability ? `${capability.method} ${capability.path}` : "사용자 입력 / 로컬 단계"}</p>
              {execution?.error && <p className="mt-1 break-words pl-4 text-xs text-[var(--px-danger)]">{execution.error}</p>}
            </li>;
          })}
        </ol>
      </> : <p className="mt-3 text-xs leading-5 text-[var(--px-muted)]">이 화면은 목록 조회용입니다. 사용자 흐름을 선택하면 단계별 연결과 실행 결과를 확인할 수 있습니다.</p>}
      {issues.length > 0 && <div className="mt-5" role="status">
        <h3 className="text-xs font-bold">부족하거나 확인할 부분</h3>
        <ul className="mt-2 space-y-2 text-xs leading-5 text-[var(--px-muted)]">{issues.map((issue, index) => <li key={index} className="break-words">{issue.message}</li>)}</ul>
        <p className="mt-2 text-[10px] leading-4 text-[var(--px-subtle)]">OpenAPI에 보이지 않는 기능은 구현 누락으로 단정하지 않습니다. 서버 계약과 실제 동작을 함께 확인하세요.</p>
      </div>}
    </aside>
  );
}
