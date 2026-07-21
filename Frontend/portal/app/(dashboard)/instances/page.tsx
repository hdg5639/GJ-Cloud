"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import { useVmEvents } from "@/hooks/use-vm-events";
import type { VmResponse, VmStatusEvent, UsageResponse } from "@/lib/types";
import { SkeletonRow } from "@/components/ui/loader";
import { buttonClass } from "@/components/ui/button";
import { StatGrid, StatCard } from "@/components/ui/stat-card";
import { Panel } from "@/components/ui/panel";
import { SearchInput, Select } from "@/components/ui/field";
import { Table, Th, Td } from "@/components/ui/table";
import { Badge, StatusBadge, StatusDot } from "@/components/ui/badge";

const PLAN_SPEC: Record<string, { cores: number; memory: string }> = {
  FREE: { cores: 4, memory: "5GB" },
  PRO: { cores: 8, memory: "12GB" },
};

const STATUS_LABEL: Record<string, string> = {
  PENDING: "대기 중",
  CREATING: "생성 중",
  BOOTING: "부팅 중",
  RUNNING: "실행 중",
  STARTING: "시작 중",
  STOPPING: "중지 중",
  STOPPED: "중지됨",
  SUSPENDING: "일시정지 중",
  SUSPENDED: "일시정지됨",
  FAILED: "실패",
  DELETING: "삭제 중",
  DELETED: "삭제됨",
};

function isOnline(status: string) {
  return status === "RUNNING" || status === "STARTING" || status === "BOOTING" || status === "CREATING";
}

