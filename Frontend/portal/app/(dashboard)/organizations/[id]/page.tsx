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
import { Breadcrumb } from "@/components/ui/breadcrumb";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Table, Th, Td } from "@/components/ui/table";
import { Field, Select } from "@/components/ui/field";
import { Modal } from "@/components/ui/modal";
import { isValidVmName, VmNameInput } from "@/components/vm-name-input";
import { Avatar } from "@/components/ui/avatar";
import { MemberInviteCombobox, type InviteTarget } from "@/components/member-invite-combobox";
import { maskEmail } from "@/lib/mask-email";
import { VM_PLAN_SPECS } from "@/lib/vm-plans";

const ROLE_LABEL: Record<MemberRole, string> = { OWNER: "소유자", ADMIN: "관리자", MEMBER: "멤버" };

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
  return <span className="bg-white/[0.06] text-muted text-[11px] font-bold px-1.5 py-0.5 rounded-full">{n}</span>;
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
  const [selectedInvite, setSelectedInvite] = useState<InviteTarget | null>(null);
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
  }, [accessToken, id, router]);

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
    if (!accessToken || !selectedInvite) return;
    setInviting(true);
    try {
      const member = await api.org.invite(accessToken, id, { ...selectedInvite, role: inviteRole });
      setOrg((prev) => prev ? { ...prev, members: [...prev.members, member] } : prev);
      setSelectedInvite(null);
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
    if (!isValidVmName(createVmName)) {
      setCreateVmError("인스턴스 이름은 영문, 숫자, 하이픈(-)만 사용할 수 있으며 하이픈으로 시작하거나 끝날 수 없습니다.");
      return;
    }
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
      <Breadcrumb items={[{ label: "협업", onClick: () => router.push("/organizations") }, { label: org.name }]} />

      {/* 헤더 */}
      <div className="mb-1">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <h1 className="text-[22px] font-extrabold tracking-tight">{org.name}</h1>
            <Badge>{ROLE_LABEL[org.myRole]}</Badge>
          </div>
          {myRole === "OWNER" && (
            <Button variant="danger" size="small" onClick={handleDeleteOrg}>
              <IconTrash />삭제
            </Button>
          )}
        </div>
      </div>

      {/* 탭 */}
      <div className="flex gap-0.5 border-b border-line mt-4 mb-6">
        {TABS.map(({ key, label, icon, badge }) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`flex items-center gap-1.5 px-4 py-2.5 text-[14px] border-b-2 transition-colors -mb-px ${
              tab === key
                ? "border-foreground text-foreground font-bold"
                : "border-transparent text-muted-soft hover:text-muted"
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
                  style={{ width: "auto", padding: "0 14px", height: 34, fontSize: 14 }}
                  className={`rounded-md transition-colors ${
                    typeFilter === t
                      ? "bg-white/[0.06] border border-line-strong text-foreground"
                      : "bg-transparent border-0 text-muted hover:text-foreground"
                  }`}
                >
                  {t === undefined ? "전체" : t === "NOTE" ? "메모" : t === "NOTICE" ? "공지" : "요청"}
                </button>
              ))}
            </div>
            <button
              onClick={() => { setEditingItem(undefined); setShowWrite(true); }}
              className="flex items-center gap-1 text-[13px] px-4 h-8 bg-soft text-brand-strong rounded-md hover:bg-brand/15 font-bold border-0"
              style={{ width: "auto" }}
            >
              <IconPlus />작성
            </button>
          </div>

          {items.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 bg-white/[0.02] rounded-panel border border-line">
              <span className="text-line-strong mb-3"><IconMessagePlus /></span>
              <p className="text-sm font-bold mb-1">아직 협업 항목이 없어요</p>
              <p className="mb-4 text-sm text-muted-soft">메모, 공지, 요청을 작성해서 팀원과 공유해보세요</p>
              <button
                onClick={() => { setEditingItem(undefined); setShowWrite(true); }}
                className="flex items-center gap-1 text-[13px] px-4 h-[34px] bg-soft text-brand-strong rounded-md hover:bg-brand/15 font-bold border-0"
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
            <form onSubmit={handleInvite} className="flex items-start gap-2">
              {selectedInvite ? (
                <div className="flex h-[42px] min-w-0 flex-1 items-center gap-2.5 rounded-[9px] border border-line-strong bg-panel px-3">
                  <Avatar
                    nickname={selectedInvite.nickname}
                    email={selectedInvite.email}
                    profileImageUrl={selectedInvite.profileImageUrl}
                    sizePx={26}
                    textSizeClassName="text-[11px]"
                  />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[13px] font-bold leading-4">{selectedInvite.nickname ?? selectedInvite.email}</p>
                    <p className="truncate text-[11px] leading-[14px] text-muted-soft">{maskEmail(selectedInvite.email)}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setSelectedInvite(null)}
                    className="shrink-0 text-xs font-bold text-muted-soft hover:text-muted"
                  >
                    변경
                  </button>
                </div>
              ) : accessToken ? (
                <MemberInviteCombobox accessToken={accessToken} orgId={id} onSelect={setSelectedInvite} />
              ) : null}
              <div className="w-28 shrink-0">
                <Select
                  id="org-invite-role"
                  name="org-invite-role"
                  value={inviteRole}
                  onChange={(e) => setInviteRole(e.target.value as MemberRole)}
                  className="h-[42px]"
                >
                  <option value="MEMBER">멤버</option>
                  <option value="ADMIN">관리자</option>
                </Select>
              </div>
              <Button type="submit" variant="primary" disabled={inviting || !selectedInvite} className="h-[42px] min-h-[42px] shrink-0">
                {inviting ? "초대 중..." : "초대"}
              </Button>
            </form>
          )}
          <div className="rounded-panel border border-line overflow-hidden">
            <Table>
              <thead>
                <tr>
                  <Th>사용자</Th>
                  <Th>역할</Th>
                  <Th>상태</Th>
                  <Th />
                </tr>
              </thead>
              <tbody>
                {org.members.map((m) => (
                  <tr key={m.id} className="hover:bg-white/[0.03]">
                    <Td>
                      <div className="flex items-center gap-2.5">
                        <Avatar nickname={m.nickname} email={m.email} profileImageUrl={m.profileImageUrl} sizePx={28} textSizeClassName="text-[11px]" />
                        <div className="min-w-0">
                          <p className="truncate text-sm font-bold">{m.nickname ?? m.email}</p>
                          {m.nickname && <p className="truncate text-xs text-muted-soft">{maskEmail(m.email)}</p>}
                        </div>
                      </div>
                    </Td>
                    <Td>
                      {myRole === "OWNER" && m.role !== "OWNER" ? (
                        <Select
                          id={`org-member-role-${m.id}`}
                          name={`org-member-role-${m.id}`}
                          value={m.role}
                          onChange={(e) => handleRoleChange(m, e.target.value as MemberRole)}
                          className="h-7 !min-h-0 w-[74px] py-0 pl-2 pr-6 text-[11px] font-extrabold"
                          style={{ backgroundPosition: "right 7px center", backgroundSize: "12px 12px" }}
                        >
                          <option value="ADMIN">관리자</option>
                          <option value="MEMBER">멤버</option>
                        </Select>
                      ) : (
                        <Badge>{ROLE_LABEL[m.role]}</Badge>
                      )}
                    </Td>
                    <Td>
                      <span className={`text-xs px-2 py-0.5 rounded font-bold ${
                        m.status === "ACCEPTED" ? "bg-soft text-brand-strong" :
                        m.status === "PENDING" ? "bg-[#e8b657]/10 text-[#e8b657]" : "bg-white/[0.06] text-muted"
                      }`}>
                        {m.status === "ACCEPTED" ? "활성" : m.status === "PENDING" ? "초대 대기" : "거절"}
                      </span>
                    </Td>
                    <Td className="text-right">
                      {isOwnerOrAdmin && m.role !== "OWNER" && (
                        <button onClick={() => handleRemoveMember(m)} className="text-xs text-danger hover:text-[#ff8686] font-bold">
                          제거
                        </button>
                      )}
                    </Td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </div>
        </div>
      )}

      {/* ── VM 탭 ── */}
      {tab === "vms" && (
        <div className="space-y-3">
          {isOwnerOrAdmin && (
            <div className="flex gap-2 justify-end">
              <Button size="small" onClick={() => { setCreateVmName(""); setCreateVmPlan("FREE"); setCreateVmDisk(20); setCreateVmError(null); setShowCreateVm(true); }}>
                <IconPlus />새 VM 생성
              </Button>
              <Button size="small" variant="ghost" onClick={() => setShowAddVm(true)}>
                <IconPlus />기존 VM 연결
              </Button>
            </div>
          )}

          {org.vms.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 bg-white/[0.02] rounded-panel border border-line">
              <span className="text-line-strong mb-3">
                <IconServer />
              </span>
              <p className="text-sm font-bold mb-1">연결된 VM이 없어요</p>
              <p className="text-[13px] text-muted-soft mb-4">VM을 연결하면 팀원과 함께 협업할 수 있어요</p>
              {isOwnerOrAdmin && (
                <button
                  onClick={() => setShowAddVm(true)}
                  className="flex items-center gap-1 text-[13px] px-4 h-[34px] bg-soft text-brand-strong rounded-md hover:bg-brand/15 font-bold border-0"
                  style={{ width: "auto" }}
                >
                  <IconPlus />VM 연결하기
                </button>
              )}
            </div>
          ) : (
            org.vms.map((vm) => (
              <div key={vm.id} className="relative rounded-panel border border-line hover:border-line-strong transition-colors">
                <Link
                  href={`/instances/${vm.id}?from=org&orgId=${id}`}
                  className="flex items-center justify-between px-4 py-3"
                >
                  <div>
                    <div className="flex items-baseline gap-2">
                      <p className="text-sm font-bold">{vm.name}</p>
                      {vm.subdomain && (
                        <span className="text-xs text-muted-soft font-mono">{vm.subdomain}.gamjabox.cloud</span>
                      )}
                    </div>
                    <p className="text-xs text-muted mt-0.5">{vm.planType} · {vm.status}</p>
                  </div>
                </Link>
                {myRole === "OWNER" && (
                  <button
                    onClick={(e) => { e.preventDefault(); handleRemoveVm(vm.id); }}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-xs px-3 h-7 border border-danger-soft text-danger rounded-md hover:bg-danger/10"
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
      <CollaborationWriteModal
          open={showWrite}
          accessToken={accessToken!}
          scopeType="ORGANIZATION"
          scopeId={id}
          editing={editingItem}
          onClose={() => setShowWrite(false)}
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

      {/* ── VM 연결 모달 ── */}
      <Modal open={showAddVm} onClose={() => setShowAddVm(false)}>
        <div className="mx-auto w-full max-w-sm rounded-panel bg-panel">
          <div className="flex items-center justify-between px-5 py-4 border-b border-line">
            <h2 className="text-sm font-bold">기존 VM 연결</h2>
            <button onClick={() => setShowAddVm(false)} className="text-muted-soft hover:text-muted text-lg leading-none">×</button>
          </div>
          <form onSubmit={handleAddVm} className="p-5 space-y-4">
            {availableVms.length === 0 ? (
              <div className="text-center py-6">
                <p className="text-sm text-muted">연결 가능한 VM이 없습니다.</p>
                <p className="text-xs text-muted-soft mt-1">이미 모든 VM이 연결됐거나 VM이 없어요.</p>
              </div>
            ) : (
              <div className="space-y-2 max-h-64 overflow-y-auto">
                {availableVms.map((vm) => (
                  <label
                    key={vm.id}
                    htmlFor={`org-collab-vm-${vm.id}`}
                    className={`flex items-center gap-3 px-3 py-2.5 rounded-lg border cursor-pointer transition-colors ${
                      selectedVmId === vm.id ? "border-brand bg-soft" : "border-line hover:border-line-strong"
                    }`}
                  >
                    <input
                      id={`org-collab-vm-${vm.id}`}
                      type="radio"
                      name="vm"
                      value={vm.id}
                      checked={selectedVmId === vm.id}
                      onChange={() => setSelectedVmId(vm.id)}
                      className="accent-brand"
                    />
                    <div className="min-w-0">
                      <p className="text-sm font-bold truncate">{vm.name}</p>
                      {vm.subdomain && <p className="text-xs text-muted-soft font-mono truncate">{vm.subdomain}.gamjabox.cloud</p>}
                      <p className="text-xs text-muted-soft">{vm.planType} · {vm.status}</p>
                    </div>
                  </label>
                ))}
              </div>
            )}
            <div className="flex gap-2 justify-end pt-1">
              <Button type="button" onClick={() => setShowAddVm(false)}>
                취소
              </Button>
              {availableVms.length > 0 && (
                <Button type="submit" variant="primary" disabled={!selectedVmId || addingVm}>
                  {addingVm ? "연결 중..." : "연결"}
                </Button>
              )}
            </div>
          </form>
        </div>
      </Modal>

      {/* ── VM 생성 모달 ── */}
      {showCreateVm && (() => {
        const freeFull = vmAvailability?.free.isFull ?? false;
        const proFull = vmAvailability?.pro.isFull ?? false;
        const planInfo = VM_PLAN_SPECS[createVmPlan];
        return (
          <Modal open={showCreateVm} onClose={() => setShowCreateVm(false)}>
            <div className="mx-auto w-full max-w-md rounded-panel bg-panel">
              <div className="flex items-center justify-between px-5 py-4 border-b border-line">
                <h2 className="text-sm font-bold">새 VM 생성 후 협업에 추가</h2>
                <button onClick={() => setShowCreateVm(false)} className="text-muted-soft hover:text-muted text-lg leading-none">×</button>
              </div>
              <form onSubmit={handleCreateVm} className="p-5 space-y-4">
                <Field label="인스턴스 이름" htmlFor="org-create-vm-name">
                  <VmNameInput
                    id="org-create-vm-name"
                    name="org-create-vm-name"
                    value={createVmName}
                    onValueChange={setCreateVmName}
                    placeholder="my-server"
                    required
                  />
                </Field>

                {/* 플랜 */}
                <div>
                  <span className="text-xs font-bold text-muted block mb-2">플랜</span>
                  <div className="grid grid-cols-2 gap-2">
                    {(["FREE", "PRO"] as const).map((plan) => {
                      const info = VM_PLAN_SPECS[plan];
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
                          className={`relative rounded-[12px] border p-3 text-left transition-colors ${selected ? "border-brand shadow-[inset_0_0_0_1px_var(--brand)]" : "border-line-strong hover:border-white/20"} ${(full || planLocked) ? "opacity-50 cursor-not-allowed" : ""}`}
                        >
                          {selected && <span className="absolute -top-2 left-2.5 bg-brand text-[#0a0c08] text-[10px] font-bold px-1.5 py-0.5 rounded">선택됨</span>}
                          {planLocked && <span className="absolute -top-2 right-2.5 bg-[#e8b657]/10 text-[#e8b657] text-[10px] font-bold px-1.5 py-0.5 rounded">프로 플랜만</span>}
                          <p className="text-sm font-bold mb-0.5">{plan}</p>
                          <p className="text-xs text-muted mb-1.5">{info.cores} vCPU · {info.memory} RAM</p>
                          <div className="flex items-center gap-1.5">
                            <div className="flex gap-0.5">
                              {Array.from({ length: total }).map((_, i) => (
                                <span key={i} className={`w-1.5 h-1.5 rounded-full ${i < used ? (full ? "bg-danger" : "bg-brand") : "bg-line-strong"}`} />
                              ))}
                            </div>
                            <span className={`text-[11px] ${full ? "text-danger font-bold" : "text-muted-soft"}`}>
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
                    <label htmlFor="org-create-vm-disk" className="text-xs font-bold text-muted">디스크 크기</label>
                    <span className="text-sm font-bold">{createVmDisk}GB</span>
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
                    className="w-full accent-brand"
                  />
                  <div className="flex justify-between text-[11px] text-muted-soft mt-0.5">
                    <span>{planInfo.diskMin}GB</span>
                    <span>{planInfo.diskMax}GB</span>
                  </div>
                </div>

                <Field label="SSH 키" htmlFor="org-create-vm-ssh-key">
                  {sshKeys.length === 0 ? (
                    <div className="text-xs text-muted-soft border border-dashed border-line-strong rounded-md px-3 py-2.5 font-normal">
                      등록된 SSH 키가 없습니다.{" "}
                      <a href="/ssh-keys" className="text-brand-strong font-bold">SSH 키 등록하기</a>
                    </div>
                  ) : (
                    <Select
                      id="org-create-vm-ssh-key"
                      name="org-create-vm-ssh-key"
                      value={createVmSshKeyId}
                      onChange={(e) => setCreateVmSshKeyId(e.target.value)}
                    >
                      {sshKeys.map((key) => (
                        <option key={key.id} value={key.id}>{key.name}</option>
                      ))}
                    </Select>
                  )}
                </Field>

                {createVmError && <p className="text-xs text-danger">{createVmError}</p>}

                <div className="flex gap-2 justify-end pt-1">
                  <Button type="button" onClick={() => setShowCreateVm(false)}>
                    취소
                  </Button>
                  <Button type="submit" variant="primary" disabled={createVmLoading || !isValidVmName(createVmName) || !createVmSshKeyId}>
                    {createVmLoading ? "생성 중..." : "생성 및 추가"}
                  </Button>
                </div>
              </form>
            </div>
          </Modal>
        );
      })()}
    </div>
  );
}
