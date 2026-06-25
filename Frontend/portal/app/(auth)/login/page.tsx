"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";

export default function LoginPage() {
  const router = useRouter();
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const result = await api.auth.login(email, password, rememberMe);
      login(result.accessToken, { email });
      router.push("/instances");
    } catch (err) {
      const e = err as Error & { errorCode?: string };
      if (e.errorCode === "EMAIL_NOT_VERIFIED") {
        router.push(`/verify?email=${encodeURIComponent(email)}`);
        return;
      }
      setError(e.message ?? "로그인에 실패했습니다");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-50">
      <div className="w-[340px] bg-white border border-gray-200 rounded-xl p-8">
        <div className="flex items-center gap-2 mb-7">
          <div className="w-7 h-7 rounded-lg bg-[#03C75A] flex items-center justify-center">
            <span className="text-white text-sm font-medium">G</span>
          </div>
          <span className="font-medium text-base text-gray-900">gamjabox</span>
        </div>

        <h1 className="text-lg font-medium text-gray-900 mb-1">로그인</h1>
        <p className="text-sm text-gray-500 mb-5">계정에 로그인하고 인스턴스를 관리하세요</p>

        <form onSubmit={handleSubmit}>
          <div className="flex flex-col gap-1.5 mb-3.5">
            <label className="text-xs text-gray-500">이메일</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
              className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-[#03C75A]/30 focus:border-[#03C75A]"
            />
          </div>

          <div className="flex flex-col gap-1.5 mb-2">
            <label className="text-xs text-gray-500">비밀번호</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="비밀번호"
              required
              className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-[#03C75A]/30 focus:border-[#03C75A]"
            />
          </div>

          <div className="flex items-center justify-end gap-2 mt-3">
            <label htmlFor="rememberMe" className="text-xs text-gray-500 cursor-pointer select-none">
              로그인 유지
            </label>
            <input
              id="rememberMe"
              type="checkbox"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
              className="w-3.5 h-3.5 accent-[#03C75A] cursor-pointer"
            />
          </div>

          {error && <p className="text-xs text-red-600 mt-2">{error}</p>}

          <button
            type="submit"
            disabled={loading}
            className="w-full h-[38px] bg-[#03C75A] text-white rounded-md text-sm font-medium mt-4 mb-4 disabled:opacity-60"
          >
            {loading ? "로그인 중..." : "로그인"}
          </button>
        </form>

        <p className="text-center text-sm text-gray-500">
          계정이 없으신가요?{" "}
          <a href="/register" className="text-[#03C75A] font-medium">
            회원가입
          </a>
        </p>
      </div>
    </div>
  );
}
