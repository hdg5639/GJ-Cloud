"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { ProfileResponse, UpgradeRequestResponse } from "@/lib/types";

type Tab = "profile" | "upgrade-requests";

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
  const [withdrawing, setWithdrawing] = useState(false);
  const [loadingRequests, setLoadingRequests] = useState(false);

  useEffect(() => {
    if (!accessToken) return;
    api.user.profile(accessToken).then((p) => {
      setProfile(p);
      setNickname(p.nickname ?? "");
    }).catch(() => {});
  }, [accessToken]);

  useEffect(() => {
    if (!accessToken || !profile || tab !== "upgrade-requests") return;
    setLoadingRequests(true);
    api.user.getUpgradeRequests(accessToken, profile.userId)
      .then((data) => setRequests(data))
      .catch(console.error)
      .finally(() => setLoadingRequests(false));
  }, [accessToken, profile, tab]);

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
      <div className="flex gap-4 mb-6 border-b border-gray-200">
        <button
          onClick={() => setTab("profile")}
          className={`pb-3 text-sm font-medium transition-colors ${
            tab === "profile"
              ? "text-gray-900 border-b-2 border-[#03C75A]"
              : "text-gray-500 hover:text-gray-700"
          }`}
        >
          프로필 설정
        </button>
        <button
          onClick={() => setTab("upgrade-requests")}
          className={`pb-3 text-sm font-medium transition-colors ${
            tab === "upgrade-requests"
              ? "text-gray-900 border-b-2 border-[#03C75A]"
              : "text-gray-500 hover:text-gray-700"
          }`}
        >
          플랜 변경 요청
        </button>
      </div>

      {tab === "profile" && (
      <>
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
      </>
      )}

      {tab === "upgrade-requests" && (
      <div>
        <h2 className="text-sm font-medium text-gray-900 mb-4">플랜 변경 요청 현황</h2>

        {loadingRequests ? (
          <p className="text-sm text-gray-500">불러오는 중...</p>
        ) : requests.length === 0 ? (
          <div className="bg-white border border-gray-200 rounded-xl p-6 text-center">
            <p className="text-sm text-gray-500">요청이 없습니다</p>
          </div>
        ) : (
          <div className="space-y-3">
            {requests.map((req) => (
              <div key={req.id} className="bg-white border border-gray-200 rounded-xl p-4">
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-sm font-medium text-gray-900">
                      {req.type === "UPGRADE" ? "업그레이드" : "다운그레이드"} 요청
                    </p>
                    <p className="text-xs text-gray-500 mt-1">
                      대상: <span className="font-medium">{req.targetPlanType}</span>
                    </p>
                    <p className="text-xs text-gray-400 mt-1">
                      요청일: {new Date(req.createdAt).toLocaleString("ko-KR")}
                    </p>
                  </div>
                  <span className={`text-xs font-medium px-2.5 py-1 rounded whitespace-nowrap ${
                    req.status === "PENDING" ? "bg-amber-100 text-amber-700"
                    : req.status === "APPROVED" ? "bg-green-100 text-green-700"
                    : "bg-red-100 text-red-700"
                  }`}>
                    {req.status === "PENDING" ? "대기 중" : req.status === "APPROVED" ? "승인됨" : "거절됨"}
                  </span>
                </div>
                {req.status === "REJECTED" && req.reason && (
                  <p className="text-xs text-red-600 mt-3 p-2 bg-red-50 rounded">거절 사유: {req.reason}</p>
                )}
                {req.status === "APPROVED" && req.reviewedAt && (
                  <p className="text-xs text-gray-500 mt-2">승인일: {new Date(req.reviewedAt).toLocaleString("ko-KR")}</p>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
      )}
    </div>
  );
}
