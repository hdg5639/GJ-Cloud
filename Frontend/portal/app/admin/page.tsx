"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { AdminUserResponse, AdminVmResponse } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";
import { StatGrid, StatCard } from "@/components/ui/stat-card";
import { Panel } from "@/components/ui/panel";
import { Badge, StatusBadge } from "@/components/ui/badge";

export default function AdminDashboardPage() {
  const { accessToken } = useAuth();
  const [users, setUsers] = useState<AdminUserResponse[]>([]);
  const [vms, setVms] = useState<AdminVmResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!accessToken) return;
    Promise.all([
      api.admin.users.list(accessToken),
      api.admin.vms.list(accessToken),
    ])
      .then(([u, v]) => {
        setUsers(u);
        setVms(v);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [accessToken]);

  const runningVms = vms.filter((v) => v.status === "RUNNING").length;
  const suspendedUsers = users.filter((u) => u.suspended).length;
  const freeVms = vms.filter((v) => v.planType === "FREE").length;
  const proVms = vms.filter((v) => v.planType === "PRO").length;

  const stats = [
    { label: "전체 사용자", value: users.length },
    { label: "정지된 계정", value: suspendedUsers, warn: suspendedUsers > 0 },
    { label: "전체 VM", value: vms.length },
    { label: "실행 중", value: runningVms },
    { label: "FREE VM", value: freeVms },
    { label: "PRO VM", value: proVms },
  ];

  return (
    <div>
      <h1 className="text-xl font-extrabold mb-6">대시보드</h1>

      {loading ? (
        <PageLoader />
      ) : (
        <>
          <StatGrid cols={3} className="mb-8">
            {stats.map((s) => (
              <StatCard
                key={s.label}
                compact
                label={s.label}
                value={<span className={s.warn ? "text-danger" : undefined}>{s.value}</span>}
              />
            ))}
          </StatGrid>

          <div className="grid grid-cols-2 gap-6">
            {/* 최근 사용자 */}
            <Panel className="p-4">
              <h2 className="text-sm font-bold mb-3">최근 가입 사용자</h2>
              <div className="space-y-2">
                {users.slice(0, 8).map((u) => (
                  <div key={u.userId} className="flex items-center justify-between">
                    <span className="text-sm text-[#3d4941] truncate max-w-[180px]">{u.email}</span>
                    <div className="flex items-center gap-2">
                      {u.planType === "PRO" ? <Badge>PRO</Badge> : <span className="text-[10px] px-1.5 py-0.5 rounded font-bold bg-[#eef1ef] text-muted">FREE</span>}
                      {u.suspended && (
                        <span className="text-[10px] px-1.5 py-0.5 rounded font-bold bg-[#fdf4f4] text-danger">정지</span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </Panel>

            {/* VM 상태 */}
            <Panel className="p-4">
              <h2 className="text-sm font-bold mb-3">VM 현황</h2>
              <div className="space-y-2">
                {vms.slice(0, 8).map((v) => (
                  <div key={v.id} className="flex items-center justify-between">
                    <span className="text-sm text-[#3d4941] truncate max-w-[160px]">{v.name}</span>
                    <div className="flex items-center gap-2">
                      {v.planType === "PRO" ? <Badge>PRO</Badge> : <span className="text-[10px] px-1.5 py-0.5 rounded font-bold bg-[#eef1ef] text-muted">FREE</span>}
                      <StatusBadge
                        tone={v.status === "RUNNING" ? "ok" : "off"}
                        className={v.status === "FAILED" ? "bg-[#fdf4f4] text-danger" : undefined}
                      >
                        {v.status}
                      </StatusBadge>
                    </div>
                  </div>
                ))}
              </div>
            </Panel>
          </div>
        </>
      )}
    </div>
  );
}
