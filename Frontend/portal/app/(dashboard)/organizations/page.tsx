"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { OrgResponse, VmResponse } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";
import { Badge } from "@/components/ui/badge";
import { Panel } from "@/components/ui/panel";
import { Field, Input } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/modal";

const ROLE_LABEL: Record<string, string> = { OWNER: "소유자", ADMIN: "관리자", MEMBER: "멤버" };

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
    <div className="mx-auto max-w-[1380px]">
      <header className="mb-[22px] flex items-center justify-between gap-6">
        <div>
          <span className="text-[11px] font-extrabold tracking-[.11em] text-muted-soft">COLLABORATION</span>
          <h1 className="my-[5px] text-[29px] font-extrabold tracking-tight">협업</h1>
          <p className="m-0 text-sm text-muted">팀원과 인스턴스를 하나의 협업 공간에서 관리합니다.</p>
        </div>
        <Button variant="primary" onClick={() => setShowCreate(true)}>
          ＋ 조직 생성
        </Button>
      </header>

      {/* 받은 초대 */}
      {invitations.length > 0 && (
        <section className="mb-8">
          <h2 className="text-sm font-bold mb-3">
            받은 초대{" "}
            <span className="ml-1.5 text-xs text-[#9c6b1f] bg-[#fffaf0] border border-[#f3dfa8] px-1.5 py-0.5 rounded">
              {invitations.length}
            </span>
          </h2>
          <div className="flex flex-col gap-2">
            {invitations.map((inv) => (
              <div key={inv.id} className="flex items-center justify-between rounded-panel border border-[#f3dfa8] bg-[#fffaf0] px-4 py-3">
                <div>
                  <p className="text-sm font-bold">{inv.name}</p>
                  <p className="mt-0.5 text-xs text-[#9c6b1f]">{ROLE_LABEL[inv.myRole]} 역할로 초대됨</p>
                </div>
                <div className="flex gap-2">
                  <Button size="small" onClick={() => handleRespond(inv.id, inv.pendingMemberId ?? "", false)}>
                    거절
                  </Button>
                  <Button size="small" variant="primary" onClick={() => handleRespond(inv.id, inv.pendingMemberId ?? "", true)}>
                    수락
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Organization 목록 */}
      {orgs.length === 0 ? (
        <div className="text-center py-20 text-muted-soft">
          <p className="text-sm">소속된 Organization이 없습니다.</p>
          <button onClick={() => setShowCreate(true)} className="text-brand-strong text-sm font-bold mt-2 inline-block">
            첫 Organization 만들기 →
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-3">
          {orgs.map((org) => (
            <button key={org.id} onClick={() => router.push(`/organizations/${org.id}`)} className="text-left">
              <Panel className="px-5 py-4 hover:border-line-strong transition-colors">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm font-bold">{org.name}</span>
                  <Badge>{ROLE_LABEL[org.myRole]}</Badge>
                </div>
                <div className="flex gap-4 text-xs text-muted">
                  <span>멤버 {org.memberCount}명</span>
                  <span>VM {org.vmCount}대</span>
                  <span>{new Date(org.createdAt).toLocaleDateString("ko-KR")}</span>
                </div>
              </Panel>
            </button>
          ))}
        </div>
      )}

      {/* 생성 모달 */}
      <Modal open={showCreate} onClose={() => setShowCreate(false)}>
        <div className="mx-auto w-full max-w-md rounded-panel bg-panel">
          <div className="flex items-center justify-between px-5 py-4 border-b border-line">
            <h2 className="text-sm font-bold">새 Organization 만들기</h2>
            <button onClick={() => setShowCreate(false)} className="text-muted-soft hover:text-muted text-lg leading-none">×</button>
          </div>
          <form onSubmit={handleCreate} className="p-5 space-y-4">
            <Field label="이름" htmlFor="org-name">
              <Input
                id="org-name"
                name="org-name"
                type="text"
                value={orgName}
                onChange={(e) => setOrgName(e.target.value)}
                placeholder="팀 또는 프로젝트 이름"
                maxLength={100}
                required
              />
            </Field>
            {vms.length > 0 && (
              <div>
                <span className="text-xs font-bold text-muted block mb-1">연결할 VM <span className="text-muted-soft font-normal">(선택)</span></span>
                <div className="space-y-1.5 max-h-40 overflow-y-auto">
                  {vms.map((vm) => (
                    <label key={vm.id} htmlFor={`org-vm-${vm.id}`} className="flex items-center gap-2 text-sm cursor-pointer">
                      <input
                        id={`org-vm-${vm.id}`}
                        name={`org-vm-${vm.id}`}
                        type="checkbox"
                        checked={selectedVmIds.includes(vm.id)}
                        onChange={(e) => setSelectedVmIds((prev) =>
                          e.target.checked ? [...prev, vm.id] : prev.filter((id) => id !== vm.id)
                        )}
                        className="rounded accent-brand"
                      />
                      <span className="text-[#3d4941]">{vm.name}</span>
                      <span className="text-xs text-muted-soft">{vm.planType}</span>
                    </label>
                  ))}
                </div>
              </div>
            )}
            <p className="text-xs text-muted-soft">팀원은 생성 후 Organization 설정에서 초대할 수 있습니다.</p>
            <div className="flex gap-2 justify-end pt-1">
              <Button type="button" onClick={() => setShowCreate(false)}>
                취소
              </Button>
              <Button type="submit" variant="primary" disabled={creating}>
                {creating ? "생성 중..." : "생성"}
              </Button>
            </div>
          </form>
        </div>
      </Modal>
    </div>
  );
}
