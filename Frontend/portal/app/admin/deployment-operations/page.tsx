"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type {
  AdminDeploymentOperationsResponse,
  AdminDeploymentTargetOperation,
  DeploymentEventPayload,
  OrphanReconcileResult,
} from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Panel } from "@/components/ui/panel";
import { PageLoader } from "@/components/ui/loader";
import { Modal } from "@/components/ui/modal";
import { StatCard, StatGrid } from "@/components/ui/stat-card";
import { cn } from "@/components/ui/cn";
import { formatDeploymentEventDetail } from "@/lib/deployment-log";

type TargetFilter = "ALL" | "AUTO" | "ORPHANED";

function dateTime(value: string | null): string {
  return value ? new Date(value).toLocaleString("ko-KR") : "-";
}

function shortId(value: string | null): string {
  return value ? value.slice(0, 8) : "-";
}

function targetTone(target: AdminDeploymentTargetOperation): string {
  if (target.lifecycleStatus === "ORPHANED") return "bg-[#fff3d9] text-[#7a5512]";
  if (target.lifecycleStatus === "ACTIVE") return "bg-[#eaf7ee] text-[#327548]";
  return "bg-[#eef1ef] text-[#5d685f]";
}

function deploymentTone(status: string): string {
  if (status === "SUCCEEDED") return "bg-[#eaf7ee] text-[#327548]";
  if (status === "FAILED") return "bg-[#fdf1f1] text-danger";
  if (["STOPPED", "ROLLED_BACK"].includes(status)) return "bg-[#eef1ef] text-[#5d685f]";
  return "bg-[#fff3d9] text-[#7a5512]";
}

