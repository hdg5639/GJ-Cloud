"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api-client";

export default function RegisterPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (password !== passwordConfirm) {
      setError("비밀번호가 일치하지 않습니다");
      return;
    }
    setLoading(true);
    try {
      await api.auth.register(email, password);
      router.push(`/verify?email=${encodeURIComponent(email)}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "회원가입에 실패했습니다");
    } finally {
      setLoading(false);
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
              1
            </span>
            <span className="text-xs font-medium text-gray-900">정보 입력</span>
          </div>
          <span className="flex-1 h-px bg-gray-200" />
          <div className="flex items-center gap-1.5">
            <span className="w-[22px] h-[22px] rounded-full bg-gray-100 border border-gray-300 text-gray-400 text-[11px] font-medium flex items-center justify-center">
              2
            </span>
            <span className="text-xs text-gray-400">이메일 인증</span>
          </div>
        </div>

        <h1 className="text-lg font-medium text-gray-900 mb-4">회원가입</h1>

        <form onSubmit={handleSubmit}>
          <div className="flex flex-col gap-1.5 mb-3.5">
            <label htmlFor="register-email" className="text-xs text-gray-500">이메일</label>
            <input
              id="register-email"
              name="register-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
              className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-[#03C75A]/30 focus:border-[#03C75A]"
            />
          </div>

          <div className="flex flex-col gap-1.5 mb-3.5">
            <label htmlFor="register-password" className="text-xs text-gray-500">비밀번호</label>
            <input
              id="register-password"
              name="register-password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="8자 이상"
              minLength={8}
              required
              className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-[#03C75A]/30 focus:border-[#03C75A]"
            />
          </div>

          <div className="flex flex-col gap-1.5 mb-5">
            <label htmlFor="register-password-confirm" className="text-xs text-gray-500">비밀번호 확인</label>
            <input
              id="register-password-confirm"
              name="register-password-confirm"
              type="password"
              value={passwordConfirm}
              onChange={(e) => setPasswordConfirm(e.target.value)}
              placeholder="비밀번호 재입력"
              required
              className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-[#03C75A]/30 focus:border-[#03C75A]"
            />
          </div>

          {error && <p className="text-xs text-red-600 mb-3">{error}</p>}

          <button
            type="submit"
            disabled={loading}
            className="w-full h-[38px] bg-[#03C75A] text-white rounded-md text-sm font-medium mb-4 disabled:opacity-60"
          >
            {loading ? "처리 중..." : "가입하고 인증 메일 받기"}
          </button>
        </form>

        <p className="text-center text-sm text-gray-500">
          이미 계정이 있으신가요?{" "}
          <a href="/login" className="text-[#03C75A] font-medium">
            로그인
          </a>
        </p>
      </div>
    </div>
  );
}
