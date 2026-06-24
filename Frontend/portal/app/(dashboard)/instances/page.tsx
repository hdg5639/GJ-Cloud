"use client";

import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import { useVmEvents } from "@/hooks/use-vm-events";
import type { VmResponse, VmStatusEvent, UsageResponse } from "@/lib/types";
import { SkeletonCard } from "@/components/ui/loader";

const STATUS_STYLE: Record<string, string> = {
  PENDING: "bg-amber-100 text-amber-700",
  CREATING: "bg-amber-100 text-amber-700",
  BOOTING: "bg-amber-100 text-amber-700",
  RUNNING: "bg-[#03C75A]/10 text-[#03C75A]",
  STARTING: "bg-amber-100 text-amber-700",
  STOPPING: "bg-amber-100 text-amber-700",
  STOPPED: "bg-gray-100 text-gray-600",
  SUSPENDING: "bg-amber-100 text-amber-700",
  SUSPENDED: "bg-gray-100 text-gray-600",
  FAILED: "bg-red-100 text-red-700",
  DELETING: "bg-red-100 text-red-700",
  DELETED: "bg-gray-100 text-gray-400",
};

export default function InstancesPage() {
  const { accessToken, refresh } = useAuth();
  const [vms, setVms] = useState<VmResponse[]>([]);
  const [usage, setUsage] = useState<UsageResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!accessToken) return;
    Promise.all([
      api.vm.list(accessToken),
      api.user.usage(accessToken),
    ])
      .then(([vmData, usageData]) => { setVms(vmData); setUsage(usageData); })
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

  const runningCount = vms.filter((v) => v.status === "RUNNING").length;
  const freeCount = vms.filter((v) => v.planType === "FREE" && v.status !== "DELETED").length;
  const proCount = vms.filter((v) => v.planType === "PRO" && v.status !== "DELETED").length;
  const activeVms = vms.filter((v) => v.status !== "DELETED");

  return (
    <div>
      <div className="flex items-center justify-between mb-5">
        <h1 className="text-lg font-medium text-gray-900">인스턴스</h1>
        <Link
          href="/instances/new"
          className="bg-[#03C75A] text-white text-sm font-medium px-3.5 h-8 rounded-md flex items-center gap-1.5"
        >
          인스턴스 생성
        </Link>
      </div>

      <div className="grid grid-cols-3 gap-3 mb-6">
        <div className="bg-gray-50 rounded-md p-4">
          <p className="text-xs text-gray-500 mb-1">실행 중</p>
          <p className="text-2xl font-medium text-gray-900">{runningCount}</p>
          <p className="text-[11px] text-gray-400 mt-0.5">내 인스턴스 기준</p>
        </div>
        <div className="bg-gray-50 rounded-md p-4">
          <p className="text-xs text-gray-500 mb-1">내 FREE 인스턴스</p>
          <p className="text-2xl font-medium text-gray-900">
            {freeCount}
            <span className="text-base text-gray-400"> / {usage?.maxFreeVmCount ?? "-"}</span>
          </p>
          <p className="text-[11px] text-gray-400 mt-0.5">최대 {usage?.maxFreeVmCount ?? "-"}대</p>
        </div>
        <div className="bg-gray-50 rounded-md p-4">
          <p className="text-xs text-gray-500 mb-1">내 PRO 인스턴스</p>
          <p className="text-2xl font-medium text-gray-900">
            {proCount}
            <span className="text-base text-gray-400"> / {usage?.maxProVmCount ?? "-"}</span>
          </p>
          <p className="text-[11px] text-gray-400 mt-0.5">최대 {usage?.maxProVmCount ?? "-"}대</p>
        </div>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 gap-3">
          {[0, 1, 2].map((i) => <SkeletonCard key={i} />)}
        </div>
      ) : activeVms.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <p className="text-sm">인스턴스가 없습니다.</p>
          <Link href="/instances/new" className="text-[#03C75A] text-sm font-medium mt-2 inline-block">
            첫 인스턴스 생성하기 →
          </Link>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {activeVms.map((vm) => (
            <Link
              key={vm.id}
              href={`/instances/${vm.id}`}
              className="flex items-center justify-between bg-white border border-gray-200 rounded-lg px-4 py-3.5 hover:border-gray-300 transition-colors"
            >
              <div className="flex items-center gap-3">
                <span
                  className={`w-2 h-2 rounded-full ${
                    vm.status === "RUNNING" ? "bg-[#03C75A]" : vm.status === "FAILED" ? "bg-red-500" : "bg-amber-400"
                  }`}
                />
                <div>
                  <p className="text-sm font-medium text-gray-900">{vm.name}</p>
                  <p className="text-xs text-gray-500">
                    {vm.planType} · {vm.internalIp ?? "IP 할당 중"}
                  </p>
                </div>
              </div>
              <span
                className={`text-xs font-medium px-2.5 py-1 rounded-md ${
                  STATUS_STYLE[vm.status] ?? "bg-gray-100 text-gray-600"
                }`}
              >
                {vm.status}
              </span>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
