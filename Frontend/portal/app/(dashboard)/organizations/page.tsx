"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { OrgResponse, VmResponse } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";

const ROLE_LABEL: Record<string, string> = { OWNER: "소유자", ADMIN: "관리자", MEMBER: "멤버" };
const ROLE_STYLE: Record<string, string> = {
  OWNER: "bg-violet-100 text-violet-700",
  ADMIN: "bg-blue-100 text-blue-700",
  MEMBER: "bg-gray-100 text-gray-600",
};

export default function OrganizationsPage() {
  const { accessToken } = useAuth();
  const router = useRouter();
  const [orgs, setOrgs] = useState<OrgResponse[]>([]);
  const [invitations, setInvitations] = useState<OrgResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [vms, setVms] = useState<VmResponse[]>([]);

  // create form
  const [orgName, setOrgName] = useState("");
  const [selectedVmIds, setSelectedVmIds] = useState<string[]>([]);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    if (!accessToken) return;
    Promise.all([
      api.org.list(accessToken).then(setOrgs),
      api.org.invitations(accessToken).then(setInvitations),
      api.vm.list(accessToken).then(setVms),
    ]).finally(() => setLoading(false));
  }, [accessToken]);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!accessToken || !orgName.trim()) return;
    setCreating(true);
    try {
      const org = await api.org.create(accessToken, {
        name: orgName.trim(),
        vmIds: selectedVmIds,
      });
      setOrgs((prev) => [...prev, { id: org.id, name: org.name, ownerId: org.ownerId, myRole: org.myRole, memberCount: org.members.length, vmCount: org.vms.length, createdAt: org.createdAt }]);
      setShowCreate(false);
      setOrgName("");
      setSelectedVmIds([]);
      router.push(`/organizations/${org.id}`);
    } catch (err) {
      alert(err instanceof Error ? err.message : "생성에 실패했습니다");
    } finally {
      setCreating(false);
    }
  }

  async function handleRespond(orgId: string, memberId: string, accept: boolean) {
    if (!accessToken) return;
    try {
      await api.org.respond(accessToken, orgId, memberId, accept);
      setInvitations((prev) => prev.filter((o) => o.id !== orgId));
      if (accept) {
        const updated = await api.org.list(accessToken);
        setOrgs(updated);
      }
    } catch (err) {
      alert(err instanceof Error ? err.message : "처리에 실패했습니다");
    }
  }

  if (loading) return <PageLoader />;

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-medium text-gray-900">협업</h1>
        <button
          onClick={() => setShowCreate(true)}
          className="text-sm px-4 h-8 bg-[#03C75A] text-white rounded-md hover:bg-[#02b351]"
        >
          + 새 Organization
        </button>
      </div>

      {/* 받은 초대 */}
      {invitations.length > 0 && (
        <section className="mb-8">
          <h2 className="text-sm font-medium text-gray-700 mb-3">받은 초대 <span className="ml-1.5 text-xs text-amber-600 bg-amber-50 border border-amber-200 px-1.5 py-0.5 rounded">{invitations.length}</span></h2>
          <div className="flex flex-col gap-2">
            {invitations.map((inv) => (
              <div key={inv.id} className="flex items-center justify-between bg-amber-50 border border-amber-200 rounded-xl px-4 py-3">
                <div>
                  <p className="text-sm font-medium text-gray-900">{inv.name}</p>
                  <p className="text-xs text-amber-700 mt-0.5">{ROLE_LABEL[inv.myRole]} 역할로 초대됨</p>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => handleRespond(inv.id, inv.pendingMemberId ?? "", false)}
                    className="text-xs px-3 h-7 border border-gray-300 rounded-md hover:bg-gray-50"
                  >
                    거절
                  </button>
                  <button
                    onClick={() => handleRespond(inv.id, inv.pendingMemberId ?? "", true)}
                    className="text-xs px-3 h-7 bg-[#03C75A] text-white rounded-md hover:bg-[#02b351]"
                  >
                    수락
                  </button>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Organization 목록 */}
      {orgs.length === 0 ? (
        <div className="text-center py-20 text-gray-400">
          <p className="text-sm">소속된 Organization이 없습니다.</p>
          <button onClick={() => setShowCreate(true)} className="text-[#03C75A] text-sm font-medium mt-2 inline-block">
            첫 Organization 만들기 →
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-3">
          {orgs.map((org) => (
            <button
              key={org.id}
              onClick={() => router.push(`/organizations/${org.id}`)}
              className="text-left border border-gray-200 rounded-xl px-5 py-4 hover:border-gray-300 hover:shadow-sm transition-all"
            >
              <div className="flex items-center justify-between mb-2">
                <span className="text-sm font-medium text-gray-900">{org.name}</span>
                <span className={`text-[11px] font-medium px-2 py-0.5 rounded ${ROLE_STYLE[org.myRole]}`}>
                  {ROLE_LABEL[org.myRole]}
                </span>
              </div>
              <div className="flex gap-4 text-xs text-gray-500">
                <span>멤버 {org.memberCount}명</span>
                <span>VM {org.vmCount}대</span>
                <span>{new Date(org.createdAt).toLocaleDateString("ko-KR")}</span>
              </div>
            </button>
          ))}
        </div>
      )}

      {/* 생성 모달 */}
      {showCreate && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-md">
            <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
              <h2 className="text-sm font-medium text-gray-900">새 Organization 만들기</h2>
              <button onClick={() => setShowCreate(false)} className="text-gray-400 hover:text-gray-600 text-lg leading-none">×</button>
            </div>
            <form onSubmit={handleCreate} className="p-5 space-y-4">
              <div>
                <label className="text-xs text-gray-500 block mb-1">이름</label>
                <input
                  type="text"
                  value={orgName}
                  onChange={(e) => setOrgName(e.target.value)}
                  placeholder="팀 또는 프로젝트 이름"
                  maxLength={100}
                  required
                  className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm"
                />
              </div>
              {vms.length > 0 && (
                <div>
                  <label className="text-xs text-gray-500 block mb-1">연결할 VM <span className="text-gray-400">(선택)</span></label>
                  <div className="space-y-1.5 max-h-40 overflow-y-auto">
                    {vms.map((vm) => (
                      <label key={vm.id} className="flex items-center gap-2 text-sm cursor-pointer">
                        <input
                          type="checkbox"
                          checked={selectedVmIds.includes(vm.id)}
                          onChange={(e) => setSelectedVmIds((prev) =>
                            e.target.checked ? [...prev, vm.id] : prev.filter((id) => id !== vm.id)
                          )}
                          className="rounded"
                        />
                        <span className="text-gray-800">{vm.name}</span>
                        <span className="text-xs text-gray-400">{vm.planType}</span>
                      </label>
                    ))}
                  </div>
                </div>
              )}
              <p className="text-xs text-gray-400">팀원은 생성 후 Organization 설정에서 초대할 수 있습니다.</p>
              <div className="flex gap-2 justify-end pt-1">
                <button type="button" onClick={() => setShowCreate(false)} className="text-sm px-4 h-8 border border-gray-300 rounded-md hover:bg-gray-50">
                  취소
                </button>
                <button type="submit" disabled={creating} className="text-sm px-4 h-8 bg-[#03C75A] text-white rounded-md hover:bg-[#02b351] disabled:opacity-50">
                  {creating ? "생성 중..." : "생성"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
