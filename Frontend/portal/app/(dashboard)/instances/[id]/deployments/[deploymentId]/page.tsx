"use client";

import { useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { DeploymentResponse, DeploymentEventPayload } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";

const STATUS_STYLE: Record<string, string> = {
  QUEUED: "bg-gray-100 text-gray-600",
  CLONING: "bg-amber-100 text-amber-700",
  UPLOADING: "bg-amber-100 text-amber-700",
  VALIDATING: "bg-amber-100 text-amber-700",
  BUILDING: "bg-amber-100 text-amber-700",
  SWAPPING: "bg-amber-100 text-amber-700",
  HEALTH_CHECKING: "bg-amber-100 text-amber-700",
  ROUTING: "bg-amber-100 text-amber-700",
  SUCCEEDED: "bg-[#03C75A]/10 text-[#03C75A]",
  FAILED: "bg-red-100 text-red-700",
  ROLLING_BACK: "bg-red-100 text-red-700",
  ROLLED_BACK: "bg-gray-100 text-gray-600",
};

const IN_PROGRESS_STATUSES = new Set([
  "QUEUED", "CLONING", "UPLOADING", "VALIDATING", "BUILDING", "SWAPPING", "HEALTH_CHECKING", "ROUTING", "ROLLING_BACK",
]);

const EVENT_TYPE_STYLE: Record<string, string> = {
  STAGE_CHANGE: "text-blue-600",
  BUILD_LOG: "text-gray-400",
  ERROR: "text-red-500",
  DONE: "text-[#03C75A]",
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
    setConnected(true);
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
      () => setConnected(false)
    );
    return () => {
      stopRef.current?.();
    };
  }, [accessToken, vmId, deploymentId]);

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight });
  }, [events]);

  if (!accessToken || loading) return <PageLoader />;
  if (!deployment) {
    return (
      <div className="p-6">
        <p className="text-sm text-red-600">{error ?? "배포를 찾을 수 없습니다."}</p>
      </div>
    );
  }

  const inProgress = IN_PROGRESS_STATUSES.has(deployment.status);

  return (
    <div className="flex flex-col h-[calc(100vh-120px)]">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-3 min-w-0">
          <button onClick={() => router.back()} className="p-2 hover:bg-gray-100 rounded-lg transition-colors shrink-0" aria-label="뒤로가기">
            <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="text-lg font-medium text-gray-900 shrink-0">배포 상세</h1>
          <span className={`text-xs font-medium px-2.5 py-1 rounded-md ${STATUS_STYLE[deployment.status] ?? "bg-gray-100 text-gray-600"}`}>
            {deployment.status}
          </span>
          {inProgress && (
            <span className="flex items-center gap-1 text-[11px] text-gray-400">
              <span className={`w-1.5 h-1.5 rounded-full ${connected ? "bg-[#03C75A] animate-pulse" : "bg-gray-300"}`} />
              {connected ? "실시간 연결됨" : "연결 끊김"}
            </span>
          )}
        </div>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md mb-3 text-sm">{error}</div>
      )}

      {deployment.errorMessage && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-3">
          <p className="text-sm font-medium text-red-800">오류</p>
          <p className="text-xs text-red-700 mt-0.5">{deployment.errorMessage}</p>
        </div>
      )}

      <div className="grid grid-cols-4 gap-3 mb-4 shrink-0">
        <div className="bg-gray-50 rounded-md p-3">
          <p className="text-xs text-gray-500 mb-1">방식</p>
          <p className="text-sm font-medium text-gray-900">{deployment.sourceType}</p>
        </div>
        <div className="bg-gray-50 rounded-md p-3">
          <p className="text-xs text-gray-500 mb-1">리비전</p>
          <p className="text-sm font-mono text-gray-900 truncate">{deployment.sourceRevision ?? "—"}</p>
        </div>
        <div className="bg-gray-50 rounded-md p-3">
          <p className="text-xs text-gray-500 mb-1">이전 배포</p>
          <p className="text-sm font-mono text-gray-900 truncate">{deployment.previousDeploymentId?.slice(0, 8) ?? "—"}</p>
        </div>
        <div className="bg-gray-50 rounded-md p-3">
          <p className="text-xs text-gray-500 mb-1">배포 완료</p>
          <p className="text-sm text-gray-900">{deployment.deployedAt ? formatDateTime(deployment.deployedAt) : "—"}</p>
        </div>
      </div>

      <div className="flex-1 bg-gray-900 rounded-lg p-3 overflow-auto" ref={logRef}>
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
    </div>
  );
}