export default function DeploymentOperationsPage() {
  const { accessToken } = useAuth();
  const [data, setData] = useState<AdminDeploymentOperationsResponse | null>(null);
  const [filter, setFilter] = useState<TargetFilter>("ALL");
  const [loading, setLoading] = useState(true);
  const [reconciling, setReconciling] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [logDeploymentId, setLogDeploymentId] = useState<string | null>(null);
  const [logEvents, setLogEvents] = useState<DeploymentEventPayload[]>([]);
  const [logsLoading, setLogsLoading] = useState(false);

  const load = useCallback(async () => {
    if (!accessToken) return;
    try {
      setData(await api.admin.deploymentOperations.get(accessToken));
      setError(null);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "배포 운영 현황을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => {
    const initialTimer = setTimeout(() => void load(), 0);
    const refreshTimer = setInterval(() => void load(), 30000);
    return () => {
      clearTimeout(initialTimer);
      clearInterval(refreshTimer);
    };
  }, [load]);

  const targets = useMemo(() => {
    const source = data?.targets ?? [];
    if (filter === "AUTO") return source.filter((target) => target.autoDeployEnabled && target.lifecycleStatus === "ACTIVE");
    if (filter === "ORPHANED") return source.filter((target) => target.lifecycleStatus === "ORPHANED");
    return source;
  }, [data?.targets, filter]);

  async function reconcile() {
    if (!accessToken) return;
    setReconciling(true);
    setError(null);
    try {
      const result: OrphanReconcileResult = await api.admin.deploymentOperations.reconcileOrphans(accessToken);
      setNotice(`검사 ${result.scanned}건 · 유실 ${result.missing}건 · 완전 삭제 ${result.hardDeleted}건 · 격리 ${result.quarantined}건 · 오류 ${result.errors}건`);
      await load();
    } catch (reconcileError) {
      setError(reconcileError instanceof Error ? reconcileError.message : "고아 데이터 검사에 실패했습니다.");
    } finally {
      setReconciling(false);
    }
  }

  async function openDeploymentLogs(deploymentId: string) {
    if (!accessToken) return;
    setLogDeploymentId(deploymentId);
    setLogEvents([]);
    setLogsLoading(true);
    try {
      setLogEvents(await api.admin.deploymentOperations.getEvents(accessToken, deploymentId));
    } catch (logError) {
      setError(logError instanceof Error ? logError.message : "배포 로그를 불러오지 못했습니다.");
    } finally {
      setLogsLoading(false);
    }
  }

  if (!accessToken || (loading && !data)) return <PageLoader label="배포 운영 현황 확인 중" />;

  return (
    <div className="mx-auto max-w-[1500px]">
      <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <span className="text-[10px] font-extrabold tracking-[.12em] text-muted-soft">DEPLOYMENT OPERATIONS</span>
          <h1 className="mt-1 text-xl font-extrabold">전체 배포 운영</h1>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">사용자별 배포 대상과 자동 재배포, 전체 이벤트 로그를 확인합니다. VM 정본이 사라진 대상은 배포·프리뷰·회귀 데이터와 실행 락 유무에 따라 완전 삭제하거나 격리합니다.</p>
        </div>
        <Button variant="primary" disabled={reconciling} onClick={reconcile}>
          {reconciling ? "고아 데이터 검사 중..." : "지금 고아 데이터 검사"}
        </Button>
      </header>

      {error && <p className="mb-4 rounded-xl border border-danger/20 bg-[#fdf1f1] p-3 text-xs text-danger">{error}</p>}
      {notice && <p className="mb-4 rounded-xl border border-[#b9dbc3] bg-[#eef8f1] p-3 text-xs font-bold text-[#327548]">{notice}</p>}

      {data && (
        <>
          <StatGrid cols={5} className="mb-6">
            <StatCard compact label="전체 대상" value={data.summary.totalTargets} />
            <StatCard compact label="활성 대상" value={data.summary.activeTargets} />
            <StatCard compact label="자동 재배포" value={data.summary.activeAutoDeployments} />
            <StatCard compact label="격리 대상" value={<span className={data.summary.orphanedTargets ? "text-[#a16d10]" : undefined}>{data.summary.orphanedTargets}</span>} />
            <StatCard compact label="최근 실패" value={<span className={data.summary.recentFailedDeployments ? "text-danger" : undefined}>{data.summary.recentFailedDeployments}</span>} />
          </StatGrid>

          <Panel className="mb-6 overflow-hidden">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line p-4">
              <div><h2 className="text-sm font-extrabold">배포 대상</h2><p className="mt-1 text-xs text-muted">최근 갱신 순 최대 200개</p></div>
              <div className="flex rounded-lg border border-line bg-background p-1">
                {(["ALL", "AUTO", "ORPHANED"] as const).map((value) => (
                  <button key={value} type="button" onClick={() => setFilter(value)} className={cn("rounded-md px-3 py-1.5 text-[11px] font-extrabold", filter === value ? "bg-[#e9efeb] text-foreground" : "text-muted")}>
                    {value === "ALL" ? "전체" : value === "AUTO" ? "자동 재배포" : "격리"}
                  </button>
                ))}
              </div>
            </div>
            <div className="overflow-x-auto">
              <table className="min-w-[1120px] w-full text-left text-xs">
                <thead className="bg-[#f5f7f5] text-[10px] uppercase tracking-wide text-muted-soft"><tr><th className="px-4 py-3">상태</th><th className="px-4 py-3">대상 / 저장소</th><th className="px-4 py-3">소유자</th><th className="px-4 py-3">VM</th><th className="px-4 py-3">자동 배포</th><th className="px-4 py-3">Revision</th><th className="px-4 py-3">갱신</th></tr></thead>
                <tbody className="divide-y divide-line">
                  {targets.map((target) => (
                    <tr key={target.id} className="align-top">
                      <td className="px-4 py-3"><span className={cn("inline-flex rounded-md px-2 py-1 text-[10px] font-extrabold", targetTone(target))}>{target.lifecycleStatus}</span>{target.orphanReason && <p className="mt-1.5 text-[10px] text-[#8a641d]">{target.orphanReason}</p>}</td>
                      <td className="max-w-[280px] px-4 py-3"><p className="font-extrabold">{target.name}</p><p className="mt-1 truncate text-muted">{target.repository} · {target.branch}</p></td>
                      <td className="px-4 py-3"><p>{target.ownerEmail}</p><p className="mt-1 font-mono text-[10px] text-muted-soft">{shortId(target.ownerUserId)}</p></td>
                      <td className="px-4 py-3 font-mono text-[10px]">{shortId(target.vmId)}</td>
                      <td className="px-4 py-3 font-bold">{target.autoDeployEnabled ? "ON" : "OFF"}</td>
                      <td className="px-4 py-3 font-mono text-[10px]"><p>요청 {shortId(target.latestRequestedRevision)}</p><p className="mt-1 text-muted">배포 {shortId(target.latestDeployedRevision)}</p></td>
                      <td className="whitespace-nowrap px-4 py-3 text-muted">{dateTime(target.updatedAt)}</td>
                    </tr>
                  ))}
                  {targets.length === 0 && <tr><td colSpan={7} className="px-4 py-10 text-center text-muted">조건에 맞는 배포 대상이 없습니다.</td></tr>}
                </tbody>
              </table>
            </div>
          </Panel>

          <Panel className="mb-6 overflow-hidden">
            <div className="border-b border-line p-4"><h2 className="text-sm font-extrabold">최근 배포 로그</h2><p className="mt-1 text-xs text-muted">전체 사용자 최근 실행 100건</p></div>
            <div className="overflow-x-auto">
              <table className="min-w-[1080px] w-full text-left text-xs">
                <thead className="bg-[#f5f7f5] text-[10px] uppercase tracking-wide text-muted-soft"><tr><th className="px-4 py-3">상태</th><th className="px-4 py-3">배포</th><th className="px-4 py-3">트리거</th><th className="px-4 py-3">최근 로그</th><th className="px-4 py-3">오류</th><th className="px-4 py-3">시각</th><th className="px-4 py-3">전체 로그</th></tr></thead>
                <tbody className="divide-y divide-line">
                  {data.recentDeployments.map((deployment) => (
                    <tr key={deployment.id} className="align-top">
                      <td className="px-4 py-3"><span className={cn("inline-flex rounded-md px-2 py-1 text-[10px] font-extrabold", deploymentTone(deployment.status))}>{deployment.status}</span></td>
                      <td className="px-4 py-3 font-mono text-[10px]"><p>{shortId(deployment.id)}</p><p className="mt-1 text-muted">VM {shortId(deployment.vmId)} · Target {shortId(deployment.deploymentTargetId)}</p></td>
                      <td className="px-4 py-3"><p className="font-bold">{deployment.triggerType}</p><p className="mt-1 text-[10px] text-muted">{deployment.sourceType} · {shortId(deployment.revision)}</p></td>
                      <td className="max-w-[300px] px-4 py-3 text-muted">{deployment.lastEvent ?? "-"}</td>
                      <td className="max-w-[300px] px-4 py-3 text-danger">{deployment.errorMessage ?? "-"}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-muted">{dateTime(deployment.createdAt)}</td>
                      <td className="px-4 py-3"><Button size="small" onClick={() => void openDeploymentLogs(deployment.id)}>로그 보기</Button></td>
                    </tr>
                  ))}
                  {data.recentDeployments.length === 0 && <tr><td colSpan={7} className="px-4 py-10 text-center text-muted">배포 이력이 없습니다.</td></tr>}
                </tbody>
              </table>
            </div>
          </Panel>

          <Panel className="overflow-hidden">
            <div className="border-b border-line p-4"><h2 className="text-sm font-extrabold">고아 데이터 정리 이력</h2><p className="mt-1 text-xs text-muted">완전 삭제 후에도 정리 근거는 감사 이벤트로 보존합니다.</p></div>
            <div className="overflow-x-auto">
              <table className="min-w-[900px] w-full text-left text-xs">
                <thead className="bg-[#f5f7f5] text-[10px] uppercase tracking-wide text-muted-soft"><tr><th className="px-4 py-3">처리</th><th className="px-4 py-3">대상</th><th className="px-4 py-3">소유자</th><th className="px-4 py-3">판정 근거</th><th className="px-4 py-3">관련 데이터</th><th className="px-4 py-3">시각</th></tr></thead>
                <tbody className="divide-y divide-line">
                  {data.cleanupEvents.map((event) => (
                    <tr key={event.id}>
                      <td className="px-4 py-3"><span className={cn("inline-flex rounded-md px-2 py-1 text-[10px] font-extrabold", event.action === "HARD_DELETED" ? "bg-[#fdf1f1] text-danger" : "bg-[#fff3d9] text-[#7a5512]")}>{event.action}</span></td>
                      <td className="px-4 py-3"><p className="font-extrabold">{event.targetName}</p><p className="mt-1 font-mono text-[10px] text-muted">{shortId(event.deploymentTargetId)} · VM {shortId(event.vmId)}</p></td>
                      <td className="px-4 py-3">{event.ownerEmail}</td>
                      <td className="px-4 py-3 font-bold">{event.reason}</td>
                      <td className="px-4 py-3">{event.hadRelatedData ? "보존됨" : "없음"}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-muted">{dateTime(event.createdAt)}</td>
                    </tr>
                  ))}
                  {data.cleanupEvents.length === 0 && <tr><td colSpan={6} className="px-4 py-10 text-center text-muted">아직 자동 정리 이력이 없습니다.</td></tr>}
                </tbody>
              </table>
            </div>
          </Panel>

          <Modal open={logDeploymentId !== null} onClose={() => setLogDeploymentId(null)}>
            <section className="flex max-h-[min(760px,calc(100dvh-32px))] w-[min(920px,calc(100vw-32px))] min-h-0 flex-col overflow-hidden rounded-2xl border border-line bg-panel shadow-2xl">
              <header className="flex items-center justify-between gap-4 border-b border-line px-5 py-4">
                <div><h2 className="text-sm font-extrabold">배포 이벤트 로그</h2><p className="mt-1 font-mono text-[10px] text-muted">{logDeploymentId} · 최근 최대 1,000건</p></div>
                <Button size="small" onClick={() => setLogDeploymentId(null)}>닫기</Button>
              </header>
              <div className="min-h-0 flex-1 overflow-y-auto bg-[#18201b] p-4 font-mono text-[11px] leading-5 text-[#e6eee8]">
                {logsLoading && <p className="text-[#aebbb1]">로그를 불러오는 중...</p>}
                {!logsLoading && logEvents.length === 0 && <p className="text-[#aebbb1]">저장된 이벤트 로그가 없습니다.</p>}
                {!logsLoading && logEvents.map((event) => {
                  const detail = formatDeploymentEventDetail(event.payload);
                  return (
                    <div key={event.sequence} className="grid grid-cols-[64px_130px_minmax(0,1fr)] gap-x-3 border-b border-white/5 py-1.5 last:border-0">
                      <span className="text-[#8fa095]">#{event.sequence}</span>
                      <span className={event.eventType === "ERROR" ? "text-[#ff9f9f]" : event.eventType === "DONE" ? "text-[#9fe3ad]" : "text-[#b7c5bb]"}>{event.eventType}</span>
                      <span className="whitespace-pre-wrap break-words">{event.message}</span>
                      {detail && (
                        <pre className="col-start-3 mt-1.5 max-w-full whitespace-pre-wrap break-words rounded-md border border-white/10 bg-black/20 px-3 py-2 text-[10px] leading-[1.55] text-[#cbd6ce]">
                          {detail}
                        </pre>
                      )}
                    </div>
                  );
                })}
              </div>
            </section>
          </Modal>
        </>
      )}
    </div>
  );
}
