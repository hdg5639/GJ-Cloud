"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { SystemWorkerResponse } from "@/lib/types";
import { Panel } from "@/components/ui/panel";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/badge";
import { PageLoader } from "@/components/ui/loader";

const STATUS_COPY: Record<string, string> = {
  NOT_CONFIGURED: "구성 전", PROVISIONING: "프로비저닝 중", ACTIVE: "정상", DEGRADED: "점검 필요",
  STOPPED: "정지됨", MISSING: "VM 유실", ERROR: "오류",
};

export default function SystemInfrastructurePage() {
  const { accessToken } = useAuth();
  const [worker, setWorker] = useState<SystemWorkerResponse | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!accessToken) return;
    try { setWorker(await api.admin.systemWorker.get(accessToken)); setError(null); }
    catch (e) { setError(e instanceof Error ? e.message : "워커 상태를 불러오지 못했습니다."); }
  }, [accessToken]);

  useEffect(() => {
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);
  useEffect(() => {
    if (worker?.status !== "PROVISIONING") return;
    const timer = setInterval(() => void load(), 3000);
    return () => clearInterval(timer);
  }, [worker?.status, load]);

  async function run(action: "create" | "start" | "stop" | "reboot" | "reconcile" | "repair") {
    if (!accessToken) return;
    setBusy(action); setError(null);
    try {
      const next = action === "create" ? await api.admin.systemWorker.create(accessToken)
        : await api.admin.systemWorker.action(accessToken, action);
      setWorker(next);
    } catch (e) { setError(e instanceof Error ? e.message : "작업에 실패했습니다."); }
    finally { setBusy(null); }
  }

  if (!accessToken || !worker) return <PageLoader label="시스템 인프라 확인 중" />;
  const active = worker.status === "ACTIVE";
  const missing = worker.status === "MISSING";

  return (
    <div className="mx-auto max-w-[1050px]">
      <header className="mb-6">
        <span className="text-[10px] font-extrabold tracking-[.12em] text-muted-soft">SYSTEM INFRASTRUCTURE</span>
        <h1 className="mt-1 text-xl font-extrabold">Auto Preview Worker</h1>
        <p className="mt-2 text-sm text-muted">관리형 Auto Preview를 실행하는 플랫폼 전용 워커입니다. 일반 사용자 VM과 분리됩니다.</p>
      </header>

      <Panel className="overflow-hidden">
        <div className="flex flex-col gap-4 border-b border-line p-5 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <div className="grid h-11 w-11 place-items-center rounded-xl bg-[#eef7f0] text-lg text-[#4aa365]">◇</div>
            <div><h2 className="text-sm font-extrabold">{worker.name}</h2><p className="mt-1 text-xs text-muted-soft">Role · AUTO_PREVIEW</p></div>
          </div>
          <StatusBadge tone={active ? "ok" : "off"}>{STATUS_COPY[worker.status] ?? worker.status}</StatusBadge>
        </div>

        {worker.configured ? (
          <>
            <dl className="grid grid-cols-2 gap-px bg-line sm:grid-cols-4">
              {[["VMID", worker.vmId], ["Node", worker.node ?? "-"], ["내부 IP", worker.internalIp ?? "-"], ["사양", `${worker.cores} vCPU · ${worker.memoryMb / 1024}GB · ${worker.diskGb}GB`]].map(([label, value]) => (
                <div key={label} className="bg-panel p-4"><dt className="text-[10px] font-bold text-muted-soft">{label}</dt><dd className="mt-1.5 break-all font-mono text-xs font-bold">{value}</dd></div>
              ))}
            </dl>
            <div className="p-5">
              {worker.status === "PROVISIONING" && <p className="mb-4 rounded-xl bg-[#f3f7f4] p-3 text-xs font-bold text-muted">현재 단계 · {worker.provisioningStage ?? "PREPARING"}</p>}
              {missing && <p className="mb-4 rounded-xl border border-[#ead9a9] bg-[#fffaf0] p-3 text-xs font-bold text-[#79591e]">Proxmox에서 워커 VM을 찾을 수 없습니다. 동일한 등록 정보와 VMID로 새 VM을 구성할 수 있습니다.</p>}
              {worker.lastError && <p className="mb-4 rounded-xl bg-[#fdf1f1] p-3 text-xs leading-5 text-danger">{worker.lastError}</p>}
              <div className="flex flex-wrap gap-2">
                {missing && <Button variant="primary" disabled={!!busy} onClick={() => run("create")}>{busy === "create" ? "재생성 요청 중..." : "Worker 재생성"}</Button>}
                {worker.status === "STOPPED" && <Button variant="primary" disabled={!!busy} onClick={() => run("start")}>시작</Button>}
                {active && <Button disabled={!!busy} onClick={() => run("stop")}>정지</Button>}
                {(active || worker.status === "DEGRADED") && <Button disabled={!!busy} onClick={() => run("reboot")}>재부팅</Button>}
                <Button disabled={!!busy || worker.status === "PROVISIONING"} onClick={() => run("reconcile")}>Reconcile</Button>
                <Button disabled={!!busy || !worker.internalIp || !["ACTIVE", "DEGRADED", "ERROR"].includes(worker.status)} onClick={() => run("repair")}>Runtime Repair</Button>
                {active && <Link href="/system-infrastructure/console" className="inline-flex h-9 items-center rounded-md border border-line px-4 text-xs font-bold hover:bg-[#f3f6f4]">워커 콘솔</Link>}
              </div>
              <p className="mt-4 text-[11px] leading-5 text-muted-soft">ControlBox에서는 워커 삭제를 제공하지 않습니다. Proxmox에서 VM이 직접 삭제되어 MISSING으로 확인된 경우에만 동일한 등록 정보로 재생성할 수 있습니다.</p>
            </div>
          </>
        ) : (
          <div className="p-6">
            <p className="text-sm font-bold">Auto Preview Worker가 아직 없습니다.</p>
            <p className="mt-2 text-xs leading-5 text-muted">VMID 300 · 4 vCPU · 5GB RAM · 80GB 전용 VM을 생성하고 Docker Runtime과 격리 네트워크를 준비합니다.</p>
            <Button className="mt-5" variant="primary" disabled={!!busy} onClick={() => run("create")}>{busy === "create" ? "요청 중..." : "Worker 구성"}</Button>
          </div>
        )}
      </Panel>
      {error && <p className="mt-4 rounded-xl bg-[#fdf1f1] p-3 text-xs text-danger">{error}</p>}
    </div>
  );
}
