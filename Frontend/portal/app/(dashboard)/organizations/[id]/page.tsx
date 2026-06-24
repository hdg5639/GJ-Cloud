"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { CollaborationResponse, CollaborationType, MemberResponse, MemberRole, OrgDetailResponse } from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";
import CollaborationWriteModal from "@/components/collaboration-write-modal";
import CollaborationCard from "@/components/collaboration-card";

const ROLE_LABEL: Record<MemberRole, string> = { OWNER: "소유자", ADMIN: "관리자", MEMBER: "멤버" };
const ROLE_STYLE: Record<MemberRole, string> = {
  OWNER: "bg-violet-100 text-violet-700",
  ADMIN: "bg-blue-100 text-blue-700",
  MEMBER: "bg-gray-100 text-gray-600",
};

type Tab = "collab" | "members" | "vms";

export default function OrganizationDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { accessToken, user } = useAuth();
  const router = useRouter();
  const [org, setOrg] = useState<OrgDetailResponse | null>(null);
  const [tab, setTab] = useState<Tab>("collab");
  const [items, setItems] = useState<CollaborationResponse[]>([]);
  const [typeFilter, setTypeFilter] = useState<CollaborationType | undefined>();
  const [showWrite, setShowWrite] = useState(false);
  const [editingItem, setEditingItem] = useState<CollaborationResponse | undefined>();
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<MemberRole>("MEMBER");
  const [inviting, setInviting] = useState(false);

  useEffect(() => {
    if (!accessToken) return;
    api.org.get(accessToken, id).then(setOrg).catch(() => router.push("/organizations"));
  }, [accessToken, id]);

  useEffect(() => {
    if (!accessToken || !org) return;
    api.collab.list(accessToken, "ORGANIZATION", id, typeFilter).then(setItems).catch(() => {});
  }, [accessToken, id, typeFilter, org]);

  const myRole = org?.myRole;
  const isOwnerOrAdmin = myRole === "OWNER" || myRole === "ADMIN";

  function handleItemUpdated(updated: CollaborationResponse) {
    setItems((prev) => prev.map((i) => (i.id === updated.id ? updated : i)));
  }

  function handleItemDeleted(itemId: string) {
    setItems((prev) => prev.filter((i) => i.id !== itemId));
  }

  function handleCreated(item: CollaborationResponse) {
    setItems((prev) => [item, ...prev]);
    setShowWrite(false);
  }

  async function handleInvite(e: React.FormEvent) {
    e.preventDefault();
    if (!accessToken || !inviteEmail.trim()) return;
    setInviting(true);
    try {
      const member = await api.org.invite(accessToken, id, inviteEmail.trim(), inviteRole);
      setOrg((prev) => prev ? { ...prev, members: [...prev.members, member] } : prev);
      setInviteEmail("");
    } catch (err) {
      alert(err instanceof Error ? err.message : "초대에 실패했습니다");
    } finally {
      setInviting(false);
    }
  }

  async function handleRemoveMember(member: MemberResponse) {
    if (!accessToken) return;
    if (!confirm(`${member.email}을 제거하시겠습니까?`)) return;
    try {
      await api.org.removeMember(accessToken, id, member.id);
      setOrg((prev) => prev ? { ...prev, members: prev.members.filter((m) => m.id !== member.id) } : prev);
    } catch (err) {
      alert(err instanceof Error ? err.message : "제거에 실패했습니다");
    }
  }

  async function handleRoleChange(member: MemberResponse, role: MemberRole) {
    if (!accessToken) return;
    try {
      const updated = await api.org.updateRole(accessToken, id, member.id, role);
      setOrg((prev) => prev ? { ...prev, members: prev.members.map((m) => m.id === member.id ? updated : m) } : prev);
    } catch (err) {
      alert(err instanceof Error ? err.message : "권한 변경에 실패했습니다");
    }
  }

  async function handleDeleteOrg() {
    if (!accessToken) return;
    if (!confirm(`"${org?.name}" Organization을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.`)) return;
    try {
      await api.org.delete(accessToken, id);
      router.push("/organizations");
    } catch (err) {
      alert(err instanceof Error ? err.message : "삭제에 실패했습니다");
    }
  }

  if (!org) return <PageLoader />;

  return (
    <div>
      {/* 헤더 */}
      <div className="flex items-center gap-2 mb-1">
        <button onClick={() => router.push("/organizations")} className="text-xs text-gray-500 hover:text-gray-700">협업</button>
        <span className="text-xs text-gray-400">/</span>
        <span className="text-xs text-gray-700">{org.name}</span>
      </div>
      <div className="flex items-center justify-between mb-5">
        <div className="flex items-center gap-3">
          <h1 className="text-xl font-medium text-gray-900">{org.name}</h1>
          <span className={`text-xs font-medium px-2 py-0.5 rounded ${ROLE_STYLE[org.myRole]}`}>{ROLE_LABEL[org.myRole]}</span>
        </div>
        {myRole === "OWNER" && (
          <button onClick={handleDeleteOrg} className="text-sm px-3 h-8 border border-red-200 bg-red-50 text-red-600 rounded-md hover:bg-red-100">
            삭제
          </button>
        )}
      </div>

      {/* 탭 */}
      <div className="flex gap-1 border-b border-gray-200 mb-5">
        {([["collab", "협업"], ["members", "멤버"], ["vms", "VM"]] as [Tab, string][]).map(([key, label]) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              tab === key ? "border-[#03C75A] text-[#03C75A]" : "border-transparent text-gray-500 hover:text-gray-700"
            }`}
          >
            {label}
            {key === "members" && <span className="ml-1.5 text-xs text-gray-400">{org.members.filter(m => m.status === "ACCEPTED").length}</span>}
            {key === "vms" && <span className="ml-1.5 text-xs text-gray-400">{org.vms.length}</span>}
          </button>
        ))}
      </div>

      {/* 협업 탭 */}
      {tab === "collab" && (
        <div>
          <div className="flex items-center justify-between mb-4">
            <div className="flex gap-1.5">
              {([undefined, "NOTE", "NOTICE", "REQUEST"] as (CollaborationType | undefined)[]).map((t) => (
                <button
                  key={t ?? "all"}
                  onClick={() => setTypeFilter(t)}
                  className={`text-xs px-2.5 py-1 rounded-md border transition-colors ${
                    typeFilter === t ? "border-gray-400 bg-gray-100 text-gray-800" : "border-gray-200 text-gray-500 hover:border-gray-300"
                  }`}
                >
                  {t === undefined ? "전체" : t === "NOTE" ? "메모" : t === "NOTICE" ? "공지" : "요청"}
                </button>
              ))}
            </div>
            <button
              onClick={() => { setEditingItem(undefined); setShowWrite(true); }}
              className="text-sm px-3 h-8 bg-[#03C75A] text-white rounded-md hover:bg-[#02b351]"
            >
              + 작성
            </button>
          </div>
          {items.length === 0 ? (
            <div className="text-center py-16 text-gray-400 text-sm">협업 항목이 없습니다.</div>
          ) : (
            <div className="flex flex-col gap-3">
              {items.map((item) => (
                <CollaborationCard
                  key={item.id}
                  item={item}
                  accessToken={accessToken!}
                  isOwnerOrAdmin={isOwnerOrAdmin}
                  onEdit={(i) => { setEditingItem(i); setShowWrite(true); }}
                  onUpdate={handleItemUpdated}
                  onDelete={handleItemDeleted}
                />
              ))}
            </div>
          )}
        </div>
      )}

      {/* 멤버 탭 */}
      {tab === "members" && (
        <div className="space-y-4">
          {isOwnerOrAdmin && (
            <form onSubmit={handleInvite} className="flex gap-2">
              <input
                type="email"
                value={inviteEmail}
                onChange={(e) => setInviteEmail(e.target.value)}
                placeholder="초대할 이메일"
                required
                className="flex-1 h-9 px-3 border border-gray-300 rounded-md text-sm"
              />
              <select
                value={inviteRole}
                onChange={(e) => setInviteRole(e.target.value as MemberRole)}
                className="h-9 px-2 border border-gray-300 rounded-md text-sm"
              >
                <option value="MEMBER">멤버</option>
                <option value="ADMIN">관리자</option>
              </select>
              <button type="submit" disabled={inviting} className="text-sm px-4 h-9 bg-[#03C75A] text-white rounded-md hover:bg-[#02b351] disabled:opacity-50">
                {inviting ? "초대 중..." : "초대"}
              </button>
            </form>
          )}
          <div className="border border-gray-200 rounded-xl overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100 bg-gray-50">
                  <th className="text-left px-4 py-2.5 text-xs text-gray-500 font-medium">이메일</th>
                  <th className="text-left px-4 py-2.5 text-xs text-gray-500 font-medium">역할</th>
                  <th className="text-left px-4 py-2.5 text-xs text-gray-500 font-medium">상태</th>
                  <th className="px-4 py-2.5" />
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {org.members.map((m) => (
                  <tr key={m.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 text-gray-800">{m.email}</td>
                    <td className="px-4 py-3">
                      {myRole === "OWNER" && m.role !== "OWNER" ? (
                        <select
                          value={m.role}
                          onChange={(e) => handleRoleChange(m, e.target.value as MemberRole)}
                          className="text-xs px-2 py-1 rounded border border-gray-200"
                        >
                          <option value="ADMIN">관리자</option>
                          <option value="MEMBER">멤버</option>
                        </select>
                      ) : (
                        <span className={`text-xs px-2 py-0.5 rounded font-medium ${ROLE_STYLE[m.role]}`}>{ROLE_LABEL[m.role]}</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`text-xs px-2 py-0.5 rounded ${
                        m.status === "ACCEPTED" ? "bg-green-50 text-green-700" :
                        m.status === "PENDING" ? "bg-amber-50 text-amber-700" : "bg-gray-50 text-gray-500"
                      }`}>
                        {m.status === "ACCEPTED" ? "활성" : m.status === "PENDING" ? "초대 대기" : "거절"}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      {isOwnerOrAdmin && m.role !== "OWNER" && (
                        <button onClick={() => handleRemoveMember(m)} className="text-xs text-red-500 hover:text-red-700">
                          제거
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* VM 탭 */}
      {tab === "vms" && (
        <div className="space-y-3">
          {org.vms.length === 0 ? (
            <p className="text-sm text-gray-400 py-12 text-center">연결된 VM이 없습니다.</p>
          ) : (
            org.vms.map((vm) => (
              <div key={vm.id} className="border border-gray-200 rounded-xl px-4 py-3 flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-900">{vm.name}</p>
                  <p className="text-xs text-gray-500">{vm.planType} · {vm.status}</p>
                </div>
                <div className="flex gap-2">
                  <button onClick={() => router.push(`/instances/${vm.id}`)} className="text-xs px-3 h-7 border border-gray-300 rounded-md hover:bg-gray-50">
                    상세
                  </button>
                  {myRole === "OWNER" && (
                    <button
                      onClick={async () => {
                        if (!accessToken || !confirm("VM 연결을 해제하시겠습니까?")) return;
                        try {
                          await api.org.removeVm(accessToken, id, vm.id);
                          setOrg((prev) => prev ? { ...prev, vms: prev.vms.filter((v) => v.id !== vm.id) } : prev);
                        } catch (err) {
                          alert(err instanceof Error ? err.message : "해제에 실패했습니다");
                        }
                      }}
                      className="text-xs px-3 h-7 border border-red-200 text-red-600 rounded-md hover:bg-red-50"
                    >
                      연결 해제
                    </button>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {/* 작성/수정 모달 */}
      {showWrite && (
        <CollaborationWriteModal
          accessToken={accessToken!}
          scopeType="ORGANIZATION"
          scopeId={id}
          editing={editingItem}
          onClose={() => { setShowWrite(false); setEditingItem(undefined); }}
          onSuccess={(item) => {
            if (editingItem) {
              handleItemUpdated(item);
              setEditingItem(undefined);
            } else {
              handleCreated(item);
            }
            setShowWrite(false);
          }}
        />
      )}
    </div>
  );
}
