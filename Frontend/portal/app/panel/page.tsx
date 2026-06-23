"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { AdminUserResponse, AdminVmResponse } from "@/lib/types";

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
      <h1 className="text-xl font-semibold text-white mb-6">대시보드</h1>

      {loading ? (
        <p className="text-sm text-gray-500">불러오는 중...</p>
      ) : (
        <>
          <div className="grid grid-cols-3 gap-4 mb-8">
            {stats.map((s) => (
              <div key={s.label} className="bg-gray-900 border border-gray-800 rounded-lg p-4">
                <p className="text-xs text-gray-500 mb-1">{s.label}</p>
                <p className={`text-2xl font-semibold ${s.warn ? "text-red-400" : "text-white"}`}>
                  {s.value}
                </p>
              </div>
            ))}
          </div>

          <div className="grid grid-cols-2 gap-6">
            {/* 최근 사용자 */}
            <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
              <h2 className="text-sm font-medium text-gray-300 mb-3">최근 가입 사용자</h2>
              <div className="space-y-2">
                {users.slice(0, 8).map((u) => (
                  <div key={u.userId} className="flex items-center justify-between">
                    <span className="text-sm text-gray-300 truncate max-w-[180px]">{u.email}</span>
                    <div className="flex items-center gap-2">
                      <span className={`text-[10px] px-1.5 py-0.5 rounded font-medium ${
                        u.planType === "PRO" ? "bg-violet-900 text-violet-300" : "bg-gray-700 text-gray-400"
                      }`}>{u.planType}</span>
                      {u.suspended && (
                        <span className="text-[10px] px-1.5 py-0.5 rounded font-medium bg-red-900 text-red-400">정지</span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* VM 상태 */}
            <div className="bg-gray-900 border border-gray-800 rounded-lg p-4">
              <h2 className="text-sm font-medium text-gray-300 mb-3">VM 현황</h2>
              <div className="space-y-2">
                {vms.slice(0, 8).map((v) => (
                  <div key={v.id} className="flex items-center justify-between">
                    <span className="text-sm text-gray-300 truncate max-w-[160px]">{v.name}</span>
                    <div className="flex items-center gap-2">
                      <span className={`text-[10px] px-1.5 py-0.5 rounded font-medium ${
                        v.planType === "PRO" ? "bg-violet-900 text-violet-300" : "bg-gray-700 text-gray-400"
                      }`}>{v.planType}</span>
                      <span className={`text-[10px] px-1.5 py-0.5 rounded font-medium ${
                        v.status === "RUNNING" ? "bg-green-900 text-green-400"
                        : v.status === "FAILED" ? "bg-red-900 text-red-400"
                        : "bg-gray-700 text-gray-400"
                      }`}>{v.status}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
