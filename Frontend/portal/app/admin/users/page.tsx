"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { AdminUserResponse } from "@/lib/types";

export default function AdminUsersPage() {
  const { accessToken } = useAuth();
  const [users, setUsers] = useState<AdminUserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    api.admin.users.list(accessToken)
      .then((data) => { console.log("[admin/users] data:", data); setUsers(data); })
      .catch((err) => console.error("[admin/users] error:", err))
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

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-semibold text-white">사용자 관리</h1>
        <span className="text-sm text-gray-500">{users.length}명</span>
      </div>

      {loading ? (
        <p className="text-sm text-gray-500">불러오는 중...</p>
      ) : (
        <div className="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-800">
                <th className="text-left px-4 py-3 text-xs text-gray-500 font-medium">이메일</th>
                <th className="text-left px-4 py-3 text-xs text-gray-500 font-medium">닉네임</th>
                <th className="text-left px-4 py-3 text-xs text-gray-500 font-medium">플랜</th>
                <th className="text-left px-4 py-3 text-xs text-gray-500 font-medium">상태</th>
                <th className="text-left px-4 py-3 text-xs text-gray-500 font-medium">가입일</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-800">
              {users.map((user) => (
                <tr key={user.userId} className="hover:bg-gray-800/50">
                  <td className="px-4 py-3 text-gray-200">{user.email}</td>
                  <td className="px-4 py-3 text-gray-400">{user.nickname ?? "-"}</td>
                  <td className="px-4 py-3">
                    <span className={`text-[11px] px-2 py-0.5 rounded font-medium ${
                      user.planType === "PRO" ? "bg-violet-900 text-violet-300"
                      : user.planType === "ADMIN" ? "bg-red-900 text-red-300"
                      : "bg-gray-700 text-gray-400"
                    }`}>{user.planType}</span>
                  </td>
                  <td className="px-4 py-3">
                    {user.suspended ? (
                      <span className="text-[11px] px-2 py-0.5 rounded font-medium bg-red-900 text-red-400">정지됨</span>
                    ) : (
                      <span className="text-[11px] px-2 py-0.5 rounded font-medium bg-green-900 text-green-400">활성</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-gray-500 text-xs">
                    {new Date(user.createdAt).toLocaleDateString("ko-KR")}
                  </td>
                  <td className="px-4 py-3 text-right">
                    {user.planType !== "ADMIN" && (
                      <button
                        onClick={() => toggleSuspend(user)}
                        disabled={actionLoading === user.userId}
                        className={`text-xs px-3 py-1.5 rounded font-medium transition-colors disabled:opacity-50 ${
                          user.suspended
                            ? "bg-green-900 text-green-400 hover:bg-green-800"
                            : "bg-red-900/50 text-red-400 hover:bg-red-900"
                        }`}
                      >
                        {actionLoading === user.userId ? "처리 중..." : user.suspended ? "활성화" : "정지"}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
