"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { api } from "@/lib/api-client";

export default function VerifyPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const email = searchParams.get("email") ?? "";
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [resent, setResent] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await api.auth.verifyEmail(email, code);
      router.push("/login");
    } catch (err) {
      setError(err instanceof Error ? err.message : "인증에 실패했습니다");
    } finally {
      setLoading(false);
    }
  }

  async function handleResend() {
    setResending(true);
    setResent(false);
    try {
      await api.auth.resendVerification(email);
      setResent(true);
    } catch {
      // 실패해도 UI 유지
    } finally {
      setResending(false);
    }
  }

  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-50">
      <div className="w-[340px] bg-white border border-gray-200 rounded-xl p-8">
        <div className="flex items-center gap-2 mb-6">
          <div className="w-7 h-7 rounded-lg bg-[#03C75A] flex items-center justify-center">
            <span className="text-white text-sm font-medium">G</span>
          </div>
          <span className="font-medium text-base text-gray-900">gamjabox</span>
        </div>

        <div className="flex items-center gap-2 mb-6">
          <div className="flex items-center gap-1.5">
            <span className="w-[22px] h-[22px] rounded-full bg-[#03C75A] text-white text-[11px] font-medium flex items-center justify-center">
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
                <path d="M5 13l4 4L19 7" />
              </svg>
            </span>
            <span className="text-xs font-medium text-gray-900">정보 입력</span>
          </div>
          <span className="flex-1 h-px bg-[#03C75A]" />
          <div className="flex items-center gap-1.5">
            <span className="w-[22px] h-[22px] rounded-full bg-[#03C75A] text-white text-[11px] font-medium flex items-center justify-center">
              2
            </span>
            <span className="text-xs font-medium text-gray-900">이메일 인증</span>
          </div>
        </div>

        <h1 className="text-lg font-medium text-gray-900 mb-1">이메일 인증</h1>
        <p className="text-sm text-gray-500 mb-5">
          <span className="font-medium text-gray-700">{email}</span>로 전송된 6자리 코드를 입력하세요
        </p>

        <form onSubmit={handleSubmit}>
          <div className="flex flex-col gap-1.5 mb-5">
            <label className="text-xs text-gray-500">인증 코드</label>
            <input
              type="text"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="123456"
              maxLength={6}
              required
              className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm tracking-widest text-center focus:outline-none focus:ring-2 focus:ring-[#03C75A]/30 focus:border-[#03C75A]"
            />
          </div>

          {error && <p className="text-xs text-red-600 mb-3">{error}</p>}

          <button
            type="submit"
            disabled={loading}
            className="w-full h-[38px] bg-[#03C75A] text-white rounded-md text-sm font-medium mb-3 disabled:opacity-60"
          >
            {loading ? "확인 중..." : "인증 완료"}
          </button>
        </form>

        <p className="text-center text-sm text-gray-500">
          코드를 못 받으셨나요?{" "}
          <button
            onClick={handleResend}
            disabled={resending}
            className="text-[#03C75A] font-medium disabled:opacity-60"
          >
            {resending ? "발송 중..." : resent ? "발송됨!" : "재발송"}
          </button>
        </p>
      </div>
    </div>
  );
}