function relativeTime(iso: string) {
  const diff = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1) return "방금";
  if (m < 60) return `${m}분 전`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}시간 전`;
  return `${Math.floor(h / 24)}일 전`;
}

export default function InstancesPage() {
  const { accessToken, refresh } = useAuth();
  const [vms, setVms] = useState<VmResponse[]>([]);
  const [usage, setUsage] = useState<UsageResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("all");

  useEffect(() => {
    if (!accessToken) return;
    Promise.all([api.vm.list(accessToken), api.user.usage(accessToken)])
      .then(([vmData, usageData]) => {
        setVms(vmData);
        setUsage(usageData);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [accessToken]);

  const handleVmEvent = useCallback((event: VmStatusEvent) => {
    setVms((prev) =>
      prev.map((vm) =>
        vm.id === event.vmId
          ? { ...vm, status: event.status as VmResponse["status"], internalIp: event.internalIp ?? vm.internalIp }
          : vm
      )
    );
  }, []);

  useVmEvents(accessToken ?? "", handleVmEvent, !!accessToken && vms.length > 0, refresh);

  const activeVms = useMemo(() => vms.filter((v) => v.status !== "DELETED"), [vms]);
  const runningCount = activeVms.filter((v) => v.status === "RUNNING").length;
  const freeCount = activeVms.filter((v) => v.planType === "FREE").length;
  const proCount = activeVms.filter((v) => v.planType === "PRO").length;

  const filteredVms = useMemo(() => {
    const q = search.trim().toLowerCase();
    return activeVms.filter((vm) => {
      const matchesQuery =
        !q ||
        vm.name.toLowerCase().includes(q) ||
        (vm.internalIp ?? "").toLowerCase().includes(q) ||
        (vm.subdomain ?? "").toLowerCase().includes(q);
      const matchesStatus =
        statusFilter === "all" || (statusFilter === "running" ? vm.status === "RUNNING" : vm.status === "STOPPED");
      return matchesQuery && matchesStatus;
    });
  }, [activeVms, search, statusFilter]);

  return (
    <div className="mx-auto max-w-[1380px]">
      <header className="mb-[22px] flex flex-wrap items-center justify-between gap-4">
        <div>
          <span className="text-[11px] font-extrabold tracking-[.11em] text-muted-soft">COMPUTE</span>
          <h1 className="my-[5px] text-[29px] font-extrabold tracking-tight">인스턴스</h1>
          <p className="m-0 text-sm text-muted">가상 머신을 생성하고 상태, 자원, 네트워크를 관리합니다.</p>
        </div>
        <Link href="/instances/new" className={buttonClass({ variant: "primary" })}>
          ＋ 인스턴스 생성
        </Link>
      </header>

      <StatGrid cols={4}>
        <StatCard label="전체 인스턴스" value={activeVms.length} />
        <StatCard label="실행 중" value={runningCount} hint={`중지 ${activeVms.length - runningCount}`} />
        <StatCard
          label="FREE"
          value={
            <>
              {freeCount} <em className="text-base font-normal not-italic text-muted-soft">/ {usage?.maxFreeVmCount ?? "-"}</em>
            </>
          }
          hint={`${PLAN_SPEC.FREE.cores} vCPU · ${PLAN_SPEC.FREE.memory} RAM`}
        />
        <StatCard
          label="PRO"
          value={
            <>
              {proCount} <em className="text-base font-normal not-italic text-muted-soft">/ {usage?.maxProVmCount ?? "-"}</em>
            </>
          }
          hint={`${PLAN_SPEC.PRO.cores} vCPU · ${PLAN_SPEC.PRO.memory} RAM`}
        />
      </StatGrid>

      <Panel>
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line p-[15px]">
          <SearchInput
            className="w-full sm:w-[420px]"
            placeholder="이름, IP, 서브도메인으로 검색"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <div className="flex gap-2.5">
            <Select className="w-[140px]" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
              <option value="all">전체 상태</option>
              <option value="running">실행 중</option>
              <option value="stopped">중지됨</option>
            </Select>
          </div>
        </div>

        <Table>
          <thead>
            <tr>
              <Th>이름</Th>
              <Th>상태</Th>
              <Th>플랜</Th>
              <Th>vCPU</Th>
              <Th>RAM</Th>
              <Th>디스크</Th>
              <Th>IP 주소</Th>
              <Th>생성일</Th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              [0, 1, 2].map((i) => <SkeletonRow key={i} cols={8} />)
            ) : filteredVms.length === 0 ? (
              <tr>
                <Td colSpan={8} className="py-16 text-center text-muted-soft">
                  {activeVms.length === 0 ? (
                    <div className="flex flex-col items-center gap-3">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img src="/gamjabox-symbol.svg" alt="" width={56} height={56} className="opacity-90" />
                      <span>
                        인스턴스가 없습니다.{" "}
                        <Link href="/instances/new" className="font-bold text-brand-strong">
                          첫 인스턴스 생성하기 →
                        </Link>
                      </span>
                    </div>
                  ) : (
                    "조건에 맞는 인스턴스가 없습니다."
                  )}
                </Td>
              </tr>
            ) : (
              filteredVms.map((vm) => {
                const spec = PLAN_SPEC[vm.planType] ?? PLAN_SPEC.FREE;
                return (
                  <tr key={vm.id}>
                    <Td>
                      <RowLinkToInstance vm={vm} />
                    </Td>
                    <Td>
                      <StatusBadge tone={isOnline(vm.status) ? "ok" : "off"}>
                        {STATUS_LABEL[vm.status] ?? vm.status}
                      </StatusBadge>
                    </Td>
                    <Td>{vm.planType === "PRO" ? <Badge>PRO</Badge> : "FREE"}</Td>
                    <Td>{spec.cores}</Td>
                    <Td>{spec.memory}</Td>
                    <Td>{vm.diskSizeGb} GB</Td>
                    <Td>{vm.internalIp ?? "—"}</Td>
                    <Td>{relativeTime(vm.createdAt)}</Td>
                  </tr>
                );
              })
            )}
          </tbody>
        </Table>
      </Panel>
    </div>
  );
}

function RowLinkToInstance({ vm }: { vm: VmResponse }) {
  return (
    <Link href={`/instances/${vm.id}`} className="flex items-center gap-2.5">
      <StatusDot off={!isOnline(vm.status)} />
      <span>
        <strong className="block font-bold">{vm.name}</strong>
        {vm.subdomain && <small className="mt-0.5 block text-muted-soft">{vm.subdomain}.gamjabox.cloud</small>}
      </span>
    </Link>
  );
}
