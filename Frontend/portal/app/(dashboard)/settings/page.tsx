"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { ProfileResponse } from "@/lib/types";

export default function SettingsPage() {
  const router = useRouter();
  const { accessToken, logout } = useAuth();
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [nickname, setNickname] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveMsg, setSaveMsg] = useState<{ type: "ok" | "err"; text: string } | null>(null);
  const [withdrawConfirm, setWithdrawConfirm] = useState(false);
  const [withdrawing, setWithdrawing] = useState(false);

  useEffect(() => {
    if (!accessToken) return;
    api.user.profile(accessToken).then((p) => {
      setProfile(p);
      setNickname(p.nickname ?? "");
    }).catch(() => {});
  }, [accessToken]);

  async function handleSave() {
    if (!accessToken) return;
    setSaving(true);
    setSaveMsg(null);
    try {
      const updated = await api.user.updateProfile(accessToken, { nickname: nickname.trim() || undefined });
      setProfile(updated);
      setSaveMsg({ type: "ok", text: "저장되었습니다." });
    } catch {
      setSaveMsg({ type: "err", text: "저장에 실패했습니다." });
    } finally {
      setSaving(false);
    }
  }

  async function handleWithdraw() {
    if (!accessToken) return;
    setWithdrawing(true);
    try {
      await api.auth.withdraw(accessToken);
      logout();
      router.push("/login");
    } catch {
      setWithdrawConfirm(false);
      setWithdrawing(false);
    }
  }

  if (!profile) {
    return <p className="text-sm text-gray-400">불러오는 중...</p>;
  }

  return (
    <div className="max-w-lg">
      <h1 className="text-lg font-medium text-gray-900 mb-6">프로필 설정</h1>

      {/* 프로필 카드 */}
      <div className="bg-white border border-gray-200 rounded-xl p-6 mb-4">
        <h2 className="text-sm font-medium text-gray-700 mb-4">계정 정보</h2>

        <div className="flex flex-col gap-4">
          {/* 이메일 (읽기 전용) */}
          <div className="flex flex-col gap-1">
            <label className="text-xs text-gray-500">이메일</label>
            <div className="h-9 px-3 flex items-center bg-gray-50 border border-gray-200 rounded-md text-sm text-gray-500 select-all">
              {profile.email}
            </div>
          </div>

          {/* 닉네임 */}
          <div className="flex flex-col gap-1">
            <label className="text-xs text-gray-500">닉네임</label>
            <input
              value={nickname}
              onChange={(e) => { setNickname(e.target.value); setSaveMsg(null); }}
              placeholder="닉네임 입력"
              maxLength={32}
              className="h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-[#03C75A]/30 focus:border-[#03C75A]"
            />
          </div>

          {/* 플랜 (읽기 전용) */}
          <div className="flex flex-col gap-1">
            <label className="text-xs text-gray-500">플랜</label>
            <div className="flex items-center gap-2">
              <span className={`text-xs font-semibold px-2 py-0.5 rounded ${
                profile.planType === "PRO"
                  ? "bg-violet-100 text-violet-700"
                  : "bg-gray-100 text-gray-600"
              }`}>
                {profile.planType}
              </span>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-3 mt-5">
          <button
            onClick={handleSave}
            disabled={saving}
            className="h-9 px-4 bg-[#03C75A] text-white text-sm font-medium rounded-md disabled:opacity-60"
          >
            {saving ? "저장 중..." : "저장"}
          </button>
          {saveMsg && (
            <span className={`text-xs ${saveMsg.type === "ok" ? "text-[#03C75A]" : "text-red-500"}`}>
              {saveMsg.text}
            </span>
          )}
        </div>
      </div>

      {/* 위험 영역 */}
      <div className="bg-white border border-red-200 rounded-xl p-6">
        <h2 className="text-sm font-medium text-red-700 mb-1">위험 영역</h2>
        <p className="text-xs text-gray-400 mb-4">계정을 삭제하면 모든 데이터가 영구적으로 제거됩니다.</p>

        {!withdrawConfirm ? (
          <button
            onClick={() => setWithdrawConfirm(true)}
            className="h-9 px-4 border border-red-300 text-red-600 text-sm rounded-md hover:bg-red-50"
          >
            회원 탈퇴
          </button>
        ) : (
          <div className="flex items-center gap-3">
            <span className="text-xs text-gray-600">정말 탈퇴하시겠습니까?</span>
            <button
              onClick={handleWithdraw}
              disabled={withdrawing}
              className="h-8 px-3 bg-red-500 text-white text-xs font-medium rounded-md disabled:opacity-60"
            >
              {withdrawing ? "처리 중..." : "네, 탈퇴합니다"}
            </button>
            <button
              onClick={() => setWithdrawConfirm(false)}
              className="h-8 px-3 border border-gray-300 text-gray-600 text-xs rounded-md hover:bg-gray-50"
            >
              취소
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
