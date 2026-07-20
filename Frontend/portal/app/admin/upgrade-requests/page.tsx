"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { UpgradeRequestResponse, PagedResponse } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";
import { Panel } from "@/components/ui/panel";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { Pager } from "@/components/ui/pager";

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
        <h1 className="text-xl font-extrabold">플랜 변경 요청</h1>
        <span className="text-sm text-muted-soft">총 {requests.length}건</span>
      </div>

      {loading ? (
        <PageLoader />
      ) : requests.length === 0 ? (
        <Panel className="p-8 text-center">
          <p className="text-muted">대기 중인 요청이 없습니다</p>
        </Panel>
      ) : (
        <>
          <div className="space-y-3">
            {requests.map((request) => (
              <Panel key={request.id} className="p-4">
                <div className="flex items-start justify-between mb-3">
                  <div className="flex-1">
                    <p className="text-sm font-bold">{request.userId}</p>
                    <p className="text-xs text-muted mt-0.5">
                      {request.type === "UPGRADE" ? "업그레이드" : "다운그레이드"} 요청: {request.targetPlanType}
                    </p>
                    <p className="text-xs text-muted-soft mt-1">
                      요청일: {new Date(request.createdAt).toLocaleString("ko-KR")}
                    </p>
                  </div>
                  <span className="text-xs px-2 py-1 rounded font-bold bg-[#fffaf0] text-[#9c6b1f]">
                    {request.status}
                  </span>
                </div>

                {request.status === "PENDING" && (
                  <div className="space-y-3 pt-3 border-t border-line">
                    <Input
                      id={`reject-reason-${request.id}`}
                      name={`reject-reason-${request.id}`}
                      type="text"
                      placeholder="거절 사유 (선택)"
                      value={rejectReason[request.id] || ""}
                      onChange={(e) =>
                        setRejectReason((prev) => ({
                          ...prev,
                          [request.id]: e.target.value,
                        }))
                      }
                      className="text-xs h-8"
                    />
                    <div className="flex gap-2">
                      <Button
                        variant="primary"
                        onClick={() => handleApprove(request)}
                        disabled={actionLoading === request.id}
                        className="flex-1"
                      >
                        {actionLoading === request.id ? "처리 중..." : "승인"}
                      </Button>
                      <Button
                        variant="danger-solid"
                        onClick={() => handleReject(request)}
                        disabled={actionLoading === request.id}
                        className="flex-1"
                      >
                        {actionLoading === request.id ? "처리 중..." : "거절"}
                      </Button>
                    </div>
                  </div>
                )}
              </Panel>
            ))}
          </div>
          <Pager page={page} totalPages={totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}
