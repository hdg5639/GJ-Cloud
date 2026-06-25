"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { CollaborationResponse, CollaborationType, MemberResponse, MemberRole, OrgDetailResponse, VmAvailabilityResponse, SshKeyResponse, VmResponse } from "@/lib/types";
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

// ── SVG 아이콘 ──────────────────────────────────────────
function IconMessages() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-[15px] h-[15px]"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>;
}
function IconUsers() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-[15px] h-[15px]"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>;
}
function IconServer() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-[15px] h-[15px]"><rect x="2" y="2" width="20" height="8" rx="2"/><rect x="2" y="14" width="20" height="8" rx="2"/><line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/></svg>;
}
function IconPlus() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-[14px] h-[14px]"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>;
}
function IconTrash() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-[15px] h-[15px]"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>;
}
function IconMessagePlus() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="w-8 h-8"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="10" y1="11" x2="14" y2="11"/></svg>;
}

// ── 뱃지 ─────────────────────────────────────────────────
function CountBadge({ n }: { n: number }) {
  return <span className="bg-gray-100 text-gray-500 text-[11px] font-medium px-1.5 py-0.5 rounded-full">{n}</span>;
}

export default function OrganizationDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { accessToken } = useAuth();
  const router = useRouter();

  const [org, setOrg] = useState<OrgDetailResponse | null>(null);
  const [tab, setTab] = useState<Tab>("collab");
  const [items, setItems] = useState<CollaborationResponse[]>([]);
  const [typeFilter, setTypeFilter] = useState<CollaborationType | undefined>();
  const [showWrite, setShowWrite] = useState(false);
  const [editingItem, setEditingItem] = useState<CollaborationResponse | undefined>();

  // 멤버 초대
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<MemberRole>("MEMBER");
  const [inviting, setInviting] = useState(false);

  // VM 연결
  const [showAddVm, setShowAddVm] = useState(false);
  const [allVms, setAllVms] = useState<VmResponse[]>([]);
  const [selectedVmId, setSelectedVmId] = useState("");
  const [addingVm, setAddingVm] = useState(false);

  // VM 생성
  const [showCreateVm, setShowCreateVm] = useState(false);
  const [createVmName, setCreateVmName] = useState("");
  const [createVmPlan, setCreateVmPlan] = useState<"FREE" | "PRO">("FREE");
  const [createVmDisk, setCreateVmDisk] = useState(20);
  const [createVmSshKeyId, setCreateVmSshKeyId] = useState("");
  const [createVmLoading, setCreateVmLoading] = useState(false);
  const [createVmError, setCreateVmError] = useState<string | null>(null);
  const [vmAvailability, setVmAvailability] = useState<VmAvailabilityResponse | null>(null);
  const [sshKeys, setSshKeys] = useState<SshKeyResponse[]>([]);
  const [userPlan, setUserPlan] = useState<string | null>(null);
  const [currentUserId, setCurrentUserId] = useState<string | undefined>();

  useEffect(() => {
    if (!accessToken) return;
    api.org.get(accessToken, id).then(setOrg).catch(() => router.push("/organizations"));
    api.user.profile(accessToken).then((p) => setCurrentUserId(p.userId)).catch(() => {});
  }, [accessToken, id]);

  useEffect(() => {
    if (!accessToken || !org) return;
    api.collab.list(accessToken, "ORGANIZATION", id, typeFilter).then(setItems).catch(() => {});
  }, [accessToken, id, typeFilter, org]);

  // VM 탭 진입 시 사용자 VM 목록 로드
  useEffect(() => {
    if (tab !== "vms" || !accessToken) return;
    api.vm.list(accessToken).then(setAllVms).catch(() => {});
  }, [tab, accessToken]);

  // VM 생성 모달 열릴 때 필요 데이터 로드
  useEffect(() => {
    if (!showCreateVm || !accessToken) return;
    Promise.all([
      api.vm.availability(accessToken),
      api.user.profile(accessToken),
      api.user.sshKeys(accessToken),
    ]).then(([avail, profile, keys]) => {
      setVmAvailability(avail);
      setUserPlan(profile.planType);
      setCurrentUserId(profile.userId);
      setSshKeys(keys);
      if (keys.length > 0) setCreateVmSshKeyId(keys[0].id);
    }).catch(() => {});
  }, [showCreateVm, accessToken]);

  const myRole = org?.myRole;
  const isOwnerOrAdmin = myRole === "OWNER" || myRole === "ADMIN";
  const connectedVmIds = new Set(org?.vms.map((v) => v.id) ?? []);
  const availableVms = allVms.filter((v) => !connectedVmIds.has(v.id) && v.status !== "DELETED");

  // ── 협업 핸들러 ──
  function handleItemUpdated(updated: CollaborationResponse) {
    setItems((prev) => prev.map((i) => (i.id === updated.id ? updated : i)));
  }
  function handleItemDeleted(itemId: string) {
    setItems((prev) => prev.filter((i) => i.id !== itemId));
  }

  // ── 멤버 핸들러 ──
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
    if (!accessToken || !confirm(`${member.email}을 제거하시겠습니까?`)) return;
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

  // ── VM 핸들러 ──
  async function handleAddVm(e: React.FormEvent) {
    e.preventDefault();
    if (!accessToken || !selectedVmId) return;
    setAddingVm(true);
    try {
      await api.org.addVm(accessToken, id, selectedVmId);
      const vm = allVms.find((v) => v.id === selectedVmId)!;
      setOrg((prev) => prev ? { ...prev, vms: [...prev.vms, vm] } : prev);
      setShowAddVm(false);
      setSelectedVmId("");
    } catch (err) {
      alert(err instanceof Error ? err.message : "VM 연결에 실패했습니다");
    } finally {
      setAddingVm(false);
    }
  }

  async function handleRemoveVm(vmId: string) {
    if (!accessToken || !confirm("VM 연결을 해제하시겠습니까?")) return;
    try {
      await api.org.removeVm(accessToken, id, vmId);
      setOrg((prev) => prev ? { ...prev, vms: prev.vms.filter((v) => v.id !== vmId) } : prev);
    } catch (err) {
      alert(err instanceof Error ? err.message : "해제에 실패했습니다");
    }
  }

  function handleCreateVmPlanChange(plan: "FREE" | "PRO") {
    setCreateVmPlan(plan);
    setCreateVmDisk(plan === "FREE" ? 20 : 20);
  }

  async function handleCreateVm(e: React.FormEvent) {
    e.preventDefault();
    if (!accessToken) return;
    setCreateVmError(null);
    setCreateVmLoading(true);
    try {
      const vm = await api.vm.create(accessToken, {
        name: createVmName,
        planType: createVmPlan,
        diskSizeGb: createVmDisk,
        sshKeyId: createVmSshKeyId,
      });
      await api.org.addVm(accessToken, id, vm.id);
      setOrg((prev) => prev ? { ...prev, vms: [...prev.vms, vm] } : prev);
      setShowCreateVm(false);
      setCreateVmName("");
      setCreateVmPlan("FREE");
      setCreateVmDisk(20);
      setCreateVmError(null);
    } catch (err) {
      setCreateVmError(err instanceof Error ? err.message : "생성에 실패했습니다");
    } finally {
      setCreateVmLoading(false);
    }
  }

  async function handleDeleteOrg() {
    if (!accessToken || !confirm(`"${org?.name}" Organization을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.`)) return;
    try {
      await api.org.delete(accessToken, id);
      router.push("/organizations");
    } catch (err) {
      alert(err instanceof Error ? err.message : "삭제에 실패했습니다");
    }
  }

  if (!org) return <PageLoader />;

  const acceptedCount = org.members.filter((m) => m.status === "ACCEPTED").length;

  // ── 탭 정의 ──
  const TABS: { key: Tab; label: string; icon: React.ReactNode; badge?: number }[] = [
    { key: "collab",   label: "협업", icon: <IconMessages /> },
    { key: "members",  label: "멤버", icon: <IconUsers />,  badge: acceptedCount },
    { key: "vms",      label: "VM",   icon: <IconServer />, badge: org.vms.length },
  ];

  return (
    <div>
      {/* 브레드크럼 */}
      <div className="flex items-center gap-2 mb-1">
        <button onClick={() => router.push("/organizations")} className="text-xs text-gray-500 hover:text-gray-700">
          협업
        </button>
        <span className="text-xs text-gray-400">/</span>
        <span className="text-xs text-gray-700">{org.name}</span>
      </div>

      {/* 헤더 */}
      <div className="mb-1">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <h1 className="text-[22px] font-medium text-gray-900">{org.name}</h1>
            <span className={`text-xs font-medium px-2.5 py-0.5 rounded-md ${ROLE_STYLE[org.myRole]}`}>{ROLE_LABEL[org.myRole]}</span>
          </div>
          {myRole === "OWNER" && (
            <button
              onClick={handleDeleteOrg}
              className="flex items-center gap-1.5 text-sm px-3 h-8 border border-red-200 text-red-600 rounded-md hover:bg-red-50"
            >
              <IconTrash />삭제
            </button>
          )}
        </div>
      </div>

      {/* 탭 */}
      <div className="flex gap-0.5 border-b border-gray-200/70 mt-4 mb-6">
        {TABS.map(({ key, label, icon, badge }) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`flex items-center gap-1.5 px-4 py-2.5 text-[14px] border-b-2 transition-colors -mb-px ${
              tab === key
                ? "border-gray-900 text-gray-900 font-medium"
                : "border-transparent text-gray-400 hover:text-gray-600"
            }`}
          >
            {icon}
            {label}
            {badge !== undefined && <CountBadge n={badge} />}
          </button>
        ))}
      </div>

      {/* ── 협업 탭 ── */}
      {tab === "collab" && (
        <div>
          <div className="flex items-center justify-between mb-5">
            <div className="flex gap-1.5">
              {([undefined, "NOTE", "NOTICE", "REQUEST"] as (CollaborationType | undefined)[]).map((t) => (
                <button
                  key={t ?? "all"}
                  onClick={() => setTypeFilter(t)}
                  style={{ width: "auto", padding: "0 14px", height: 32, fontSize: 13 }}
                  className={`rounded-md transition-colors ${
                    typeFilter === t
                      ? "bg-gray-100 border border-gray-300 text-gray-800"
                      : "bg-transparent border-0 text-gray-500 hover:text-gray-700"
                  }`}
                >
                  {t === undefined ? "전체" : t === "NOTE" ? "메모" : t === "NOTICE" ? "공지" : "요청"}
                </button>
              ))}
            </div>
            <button
              onClick={() => { setEditingItem(undefined); setShowWrite(true); }}
              className="flex items-center gap-1 text-[13px] px-4 h-8 bg-[#03C75A]/10 text-[#03C75A] rounded-md hover:bg-[#03C75A]/20 font-medium border-0"
              style={{ width: "auto" }}
            >
              <IconPlus />작성
            </button>
          </div>

          {items.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 bg-gray-50 rounded-xl">
              <span className="text-gray-300 mb-3"><IconMessagePlus /></span>
              <p className="text-sm font-medium text-gray-700 mb-1">아직 협업 항목이 없어요</p>
              <p className="text-[13px] text-gray-400 mb-4">메모, 공지, 요청을 작성해서 팀원과 공유해보세요</p>
              <button
                onClick={() => { setEditingItem(undefined); setShowWrite(true); }}
                className="flex items-center gap-1 text-[13px] px-4 h-[34px] bg-[#03C75A]/10 text-[#03C75A] rounded-md hover:bg-[#03C75A]/20 font-medium border-0"
                style={{ width: "auto" }}
              >
                <IconPlus />첫 항목 작성하기
              </button>
            </div>
          ) : (
            <div className="flex flex-col gap-2.5">
              {items.map((item) => (
                <CollaborationCard
                  key={item.id}
                  item={item}
                  accessToken={accessToken!}
                  isOwnerOrAdmin={isOwnerOrAdmin}
                  currentUserId={currentUserId}
                  onEdit={(i) => { setEditingItem(i); setShowWrite(true); }}
                  onUpdate={handleItemUpdated}
                  onDelete={handleItemDeleted}
                />
              ))}
            </div>
          )}
        </div>
      )}

      {/* ── 멤버 탭 ── */}
      {tab === "members" && (
        <div className="space-y-4">
          {isOwnerOrAdmin && (
            <form onSubmit={handleInvite} className="flex gap-2">
              <input
                id="org-invite-email"
                name="org-invite-email"
                type="email"
                value={inviteEmail}
                onChange={(e) => setInviteEmail(e.target.value)}
                placeholder="초대할 이메일"
                required
                className="flex-1 h-9 px-3 border border-gray-300 rounded-md text-sm"
              />
              <select
                id="org-invite-role"
                name="org-invite-role"
                value={inviteRole}
                onChange={(e) => setInviteRole(e.target.value as MemberRole)}
                className="h-9 px-2 border border-gray-300 rounded-md text-sm"
              >
                <option value="MEMBER">멤버</option>
                <option value="ADMIN">관리자</option>
              </select>
              <button type="submit" disabled={inviting} className="text-sm px-4 h-9 bg-[#03C75A] text-white rounded-md hover:bg-[#02b351] disabled:opacity-50" style={{ width: "auto" }}>
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
                          id={`org-member-role-${m.id}`}
                          name={`org-member-role-${m.id}`}
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

      {/* ── VM 탭 ── */}
      {tab === "vms" && (
        <div className="space-y-3">
          {isOwnerOrAdmin && (
            <div className="flex gap-2 justify-end">
              <button
                onClick={() => { setCreateVmName(""); setCreateVmPlan("FREE"); setCreateVmDisk(20); setCreateVmError(null); setShowCreateVm(true); }}
                className="flex items-center gap-1.5 text-sm px-3 h-8 border border-gray-300 rounded-md hover:bg-gray-50"
                style={{ width: "auto" }}
              >
                <IconPlus />새 VM 생성
              </button>
              <button
                onClick={() => setShowAddVm(true)}
                className="flex items-center gap-1.5 text-sm px-3 h-8 bg-[#03C75A]/10 text-[#03C75A] rounded-md hover:bg-[#03C75A]/20 border-0 font-medium"
                style={{ width: "auto" }}
              >
                <IconPlus />기존 VM 연결
              </button>
            </div>
          )}

          {org.vms.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 bg-gray-50 rounded-xl">
              <span className="text-gray-300 mb-3">
                <IconServer />
              </span>
              <p className="text-sm font-medium text-gray-700 mb-1">연결된 VM이 없어요</p>
              <p className="text-[13px] text-gray-400 mb-4">VM을 연결하면 팀원과 함께 협업할 수 있어요</p>
              {isOwnerOrAdmin && (
                <button
                  onClick={() => setShowAddVm(true)}
                  className="flex items-center gap-1 text-[13px] px-4 h-[34px] bg-[#03C75A]/10 text-[#03C75A] rounded-md hover:bg-[#03C75A]/20 font-medium border-0"
                  style={{ width: "auto" }}
                >
                  <IconPlus />VM 연결하기
                </button>
              )}
            </div>
          ) : (
            org.vms.map((vm) => (
              <div key={vm.id} className="relative border border-gray-200 rounded-xl hover:border-gray-300 transition-colors">
                <Link
                  href={`/instances/${vm.id}?from=org&orgId=${id}`}
                  className="flex items-center justify-between px-4 py-3"
                >
                  <div>
                    <div className="flex items-baseline gap-2">
                      <p className="text-sm font-medium text-gray-900">{vm.name}</p>
                      {vm.subdomain && (
                        <span className="text-xs text-gray-400 font-mono">{vm.subdomain}.gamjabox.cloud</span>
                      )}
                    </div>
                    <p className="text-xs text-gray-500 mt-0.5">{vm.planType} · {vm.status}</p>
                  </div>
                </Link>
                {myRole === "OWNER" && (
                  <button
                    onClick={(e) => { e.preventDefault(); handleRemoveVm(vm.id); }}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-xs px-3 h-7 border border-red-200 text-red-600 rounded-md hover:bg-red-50"
                    style={{ width: "auto" }}
                  >
                    연결 해제
                  </button>
                )}
              </div>
            ))
          )}
        </div>
      )}

      {/* ── 협업 작성/수정 모달 ── */}
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
              setItems((prev) => [item, ...prev]);
            }
            setShowWrite(false);
          }}
        />
      )}

      {/* ── VM 연결 모달 ── */}
      {showAddVm && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-sm">
            <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
              <h2 className="text-sm font-medium text-gray-900">기존 VM 연결</h2>
              <button onClick={() => setShowAddVm(false)} className="text-gray-400 hover:text-gray-600 text-lg leading-none">×</button>
            </div>
            <form onSubmit={handleAddVm} className="p-5 space-y-4">
              {availableVms.length === 0 ? (
                <div className="text-center py-6">
                  <p className="text-sm text-gray-500">연결 가능한 VM이 없습니다.</p>
                  <p className="text-xs text-gray-400 mt-1">이미 모든 VM이 연결됐거나 VM이 없어요.</p>
                </div>
              ) : (
                <div className="space-y-2 max-h-64 overflow-y-auto">
                  {availableVms.map((vm) => (
                    <label
                      key={vm.id}
                      className={`flex items-center gap-3 px-3 py-2.5 rounded-lg border cursor-pointer transition-colors ${
                        selectedVmId === vm.id ? "border-[#03C75A] bg-[#03C75A]/5" : "border-gray-200 hover:border-gray-300"
                      }`}
                    >
                      <input
                        type="radio"
                        name="vm"
                        value={vm.id}
                        checked={selectedVmId === vm.id}
                        onChange={() => setSelectedVmId(vm.id)}
                        className="accent-[#03C75A]"
                      />
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-gray-900 truncate">{vm.name}</p>
                        {vm.subdomain && <p className="text-xs text-gray-400 font-mono truncate">{vm.subdomain}.gamjabox.cloud</p>}
                        <p className="text-xs text-gray-400">{vm.planType} · {vm.status}</p>
                      </div>
                    </label>
                  ))}
                </div>
              )}
              <div className="flex gap-2 justify-end pt-1">
                <button type="button" onClick={() => setShowAddVm(false)} className="text-sm px-4 h-8 border border-gray-300 rounded-md hover:bg-gray-50" style={{ width: "auto" }}>
                  취소
                </button>
                {availableVms.length > 0 && (
                  <button type="submit" disabled={!selectedVmId || addingVm} className="text-sm px-4 h-8 bg-[#03C75A] text-white rounded-md hover:bg-[#02b351] disabled:opacity-50" style={{ width: "auto" }}>
                    {addingVm ? "연결 중..." : "연결"}
                  </button>
                )}
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── VM 생성 모달 ── */}
      {showCreateVm && (() => {
        const PLAN_INFO = {
          FREE: { cores: 4, memory: "5GB", diskMin: 20, diskMax: 50 },
          PRO: { cores: 8, memory: "12GB", diskMin: 20, diskMax: 100 },
        };
        const freeFull = vmAvailability?.free.isFull ?? false;
        const proFull = vmAvailability?.pro.isFull ?? false;
        const planInfo = PLAN_INFO[createVmPlan];
        return (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
            <div className="bg-white rounded-xl shadow-xl w-full max-w-md">
              <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
                <h2 className="text-sm font-medium text-gray-900">새 VM 생성 후 협업에 추가</h2>
                <button onClick={() => setShowCreateVm(false)} className="text-gray-400 hover:text-gray-600 text-lg leading-none">×</button>
              </div>
              <form onSubmit={handleCreateVm} className="p-5 space-y-4">
                {/* 이름 */}
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="org-create-vm-name" className="text-xs text-gray-500">인스턴스 이름</label>
                  <input
                    id="org-create-vm-name"
                    name="org-create-vm-name"
                    value={createVmName}
                    onChange={(e) => setCreateVmName(e.target.value)}
                    placeholder="my-server"
                    required
                    className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-[#03C75A]/30 focus:border-[#03C75A]"
                  />
                </div>

                {/* 플랜 */}
                <div>
                  <label className="text-xs text-gray-500 block mb-2">플랜</label>
                  <div className="grid grid-cols-2 gap-2">
                    {(["FREE", "PRO"] as const).map((plan) => {
                      const info = PLAN_INFO[plan];
                      const full = plan === "FREE" ? freeFull : proFull;
                      const planLocked = plan === "PRO" && userPlan === "FREE";
                      const used = vmAvailability?.[plan.toLowerCase() as "free" | "pro"].used ?? 0;
                      const total = vmAvailability?.[plan.toLowerCase() as "free" | "pro"].total ?? 0;
                      const selected = createVmPlan === plan;
                      return (
                        <button
                          key={plan}
                          type="button"
                          disabled={full || planLocked}
                          onClick={() => handleCreateVmPlanChange(plan)}
                          className={`relative border rounded-lg p-3 text-left transition-colors ${selected ? "border-2 border-[#03C75A]" : "border-gray-200 hover:border-gray-300"} ${(full || planLocked) ? "opacity-50 cursor-not-allowed" : ""}`}
                        >
                          {selected && <span className="absolute -top-2 left-2.5 bg-[#e6faf0] text-[#03C75A] text-[10px] font-medium px-1.5 py-0.5 rounded">선택됨</span>}
                          {planLocked && <span className="absolute -top-2 right-2.5 bg-amber-100 text-amber-700 text-[10px] font-medium px-1.5 py-0.5 rounded">프로 플랜만</span>}
                          <p className="text-sm font-medium text-gray-900 mb-0.5">{plan}</p>
                          <p className="text-xs text-gray-500 mb-1.5">{info.cores} vCPU · {info.memory} RAM</p>
                          <div className="flex items-center gap-1.5">
                            <div className="flex gap-0.5">
                              {Array.from({ length: total }).map((_, i) => (
                                <span key={i} className={`w-1.5 h-1.5 rounded-full ${i < used ? (full ? "bg-red-500" : "bg-[#03C75A]") : "bg-gray-200"}`} />
                              ))}
                            </div>
                            <span className={`text-[11px] ${full ? "text-red-600 font-medium" : "text-gray-400"}`}>
                              {full ? `자리 없음 (${used}/${total})` : `${used}/${total} 사용 중`}
                            </span>
                          </div>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* 디스크 */}
                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <label htmlFor="org-create-vm-disk" className="text-xs text-gray-500">디스크 크기</label>
                    <span className="text-sm font-medium text-gray-900">{createVmDisk}GB</span>
                  </div>
                  <input
                    id="org-create-vm-disk"
                    name="org-create-vm-disk"
                    type="range"
                    min={planInfo.diskMin}
                    max={planInfo.diskMax}
                    step={5}
                    value={createVmDisk}
                    onChange={(e) => setCreateVmDisk(Number(e.target.value))}
                    className="w-full accent-[#03C75A]"
                  />
                  <div className="flex justify-between text-[11px] text-gray-400 mt-0.5">
                    <span>{planInfo.diskMin}GB</span>
                    <span>{planInfo.diskMax}GB</span>
                  </div>
                </div>

                {/* SSH 키 */}
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="org-create-vm-ssh-key" className="text-xs text-gray-500">SSH 키</label>
                  {sshKeys.length === 0 ? (
                    <div className="text-xs text-gray-400 border border-dashed border-gray-300 rounded-md px-3 py-2.5">
                      등록된 SSH 키가 없습니다.{" "}
                      <a href="/ssh-keys" className="text-[#03C75A] font-medium">SSH 키 등록하기</a>
                    </div>
                  ) : (
                    <select
                      id="org-create-vm-ssh-key"
                      name="org-create-vm-ssh-key"
                      value={createVmSshKeyId}
                      onChange={(e) => setCreateVmSshKeyId(e.target.value)}
                      className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-[#03C75A]/30 focus:border-[#03C75A]"
                    >
                      {sshKeys.map((key) => (
                        <option key={key.id} value={key.id}>{key.name}</option>
                      ))}
                    </select>
                  )}
                </div>

                {createVmError && <p className="text-xs text-red-600">{createVmError}</p>}

                <div className="flex gap-2 justify-end pt-1">
                  <button type="button" onClick={() => setShowCreateVm(false)} className="text-sm px-4 h-9 border border-gray-300 rounded-md hover:bg-gray-50" style={{ width: "auto" }}>
                    취소
                  </button>
                  <button
                    type="submit"
                    disabled={createVmLoading || !createVmName || !createVmSshKeyId}
                    className="text-sm px-4 h-9 bg-[#03C75A] text-white rounded-md hover:bg-[#02b351] disabled:opacity-50"
                    style={{ width: "auto" }}
                  >
                    {createVmLoading ? "생성 중..." : "생성 및 추가"}
                  </button>
                </div>
              </form>
            </div>
          </div>
        );
      })()}
    </div>
  );
}
