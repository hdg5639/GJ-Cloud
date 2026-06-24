"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { UpgradeRequestResponse, PagedResponse } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";

export default function AdminUpgradeRequestsPage() {
  const { accessToken } = useAuth();
  const [requests, setRequests] = useState<UpgradeRequestResponse[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState<Record<string, string>>({});
  const [page, setPage] = useState(1);

  useEffect(() => {
    if (!accessToken) return;
    loadRequests();
  }, [accessToken, page]);

  function loadRequests() {
    if (!accessToken) return;
    setLoading(true);
    api.admin.upgradeRequests
      .list(accessToken, page)
      .then((data: PagedResponse<UpgradeRequestResponse>) => {
        setRequests(data.content);
        setTotalPages(data.totalPages);
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }

  async function handleApprove(request: UpgradeRequestResponse) {
    if (!accessToken) return;
    setActionLoading(request.id);
    try {
      await api.admin.upgradeRequests.review(accessToken, request.id, true);
      loadRequests();
    } catch (err) {
      console.error(err);
    } finally {
      setActionLoading(null);
    }
  }

  async function handleReject(request: UpgradeRequestResponse) {
    if (!accessToken) return;
    const reason = rejectReason[request.id] || "거절됨";
    setActionLoading(request.id);
    try {
      await api.admin.upgradeRequests.review(accessToken, request.id, false, reason);
      loadRequests();
      setRejectReason((prev) => {
        const next = { ...prev };
        delete next[request.id];
        return next;
      });
    } catch (err) {
      console.error(err);
    } finally {
      setActionLoading(null);
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-semibold text-white">플랜 변경 요청</h1>
        <span className="text-sm text-gray-500">총 {requests.length}건</span>
      </div>

      {loading ? (
        <PageLoader dark />
      ) : requests.length === 0 ? (
        <div className="bg-gray-900 border border-gray-800 rounded-lg p-8 text-center">
          <p className="text-gray-400">대기 중인 요청이 없습니다</p>
        </div>
      ) : (
        <div className="space-y-3">
          {requests.map((request) => (
            <div key={request.id} className="bg-gray-900 border border-gray-800 rounded-lg p-4">
              <div className="flex items-start justify-between mb-3">
                <div className="flex-1">
                  <p className="text-sm font-medium text-gray-200">{request.userId}</p>
                  <p className="text-xs text-gray-500 mt-0.5">
                    {request.type === "UPGRADE" ? "업그레이드" : "다운그레이드"} 요청: {request.targetPlanType}
                  </p>
                  <p className="text-xs text-gray-600 mt-1">
                    요청일: {new Date(request.createdAt).toLocaleString("ko-KR")}
                  </p>
                </div>
                <span className="text-xs px-2 py-1 rounded font-medium bg-amber-900 text-amber-300">
                  {request.status}
                </span>
              </div>

              {request.status === "PENDING" && (
                <div className="space-y-3 pt-3 border-t border-gray-800">
                  <input
                    type="text"
                    placeholder="거절 사유 (선택)"
                    value={rejectReason[request.id] || ""}
                    onChange={(e) =>
                      setRejectReason((prev) => ({
                        ...prev,
                        [request.id]: e.target.value,
                      }))
                    }
                    className="w-full text-xs px-2 py-1.5 rounded bg-gray-800 border border-gray-700 text-gray-300 placeholder-gray-500"
                  />
                  <div className="flex gap-2">
                    <button
                      onClick={() => handleApprove(request)}
                      disabled={actionLoading === request.id}
                      className="flex-1 h-8 bg-green-600 text-white rounded text-xs font-medium disabled:opacity-60 hover:bg-green-700"
                    >
                      {actionLoading === request.id ? "처리 중..." : "승인"}
                    </button>
                    <button
                      onClick={() => handleReject(request)}
                      disabled={actionLoading === request.id}
                      className="flex-1 h-8 bg-red-600 text-white rounded text-xs font-medium disabled:opacity-60 hover:bg-red-700"
                    >
                      {actionLoading === request.id ? "처리 중..." : "거절"}
                    </button>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
