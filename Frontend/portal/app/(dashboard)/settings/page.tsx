"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { ProfileResponse, UpgradeRequestResponse } from "@/lib/types";
import { PageLoader, Spinner } from "@/components/ui/loader";
import { Card } from "@/components/ui/panel";
import { Field, Input } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Avatar } from "@/components/ui/avatar";
import { validateProfileImage } from "@/lib/profile-image";
import { Modal } from "@/components/ui/modal";

type Tab = "profile" | "security" | "upgrade-requests";

const NAV_ITEMS: { key: Tab; label: string }[] = [
  { key: "profile", label: "프로필" },
  { key: "security", label: "보안 및 계정" },
  { key: "upgrade-requests", label: "플랜 및 사용량" },
];

export default function SettingsPage() {
  const router = useRouter();
  const { accessToken, logout } = useAuth();
  const [tab, setTab] = useState<Tab>("profile");
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [requests, setRequests] = useState<UpgradeRequestResponse[]>([]);
  const [nickname, setNickname] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveMsg, setSaveMsg] = useState<{ type: "ok" | "err"; text: string } | null>(null);
  const [withdrawConfirm, setWithdrawConfirm] = useState(false);
  const [withdrawPassword, setWithdrawPassword] = useState("");
  const [withdrawError, setWithdrawError] = useState<string | null>(null);
  const [withdrawing, setWithdrawing] = useState(false);
  const [loadingRequests, setLoadingRequests] = useState(false);
  const [requestLoading, setRequestLoading] = useState(false);
  const [selectedPlan, setSelectedPlan] = useState<"FREE" | "PRO" | null>(null);
  const [cancelingId, setCancelingId] = useState<string | null>(null);
  const [imageUploading, setImageUploading] = useState(false);
  const [imageError, setImageError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmNewPassword, setConfirmNewPassword] = useState("");
  const [changingPassword, setChangingPassword] = useState(false);
  const [passwordMsg, setPasswordMsg] = useState<{ type: "ok" | "err"; text: string } | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    api.user.profile(accessToken).then((p) => {
      setProfile(p);
      setNickname(p.nickname ?? "");
    }).catch(() => {});
  }, [accessToken]);

  useEffect(() => {
    if (!accessToken || !profile || tab !== "upgrade-requests") return;
    const timer = window.setTimeout(() => {
      setLoadingRequests(true);
      api.user.getUpgradeRequests(accessToken, profile.userId)
        .then((data) => setRequests(data))
        .catch(console.error)
        .finally(() => setLoadingRequests(false));
    }, 0);
    return () => window.clearTimeout(timer);
  }, [accessToken, profile, tab]);

  async function handleSave() {
    if (!accessToken) return;
    const trimmed = nickname.trim();
    if (trimmed && (trimmed.length < 2 || trimmed.length > 12)) {
      setSaveMsg({ type: "err", text: "닉네임은 2~12자로 입력해주세요." });
      return;
    }
    setSaving(true);
    setSaveMsg(null);
    try {
      const updated = await api.user.updateProfile(accessToken, { nickname: trimmed || undefined });
      setProfile(updated);
      setSaveMsg({ type: "ok", text: "저장되었습니다." });
    } catch {
      setSaveMsg({ type: "err", text: "저장에 실패했습니다." });
    } finally {
      setSaving(false);
    }
  }

  async function handleChangePassword() {
    if (!accessToken) return;
    setPasswordMsg(null);
    if (newPassword.length < 8) {
      setPasswordMsg({ type: "err", text: "새 비밀번호는 8자 이상이어야 합니다." });
      return;
    }
    if (newPassword !== confirmNewPassword) {
      setPasswordMsg({ type: "err", text: "새 비밀번호가 일치하지 않습니다." });
      return;
    }
    setChangingPassword(true);
    try {
      await api.auth.changePassword(accessToken, currentPassword, newPassword);
      logout();
      router.push("/login");
    } catch (err) {
      setPasswordMsg({ type: "err", text: err instanceof Error ? err.message : "비밀번호 변경에 실패했습니다." });
    } finally {
      setChangingPassword(false);
    }
  }

  async function handleWithdraw() {
    if (!accessToken || !withdrawPassword) return;
    setWithdrawing(true);
    setWithdrawError(null);
    try {
      await api.auth.withdraw(accessToken, withdrawPassword);
      logout();
      router.replace("/login");
    } catch (err) {
      setWithdrawError(err instanceof Error ? err.message : "회원 탈퇴에 실패했습니다.");
      setWithdrawing(false);
    }
  }

  function openWithdrawModal() {
    setWithdrawPassword("");
    setWithdrawError(null);
    setWithdrawConfirm(true);
  }

  function closeWithdrawModal() {
    if (withdrawing) return;
    setWithdrawConfirm(false);
    setWithdrawPassword("");
    setWithdrawError(null);
  }

  async function handleImageChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    if (!file || !accessToken) return;
    setImageError(null);
    const validationError = validateProfileImage(file);
    if (validationError) {
      setImageError(validationError);
      if (fileInputRef.current) fileInputRef.current.value = "";
      return;
    }
    setImageUploading(true);
    try {
      const updated = await api.user.uploadProfileImage(accessToken, file);
      setProfile(updated);
    } catch {
      setImageError("업로드에 실패했습니다");
    } finally {
      setImageUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  async function handleCreateRequest() {
    if (!accessToken || !profile || !selectedPlan) return;
    setRequestLoading(true);
    try {
      await api.user.createUpgradeRequest(accessToken, profile.userId, selectedPlan);
      setSelectedPlan(null);
      await new Promise(r => setTimeout(r, 500));
      const updated = await api.user.getUpgradeRequests(accessToken, profile.userId);
      setRequests(updated);
    } catch (err) {
      console.error(err);
    } finally {
      setRequestLoading(false);
    }
  }

  async function handleCancelRequest(requestId: string) {
    if (!accessToken || !profile) return;
    setCancelingId(requestId);
    try {
      await api.user.cancelUpgradeRequest(accessToken, profile.userId, requestId);
      const updated = await api.user.getUpgradeRequests(accessToken, profile.userId);
      setRequests(updated);
    } catch (err) {
      console.error(err);
    } finally {
      setCancelingId(null);
    }
  }

  if (!profile) {
    return <PageLoader />;
  }

  return (
    <div className="mx-auto max-w-[1380px]">
      <header className="mb-[22px]">
        <span className="text-[11px] font-extrabold tracking-[.11em] text-muted-soft">ACCOUNT</span>
        <h1 className="my-[5px] text-[29px] font-extrabold tracking-tight">설정</h1>
        <p className="m-0 text-sm text-muted">프로필, 플랜 및 계정 설정을 관리합니다.</p>
      </header>

      <div className="grid grid-cols-1 gap-[18px] items-start lg:grid-cols-[210px_1fr]">
        <nav className="grid grid-cols-3 gap-[5px] rounded-panel border border-line bg-panel p-2 lg:grid-cols-1">
          {NAV_ITEMS.map((item) => (
            <button
              key={item.key}
              onClick={() => setTab(item.key)}
              className={`rounded-[9px] px-3 py-[11px] text-left text-sm font-bold transition-colors ${
                tab === item.key ? "bg-soft text-brand-strong" : "text-muted hover:bg-white/[0.04]"
              }`}
            >
              {item.label}
            </button>
          ))}
        </nav>

        <div>
          {tab === "profile" && (
            <div className="space-y-4">
              <Card>
                <h2 className="text-sm font-bold mb-4">계정 정보</h2>

                <div className="mb-4 flex items-center gap-4">
                  <Avatar
                    nickname={profile.nickname}
                    email={profile.email}
                    profileImageUrl={profile.profileImageUrl}
                    sizePx={56}
                    textSizeClassName="text-lg"
                  />
                  <div>
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      className="hidden"
                      onChange={handleImageChange}
                    />
                    <Button
                      type="button"
                      variant="secondary"
                      size="small"
                      disabled={imageUploading}
                      onClick={() => fileInputRef.current?.click()}
                    >
                      {imageUploading ? "업로드 중..." : "프로필 사진 변경"}
                    </Button>
                    {imageError && <p className="mt-1.5 text-xs text-danger">{imageError}</p>}
                  </div>
                </div>

                <div className="flex flex-col gap-4">
                  <Field label="이메일" className="mb-0">
                    <div className="h-[42px] px-3 flex items-center bg-white/[0.03] border border-line rounded-[9px] text-sm text-muted select-all">
                      {profile.email}
                    </div>
                  </Field>

                  <Field label="닉네임" htmlFor="settings-nickname" className="mb-0">
                    <Input
                      id="settings-nickname"
                      name="settings-nickname"
                      value={nickname}
                      onChange={(e) => { setNickname(e.target.value); setSaveMsg(null); }}
                      placeholder="닉네임 입력 (2~12자)"
                      maxLength={12}
                    />
                  </Field>

                  <Field label="플랜" className="mb-0">
                    <div>
                      {profile.planType === "PRO" ? <Badge>PRO</Badge> : <span className="text-xs font-bold px-2 py-0.5 rounded bg-white/[0.06] text-muted">FREE</span>}
                    </div>
                  </Field>
                </div>

                <div className="flex items-center gap-3 mt-5">
                  <Button variant="primary" onClick={handleSave} disabled={saving}>
                    {saving ? "저장 중..." : "저장"}
                  </Button>
                  {saveMsg && (
                    <span className={`text-xs ${saveMsg.type === "ok" ? "text-brand-strong" : "text-danger"}`}>
                      {saveMsg.text}
                    </span>
                  )}
                </div>
              </Card>
            </div>
          )}

          {tab === "security" && (
            <div className="space-y-4">
              <Card>
                <h2 className="text-sm font-bold mb-4">비밀번호 변경</h2>

                <div className="flex flex-col gap-4">
                  <Field label="현재 비밀번호" htmlFor="settings-current-password" className="mb-0">
                    <Input
                      id="settings-current-password"
                      name="settings-current-password"
                      type="password"
                      value={currentPassword}
                      onChange={(e) => { setCurrentPassword(e.target.value); setPasswordMsg(null); }}
                      placeholder="현재 비밀번호"
                    />
                  </Field>

                  <Field label="새 비밀번호" htmlFor="settings-new-password" className="mb-0">
                    <Input
                      id="settings-new-password"
                      name="settings-new-password"
                      type="password"
                      value={newPassword}
                      onChange={(e) => { setNewPassword(e.target.value); setPasswordMsg(null); }}
                      placeholder="8자 이상"
                      minLength={8}
                    />
                  </Field>

                  <Field label="새 비밀번호 확인" htmlFor="settings-confirm-new-password" className="mb-0">
                    <Input
                      id="settings-confirm-new-password"
                      name="settings-confirm-new-password"
                      type="password"
                      value={confirmNewPassword}
                      onChange={(e) => { setConfirmNewPassword(e.target.value); setPasswordMsg(null); }}
                      placeholder="새 비밀번호 다시 입력"
                      minLength={8}
                    />
                  </Field>
                </div>

                <div className="flex items-center gap-3 mt-5">
                  <Button
                    variant="primary"
                    onClick={handleChangePassword}
                    disabled={changingPassword || !currentPassword || !newPassword || !confirmNewPassword}
                  >
                    {changingPassword ? "변경 중..." : "비밀번호 변경"}
                  </Button>
                  {passwordMsg && (
                    <span className={`text-xs ${passwordMsg.type === "ok" ? "text-brand-strong" : "text-danger"}`}>
                      {passwordMsg.text}
                    </span>
                  )}
                </div>
              </Card>

              {/* 위험 영역 */}
              <Card className="border-danger-soft">
                <h2 className="text-sm font-bold text-danger mb-1">위험 영역</h2>
                <p className="text-xs text-muted-soft mb-4">계정을 삭제하면 모든 데이터가 영구적으로 제거됩니다.</p>

                <Button variant="danger" onClick={openWithdrawModal}>
                  회원 탈퇴
                </Button>
              </Card>
            </div>
          )}

          {tab === "upgrade-requests" && (
            <div className="space-y-6">
              {/* 요청 보내기 */}
              <Card>
                <h2 className="text-sm font-bold mb-4">요청 보내기</h2>

                {requests.some(r => r.status === "PENDING") ? (
                  <div className="rounded-md border border-[#e8b657]/25 bg-[#e8b657]/[0.06] p-4">
                    <p className="text-sm text-[#e8b657]">진행 중인 요청이 있습니다</p>
                    <p className="text-xs text-[#e8b657]/80 mt-1">기존 요청이 처리될 때까지 새 요청을 보낼 수 없습니다.</p>
                  </div>
                ) : (
                  <div className="space-y-4">
                    <div>
                      <p className="text-xs font-bold text-muted mb-2">현재 플랜</p>
                      <div className="px-4 py-3 bg-white/[0.03] border border-line rounded-lg">
                        <p className="text-sm font-bold">{profile.planType}</p>
                      </div>
                    </div>

                    <div>
                      <p className="text-xs font-bold text-muted mb-2">변경할 플랜</p>
                      <div className="space-y-2">
                        {profile.planType === "FREE" ? (
                          <label htmlFor="settings-plan-pro" className="flex items-center p-3 border border-line rounded-lg cursor-pointer hover:bg-white/[0.03]">
                            <input
                              id="settings-plan-pro"
                              type="radio"
                              name="plan"
                              value="PRO"
                              checked={selectedPlan === "PRO"}
                              onChange={() => setSelectedPlan("PRO")}
                              className="w-4 h-4 accent-brand"
                            />
                            <span className="ml-3 text-sm font-bold">PRO</span>
                          </label>
                        ) : (
                          <label htmlFor="settings-plan-free" className="flex items-center p-3 border border-line rounded-lg cursor-pointer hover:bg-white/[0.03]">
                            <input
                              id="settings-plan-free"
                              type="radio"
                              name="plan"
                              value="FREE"
                              checked={selectedPlan === "FREE"}
                              onChange={() => setSelectedPlan("FREE")}
                              className="w-4 h-4 accent-brand"
                            />
                            <span className="ml-3 text-sm font-bold">FREE</span>
                          </label>
                        )}
                      </div>
                    </div>

                    <Button variant="primary" onClick={handleCreateRequest} disabled={requestLoading || !selectedPlan} className="w-full">
                      {requestLoading ? "요청 중..." : "변경 요청"}
                    </Button>
                  </div>
                )}
              </Card>

              {/* 요청 현황 */}
              <div>
                <h2 className="text-sm font-bold mb-4">요청 현황</h2>

                {loadingRequests ? (
                  <div className="flex items-center gap-2 py-6 text-muted-soft">
                    <Spinner className="w-4 h-4 text-line-strong" />
                    <span className="text-sm">불러오는 중</span>
                  </div>
                ) : requests.length === 0 ? (
                  <Card className="text-center">
                    <p className="text-sm text-muted">요청이 없습니다</p>
                  </Card>
                ) : (
                  <div className="space-y-3">
                    {requests.map((req) => (
                      <Card key={req.id}>
                        <div className="flex items-start justify-between">
                          <div>
                            <p className="text-sm font-bold">
                              {req.type === "UPGRADE" ? "업그레이드" : "다운그레이드"} 요청
                            </p>
                            <p className="text-xs text-muted mt-1">
                              대상: <span className="font-bold">{req.targetPlanType}</span>
                            </p>
                            <p className="text-xs text-muted-soft mt-1">
                              요청일: {new Date(req.createdAt).toLocaleString("ko-KR")}
                            </p>
                          </div>
                          <span className={`text-xs font-bold px-2.5 py-1 rounded whitespace-nowrap ${
                            req.status === "PENDING" ? "bg-[#e8b657]/10 text-[#e8b657]"
                            : req.status === "APPROVED" ? "bg-soft text-brand-strong"
                            : "bg-danger/10 text-danger"
                          }`}>
                            {req.status === "PENDING" ? "대기 중" : req.status === "APPROVED" ? "승인됨" : "거절됨"}
                          </span>
                        </div>
                        {req.status === "REJECTED" && req.reason && (
                          <p className="text-xs text-danger mt-3 p-2 bg-danger/10 rounded">거절 사유: {req.reason}</p>
                        )}
                        {req.status === "APPROVED" && req.reviewedAt && (
                          <p className="text-xs text-muted mt-2">승인일: {new Date(req.reviewedAt).toLocaleString("ko-KR")}</p>
                        )}
                        {req.status === "PENDING" && (
                          <button
                            onClick={() => handleCancelRequest(req.id)}
                            disabled={cancelingId === req.id}
                            className="text-xs text-danger hover:text-[#ff8686] mt-3 font-bold disabled:opacity-60"
                          >
                            {cancelingId === req.id ? "취소 중..." : "취소"}
                          </button>
                        )}
                      </Card>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      <Modal open={withdrawConfirm} onClose={closeWithdrawModal}>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            void handleWithdraw();
          }}
          className="w-[min(460px,calc(100vw-32px))] overflow-hidden rounded-2xl border border-danger-soft bg-panel shadow-2xl"
        >
          <div className="border-b border-line px-5 py-5 sm:px-6">
            <span className="text-[10px] font-extrabold tracking-[.12em] text-danger">DELETE ACCOUNT</span>
            <h2 className="mt-1 text-lg font-extrabold">회원 탈퇴 확인</h2>
            <p className="mt-2 text-sm leading-6 text-muted">
              탈퇴하면 계정과 연결된 모든 데이터가 삭제되며 되돌릴 수 없습니다. 본인 확인을 위해 현재 비밀번호를 입력해주세요.
            </p>
          </div>

          <div className="space-y-4 px-5 py-5 sm:px-6">
            <Field label="현재 비밀번호" htmlFor="withdraw-password" className="mb-0">
              <Input
                id="withdraw-password"
                name="withdraw-password"
                type="password"
                autoComplete="current-password"
                value={withdrawPassword}
                onChange={(event) => {
                  setWithdrawPassword(event.target.value);
                  setWithdrawError(null);
                }}
                placeholder="현재 비밀번호"
                required
                autoFocus
              />
            </Field>

            {withdrawError && (
              <p aria-live="polite" className="rounded-lg border border-danger-soft bg-danger/10 px-3 py-2.5 text-xs text-danger">
                {withdrawError}
              </p>
            )}

            <div className="flex flex-col-reverse gap-2 pt-1 sm:flex-row sm:justify-end">
              <Button type="button" onClick={closeWithdrawModal} disabled={withdrawing}>
                취소
              </Button>
              <Button type="submit" variant="danger-solid" disabled={withdrawing || !withdrawPassword}>
                {withdrawing ? "탈퇴 처리 중..." : "비밀번호 확인 후 탈퇴"}
              </Button>
            </div>
          </div>
        </form>
      </Modal>
    </div>
  );
}
