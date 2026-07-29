"use client";

import { useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { PortResponse } from "@/lib/api-client";
import type { DeploymentResponse, DeploymentEventPayload } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";
import { StatusBadge } from "@/components/ui/badge";
import { StatGrid, StatCard } from "@/components/ui/stat-card";
import { Modal } from "@/components/ui/modal";
import { Button } from "@/components/ui/button";
import { InstanceSectionNav } from "@/components/ui/instance-section-nav";

const STATUS_TONE: Record<string, "ok" | "off"> = {
  QUEUED: "off",
  CLONING: "off",
  UPLOADING: "off",
  VALIDATING: "off",
  BUILDING: "off",
  SWAPPING: "off",
  HEALTH_CHECKING: "off",
  ROUTING: "off",
  SUCCEEDED: "ok",
  FAILED: "off",
  ROLLING_BACK: "off",
  ROLLED_BACK: "off",
  STOPPING: "off",
  STOPPED: "off",
};

const IN_PROGRESS_STATUSES = new Set([
  "QUEUED", "CLONING", "UPLOADING", "VALIDATING", "BUILDING", "SWAPPING", "HEALTH_CHECKING", "ROUTING", "ROLLING_BACK", "STOPPING",
]);

const EVENT_TYPE_STYLE: Record<string, string> = {
  STAGE_CHANGE: "text-[#7ab3f5]",
  BUILD_LOG: "text-gray-400",
  ERROR: "text-[#f28b8b]",
  DONE: "text-[#4fd88a]",
};

function formatDateTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

export default function DeploymentDetailPage() {
  const params = useParams();
  const router = useRouter();
  const vmId = params.id as string;
  const deploymentId = params.deploymentId as string;
  const { accessToken } = useAuth();

  const [deployment, setDeployment] = useState<DeploymentResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [events, setEvents] = useState<DeploymentEventPayload[]>([]);
  const [connected, setConnected] = useState(false);
  const [rollingBack, setRollingBack] = useState(false);
  const [showTeardown, setShowTeardown] = useState(false);
  const [teardownPorts, setTeardownPorts] = useState<PortResponse[]>([]);
  const [loadingTeardownPorts, setLoadingTeardownPorts] = useState(false);
  const [selectedRemoveIds, setSelectedRemoveIds] = useState<Set<string>>(new Set());
  const [tearingDown, setTearingDown] = useState(false);
  const logRef = useRef<HTMLDivElement>(null);
  const stopRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    api.ops.deployments
      .get(accessToken, vmId, deploymentId)
      .then(setDeployment)
      .catch((err) => setError(err instanceof Error ? err.message : "배포 정보를 불러올 수 없습니다."))
      .finally(() => setLoading(false));
  }, [accessToken, vmId, deploymentId]);

  useEffect(() => {
    if (!accessToken) return;
    stopRef.current = api.ops.deployments.streamEvents(
      accessToken,
      vmId,
      deploymentId,
      (event) => {
        setEvents((prev) => [...prev, event]);
        if (event.eventType === "STAGE_CHANGE" || event.eventType === "DONE") {
          api.ops.deployments.get(accessToken, vmId, deploymentId).then(setDeployment).catch(() => {});
        }
        if (event.eventType === "DONE") setConnected(false);
      },
      () => setConnected(false),
      () => setConnected(true)
    );
    return () => {
      stopRef.current?.();
    };
  }, [accessToken, vmId, deploymentId]);

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight });
  }, [events]);

  // SEC-010: 복호화된 스펙(.env 등 시크릿 포함 가능)을 sessionStorage에 그대로 담지 않고
  // deploymentId만 전달 — 목록 페이지가 진입 시 그 ID로 스펙을 직접 새로 조회한다(handleRetry).
  function handleRetry() {
    sessionStorage.setItem(`retryDeployment:${vmId}`, deploymentId);
    router.push(`/instances/${vmId}/deployments`);
  }

  async function handleRollback() {
    if (!accessToken) return;
    if (!confirm("이 배포로 롤백하시겠습니까? 재빌드 없이 이 시점의 이미지로 컨테이너만 재기동합니다.")) return;
    setRollingBack(true);
    setError(null);
    try {
      const rollback = await api.ops.deployments.rollback(accessToken, vmId, deploymentId);
      router.push(`/instances/${vmId}/deployments/${rollback.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "롤백 요청에 실패했습니다.");
      setRollingBack(false);
    }
  }

  // 배포가 만든 포트(deploymentId 있는 것)만 선택 삭제 후보로 보여줌 — 사용자가 직접 추가한 포트는 안 건드림
  async function openTeardownModal() {
    setShowTeardown(true);
    setSelectedRemoveIds(new Set());
    if (!accessToken) return;
    setLoadingTeardownPorts(true);
    try {
      const allPorts = await api.vm.getPorts(accessToken, vmId);
      setTeardownPorts(allPorts.filter((p) => p.deploymentId != null));
    } catch {
      setTeardownPorts([]);
    } finally {
      setLoadingTeardownPorts(false);
    }
  }

  function toggleSelectAllPorts() {
    setSelectedRemoveIds((prev) =>
      prev.size === teardownPorts.length ? new Set() : new Set(teardownPorts.map((p) => p.id))
    );
  }

  function togglePortSelection(portId: string) {
    setSelectedRemoveIds((prev) => {
      const next = new Set(prev);
      if (next.has(portId)) {
        next.delete(portId);
      } else {
        next.add(portId);
      }
      return next;
    });
  }

  async function handleTeardownConfirm() {
    if (!accessToken) return;
    setTearingDown(true);
    setError(null);
    try {
      const removeRouteNicknames = teardownPorts
        .filter((p) => selectedRemoveIds.has(p.id))
        .map((p) => p.nickname);
      const updated = await api.ops.deployments.teardown(accessToken, vmId, deploymentId, removeRouteNicknames);
      setDeployment(updated);
      setShowTeardown(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "배포 내리기에 실패했습니다.");
    } finally {
      setTearingDown(false);
    }
  }

  if (!accessToken || loading) {
    return (
      <div className="mx-auto max-w-[1380px]">
        <InstanceSectionNav vmId={vmId} />
        <PageLoader />
      </div>
    );
  }
  if (!deployment) {
    return (
      <div className="mx-auto max-w-[1380px]">
        <InstanceSectionNav vmId={vmId} />
        <p className="text-sm text-danger">{error ?? "배포를 찾을 수 없습니다."}</p>
      </div>
    );
  }

  const inProgress = IN_PROGRESS_STATUSES.has(deployment.status);

  return (
    <div className="mx-auto max-w-[1380px]">
      <InstanceSectionNav vmId={vmId} />
      <div className="flex min-h-[640px] flex-col lg:h-[calc(100vh-185px)]">
      <div className="mb-3 flex items-center rounded-panel border border-line bg-panel">
        <div className="flex h-10 min-w-0 shrink items-center gap-2.5 pl-4 pr-3.5">
          <button onClick={() => router.back()} className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-muted-soft transition-colors hover:bg-white/[0.06] hover:text-muted" aria-label="뒤로가기">
            <svg className="w-[15px] h-[15px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="text-[15px] font-bold whitespace-nowrap shrink-0">배포 상세</h1>
          <StatusBadge tone={STATUS_TONE[deployment.status] ?? "off"} className="whitespace-nowrap">{deployment.status}</StatusBadge>
          {inProgress && (
            <span className="flex shrink-0 items-center gap-1 text-[11px] text-muted-soft whitespace-nowrap">
              <span className={`w-1.5 h-1.5 rounded-full ${connected ? "bg-brand animate-pulse" : "bg-line-strong"}`} />
              {connected ? "실시간 연결됨" : "연결 끊김"}
            </span>
          )}
        </div>
        <div className="ml-auto flex h-10 shrink-0 items-center">
          {deployment.status === "FAILED" && (
            <button onClick={handleRetry} className="flex h-10 shrink-0 items-center gap-1.5 whitespace-nowrap px-3.5 text-sm text-muted transition-colors hover:bg-white/[0.06] disabled:opacity-50 rounded-r-panel">
              재시도 / 수정 후 재배포
            </button>
          )}
          {deployment.status === "SUCCEEDED" && (
            <button onClick={handleRollback} disabled={rollingBack} className="flex h-10 shrink-0 items-center gap-1.5 whitespace-nowrap px-3.5 text-sm text-muted transition-colors hover:bg-white/[0.06] disabled:opacity-50 border-r border-line">
              {rollingBack ? "롤백 요청 중..." : "이 배포로 롤백"}
            </button>
          )}
          {deployment.status === "SUCCEEDED" && (
            <button onClick={openTeardownModal} className="flex h-10 shrink-0 items-center gap-1.5 whitespace-nowrap px-3.5 text-sm text-danger transition-colors hover:bg-danger/10 rounded-r-panel">
              내리기
            </button>
          )}
        </div>
      </div>

      {error && (
        <div className="bg-danger/10 border border-danger-soft text-danger px-4 py-3 rounded-md mb-3 text-sm">{error}</div>
      )}

      {deployment.errorMessage && (
        <div className="rounded-panel border border-danger-soft bg-danger/10 p-4 mb-3">
          <p className="text-sm font-bold text-danger">오류</p>
          <p className="mt-0.5 text-xs text-danger/80">{deployment.errorMessage}</p>
        </div>
      )}

      <StatGrid cols={4} className="mb-4 shrink-0">
        <StatCard compact label="방식" value={deployment.sourceType} />
        <StatCard compact label="리비전" value={<span className="font-mono truncate block">{deployment.sourceRevision ?? "—"}</span>} />
        <StatCard compact label="이전 배포" value={<span className="font-mono truncate block">{deployment.previousDeploymentId?.slice(0, 8) ?? "—"}</span>} />
        <StatCard compact label="배포 완료" value={deployment.deployedAt ? formatDateTime(deployment.deployedAt) : "—"} />
      </StatGrid>

      <div className="flex-1 bg-[#121814] rounded-panel p-3 overflow-auto" ref={logRef}>
        {events.length === 0 ? (
          <p className="text-xs text-gray-500 text-center py-10">
            {connected ? "이벤트를 기다리는 중..." : "표시할 이벤트가 없습니다."}
          </p>
        ) : (
          <div className="space-y-1">
            {events.map((ev) => (
              <div key={ev.sequence} className="text-[11px] font-mono flex gap-2">
                <span className="text-gray-500 shrink-0">{formatDateTime(ev.createdAt)}</span>
                <span className={`shrink-0 ${EVENT_TYPE_STYLE[ev.eventType] ?? "text-gray-300"}`}>[{ev.eventType}]</span>
                <span className="text-gray-200 whitespace-pre-wrap break-all">{ev.message}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      <Modal open={showTeardown} onClose={() => !tearingDown && setShowTeardown(false)}>
        <div className="mx-auto w-full max-w-[480px] rounded-panel border border-line bg-panel p-5">
          <h2 className="mb-1 text-base font-bold text-danger">배포 내리기</h2>
          <p className="mb-4 text-xs text-muted">
            이 VM에서 실행 중인 컨테이너를 중지/제거합니다. 아래에서 선택한 노출 포트는 함께 삭제되고,
            선택하지 않은 포트는 그대로 유지됩니다(컨테이너가 없으니 접속은 안 됨).
          </p>

          {loadingTeardownPorts ? (
            <p className="mb-4 text-xs text-muted-soft">포트 목록 불러오는 중...</p>
          ) : teardownPorts.length === 0 ? (
            <p className="mb-4 text-xs text-muted-soft">이 배포로 만들어진 노출 포트가 없습니다.</p>
          ) : (
            <div className="mb-4 rounded-[10px] border border-line-strong">
              <label className="flex items-center gap-2 border-b border-line px-3 py-2 text-xs font-bold text-muted">
                <input
                  type="checkbox"
                  checked={selectedRemoveIds.size === teardownPorts.length}
                  onChange={toggleSelectAllPorts}
                />
                전체 선택 (선택한 포트만 함께 삭제)
              </label>
              <div className="max-h-[220px] overflow-y-auto">
                {teardownPorts.map((p) => (
                  <label key={p.id} className="flex items-center gap-2 px-3 py-2 text-xs text-foreground hover:bg-white/[0.03]">
                    <input
                      type="checkbox"
                      checked={selectedRemoveIds.has(p.id)}
                      onChange={() => togglePortSelection(p.id)}
                    />
                    <span className="font-mono">{p.fullDomain}</span>
                    <span className="text-muted-soft">:{p.port}</span>
                  </label>
                ))}
              </div>
            </div>
          )}

          <div className="flex justify-end gap-2">
            <Button size="small" onClick={() => setShowTeardown(false)} disabled={tearingDown}>
              취소
            </Button>
            <Button variant="danger-solid" size="small" onClick={handleTeardownConfirm} disabled={tearingDown}>
              {tearingDown ? "내리는 중..." : "내리기"}
            </Button>
          </div>
        </div>
      </Modal>
      </div>
    </div>
  );
}
