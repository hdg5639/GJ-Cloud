"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { AdminUserResponse } from "@/lib/types";
import { SkeletonRow } from "@/components/ui/loader";
import { Panel } from "@/components/ui/panel";
import { Table, Th, Td } from "@/components/ui/table";
import { StatusBadge } from "@/components/ui/badge";

export default function AdminUsersPage() {
  const { accessToken } = useAuth();
  const [users, setUsers] = useState<AdminUserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    api.admin.users.list(accessToken)
      .then((data) => setUsers(data))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [accessToken]);

  async function toggleSuspend(user: AdminUserResponse) {
    if (!accessToken) return;
    setActionLoading(user.userId);
    try {
      const updated = user.suspended
        ? await api.admin.users.activate(accessToken, user.userId)
        : await api.admin.users.suspend(accessToken, user.userId);
      setUsers((prev) => prev.map((u) => (u.userId === updated.userId ? updated : u)));
    } catch {
    } finally {
      setActionLoading(null);
    }
  }

  async function handlePlanChange(user: AdminUserResponse, newPlan: string) {
    if (!accessToken) return;
    setActionLoading(user.userId);
    try {
      const updated = await api.admin.users.updatePlan(accessToken, user.userId, newPlan);
      setUsers((prev) => prev.map((u) => (u.userId === updated.userId ? updated : u)));
    } catch {
    } finally {
      setActionLoading(null);
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-extrabold">사용자 관리</h1>
        <span className="text-sm text-muted-soft">{users.length}명</span>
      </div>

      <Panel className="overflow-hidden">
        <Table>
          <thead>
            <tr>
              <Th>이메일</Th>
              <Th>닉네임</Th>
              <Th>플랜</Th>
              <Th>상태</Th>
              <Th>가입일</Th>
              <Th />
            </tr>
          </thead>
          <tbody>
            {loading
              ? [0, 1, 2, 3, 4].map((i) => <SkeletonRow key={i} cols={6} />)
              : users.map((user) => (
              <tr key={user.userId} className="hover:bg-[#fbfdfc]">
                <Td className="text-[#3d4941]">{user.email}</Td>
                <Td className="text-muted">{user.nickname ?? "-"}</Td>
                <Td>
                  {user.planType === "ADMIN" ? (
                    <span className="text-[11px] px-2 py-0.5 rounded font-bold bg-[#fdf4f4] text-danger">ADMIN</span>
                  ) : (
                    <select
                      id={`user-plan-${user.userId}`}
                      name={`user-plan-${user.userId}`}
                      value={user.planType}
                      onChange={(e) => handlePlanChange(user, e.target.value)}
                      disabled={actionLoading === user.userId}
                      className="text-xs px-2 py-1 rounded border border-line-strong text-[#3d4941] disabled:opacity-50"
                    >
                      <option value="FREE">FREE</option>
                      <option value="PRO">PRO</option>
                    </select>
                  )}
                </Td>
                <Td>
                  <StatusBadge tone={user.suspended ? "off" : "ok"} className={user.suspended ? "bg-[#fdf4f4] text-danger" : undefined}>
                    {user.suspended ? "정지됨" : "활성"}
                  </StatusBadge>
                </Td>
                <Td className="text-muted-soft text-xs">
                  {new Date(user.createdAt).toLocaleDateString("ko-KR")}
                </Td>
                <Td className="text-right">
                  {user.planType !== "ADMIN" && (
                    <button
                      onClick={() => toggleSuspend(user)}
                      disabled={actionLoading === user.userId}
                      className={`text-xs px-3 py-1.5 rounded font-bold transition-colors disabled:opacity-50 ${
                        user.suspended
                          ? "bg-soft text-brand-strong hover:bg-[#dff3e6]"
                          : "bg-[#fdf4f4] text-danger hover:bg-[#fbe5e5]"
                      }`}
                    >
                      {actionLoading === user.userId ? "처리 중..." : user.suspended ? "활성화" : "정지"}
                    </button>
                  )}
                </Td>
              </tr>
            ))}
          </tbody>
        </Table>
      </Panel>
    </div>
  );
}
