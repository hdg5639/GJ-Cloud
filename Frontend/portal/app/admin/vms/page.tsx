"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { AdminVmResponse, AdminUserResponse } from "@/lib/types";
import { SkeletonRow } from "@/components/ui/loader";
import { Panel } from "@/components/ui/panel";
import { Table, Th, Td } from "@/components/ui/table";
import { Badge, StatusBadge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/modal";
import { Pager } from "@/components/ui/pager";

const STATUS_TONE: Record<string, "ok" | "off"> = {
  RUNNING: "ok",
  FAILED: "off",
  DELETING: "off",
  STOPPED: "off",
  SUSPENDED: "off",
};

export default function AdminVmsPage() {
  const { accessToken } = useAuth();
  const [vms, setVms] = useState<AdminVmResponse[]>([]);
  const [userMap, setUserMap] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [deleteTarget, setDeleteTarget] = useState<AdminVmResponse | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (!accessToken) return;
    api.admin.vms.listPage(accessToken, page, 50)
      .then(async (vmData) => {
        const ownerIds = Array.from(new Set(vmData.content.map((vm) => vm.userId)));
        const userData = ownerIds.length > 0
          ? await api.admin.users.batch(accessToken, ownerIds)
          : [];
        setVms(vmData.content);
        setTotalPages(vmData.totalPages);
        setTotalElements(vmData.totalElements);
        setUserMap(Object.fromEntries(userData.map((u: AdminUserResponse) => [u.userId, u.email])));
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [accessToken, page]);

  function changePage(nextPage: number) {
    setLoading(true);
    setPage(nextPage);
  }

  async function handleForceDelete() {
    if (!accessToken || !deleteTarget) return;
    setDeleting(true);
    try {
      await api.admin.vms.forceDelete(accessToken, deleteTarget.id);
      setVms((prev) => prev.filter((v) => v.id !== deleteTarget.id));
      setTotalElements((current) => Math.max(0, current - 1));
      setDeleteTarget(null);
    } catch {
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-extrabold">VM 관리</h1>
        <span className="text-sm text-muted-soft">{totalElements.toLocaleString("ko-KR")}대</span>
      </div>

      <Panel className="overflow-hidden">
        <Table>
          <thead>
            <tr>
              <Th>이름</Th>
              <Th>소유자</Th>
              <Th>플랜</Th>
              <Th>상태</Th>
              <Th>IP</Th>
              <Th>디스크</Th>
              <Th>생성일</Th>
              <Th />
            </tr>
          </thead>
          <tbody>
            {loading
              ? [0, 1, 2, 3, 4].map((i) => <SkeletonRow key={i} cols={8} />)
              : vms.map((vm) => (
              <tr key={vm.id} className="hover:bg-[#fbfdfc]">
                <Td>
                  <p className="text-[#3d4941]">{vm.name}</p>
                  {vm.subdomain && (
                    <p className="text-xs text-muted-soft">{vm.subdomain}</p>
                  )}
                </Td>
                <Td className="text-muted text-xs">
                  {userMap[vm.userId] ?? vm.userId.slice(0, 8) + "…"}
                </Td>
                <Td>
                  {vm.planType === "PRO" ? <Badge>PRO</Badge> : <span className="text-[11px] px-2 py-0.5 rounded font-bold bg-[#eef1ef] text-muted">FREE</span>}
                </Td>
                <Td>
                  <StatusBadge
                    tone={STATUS_TONE[vm.status] ?? "off"}
                    className={STATUS_TONE[vm.status] === undefined ? "bg-[#fffaf0] text-[#9c6b1f]" : undefined}
                  >
                    {vm.status}
                  </StatusBadge>
                </Td>
                <Td className="text-muted font-mono text-xs">{vm.internalIp ?? "-"}</Td>
                <Td className="text-muted text-xs">{vm.diskSizeGb}GB</Td>
                <Td className="text-muted-soft text-xs">
                  {new Date(vm.createdAt).toLocaleDateString("ko-KR")}
                </Td>
                <Td className="text-right">
                  <button
                    onClick={() => setDeleteTarget(vm)}
                    className="text-xs px-3 py-1.5 rounded font-bold bg-[#fdf4f4] text-danger hover:bg-[#fbe5e5] transition-colors"
                  >
                    강제 삭제
                  </button>
                </Td>
              </tr>
            ))}
          </tbody>
        </Table>
      </Panel>
      <Pager page={page} totalPages={totalPages} onChange={changePage} />

      {/* 강제 삭제 확인 모달 */}
      <Modal open={!!deleteTarget} onClose={() => setDeleteTarget(null)}>
        {deleteTarget && (
          <div className="mx-auto w-[400px] rounded-panel bg-panel p-6">
            <h2 className="text-base font-bold mb-2">VM 강제 삭제</h2>
            <p className="text-sm text-muted mb-1">
              <span className="text-[#3f4c43] font-bold">{deleteTarget.name}</span> 을(를) 강제 삭제합니다.
            </p>
            <p className="text-xs text-danger mb-6">
              소유자 확인 없이 즉시 삭제되며, Cloudflare 리소스도 함께 정리됩니다. 복구 불가.
            </p>
            <div className="flex gap-3 justify-end">
              <Button onClick={() => setDeleteTarget(null)} disabled={deleting}>
                취소
              </Button>
              <Button variant="danger-solid" onClick={handleForceDelete} disabled={deleting}>
                {deleting ? "삭제 중..." : "강제 삭제"}
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
