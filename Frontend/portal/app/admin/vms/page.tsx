"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { AdminVmResponse, AdminUserResponse } from "@/lib/types";

const STATUS_COLOR: Record<string, string> = {
  RUNNING: "bg-green-900 text-green-400",
  FAILED: "bg-red-900 text-red-400",
  DELETING: "bg-red-900/50 text-red-400",
  STOPPED: "bg-gray-700 text-gray-400",
  SUSPENDED: "bg-gray-700 text-gray-400",
};

export default function AdminVmsPage() {
  const { accessToken } = useAuth();
  const [vms, setVms] = useState<AdminVmResponse[]>([]);
  const [userMap, setUserMap] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [deleteTarget, setDeleteTarget] = useState<AdminVmResponse | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (!accessToken) return;
    Promise.all([
      api.admin.vms.list(accessToken),
      api.admin.users.list(accessToken),
    ])
      .then(([vmData, userData]) => {
        setVms(vmData);
        setUserMap(Object.fromEntries(userData.map((u: AdminUserResponse) => [u.userId, u.email])));
      })
      .catch((err) => console.error("[admin/vms] error:", err))
      .finally(() => setLoading(false));
  }, [accessToken]);

  async function handleForceDelete() {
    if (!accessToken || !deleteTarget) return;
    setDeleting(true);
    try {
      await api.admin.vms.forceDelete(accessToken, deleteTarget.id);
      setVms((prev) => prev.filter((v) => v.id !== deleteTarget.id));
      setDeleteTarget(null);
    } catch {
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-semibold text-white">VM 관리</h1>
        <span className="text-sm text-gray-500">{vms.length}대</span>
      </div>

      {loading ? (
        <p className="text-sm text-gray-500">불러오는 중...</p>
      ) : (
        <div className="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-800">
                <th className="text-left px-4 py-3 text-xs text-gray-500 font-medium">이름</th>
                <th className="text-left px-4 py-3 text-xs text-gray-500 font-medium">소유자</th>
                <th className="text-left px-4 py-3 text-xs text-gray-500 font-medium">플랜</th>
                <th className="text-left px-4 py-3 text-xs text-gray-500 font-medium">상태</th>
                <th className="text-left px-4 py-3 text-xs text-gray-500 font-medium">IP</th>
                <th className="text-left px-4 py-3 text-xs text-gray-500 font-medium">디스크</th>
                <th className="text-left px-4 py-3 text-xs text-gray-500 font-medium">생성일</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-800">
              {vms.map((vm) => (
                <tr key={vm.id} className="hover:bg-gray-800/50">
                  <td className="px-4 py-3">
                    <p className="text-gray-200">{vm.name}</p>
                    {vm.subdomain && (
                      <p className="text-xs text-gray-500">{vm.subdomain}</p>
                    )}
                  </td>
                  <td className="px-4 py-3 text-gray-400 text-xs">
                    {userMap[vm.userId] ?? vm.userId.slice(0, 8) + "…"}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`text-[11px] px-2 py-0.5 rounded font-medium ${
                      vm.planType === "PRO" ? "bg-violet-900 text-violet-300" : "bg-gray-700 text-gray-400"
                    }`}>{vm.planType}</span>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`text-[11px] px-2 py-0.5 rounded font-medium ${
                      STATUS_COLOR[vm.status] ?? "bg-amber-900 text-amber-400"
                    }`}>{vm.status}</span>
                  </td>
                  <td className="px-4 py-3 text-gray-400 font-mono text-xs">{vm.internalIp ?? "-"}</td>
                  <td className="px-4 py-3 text-gray-400 text-xs">{vm.diskSizeGb}GB</td>
                  <td className="px-4 py-3 text-gray-500 text-xs">
                    {new Date(vm.createdAt).toLocaleDateString("ko-KR")}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => setDeleteTarget(vm)}
                      className="text-xs px-3 py-1.5 rounded font-medium bg-red-900/50 text-red-400 hover:bg-red-900 transition-colors"
                    >
                      강제 삭제
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* 강제 삭제 확인 모달 */}
      {deleteTarget && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50">
          <div className="bg-gray-900 border border-gray-700 rounded-xl p-6 w-[400px]">
            <h2 className="text-base font-semibold text-white mb-2">VM 강제 삭제</h2>
            <p className="text-sm text-gray-400 mb-1">
              <span className="text-white font-medium">{deleteTarget.name}</span> 을(를) 강제 삭제합니다.
            </p>
            <p className="text-xs text-red-400 mb-6">
              소유자 확인 없이 즉시 삭제되며, Cloudflare 리소스도 함께 정리됩니다. 복구 불가.
            </p>
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => setDeleteTarget(null)}
                disabled={deleting}
                className="px-4 py-2 rounded-lg text-sm text-gray-400 hover:bg-gray-800 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleForceDelete}
                disabled={deleting}
                className="px-4 py-2 rounded-lg text-sm font-medium bg-red-600 text-white hover:bg-red-700 disabled:opacity-50 transition-colors"
              >
                {deleting ? "삭제 중..." : "강제 삭제"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
