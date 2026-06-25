"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";

export default function AdminLoginPage() {
  const router = useRouter();
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const result = await api.auth.login(email, password);

      const payload = JSON.parse(atob(result.accessToken.split(".")[1]));
      if (payload.role !== "ADMIN") {
        setError("관리자 권한이 없는 계정입니다");
        return;
      }

      login(result.accessToken, { email });
      router.push("/");
    } catch (err) {
      const e = err as Error & { errorCode?: string };
      setError(e.message ?? "로그인에 실패했습니다");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-950">
      <div className="w-[340px] bg-gray-900 border border-gray-800 rounded-xl p-8">
        <div className="flex items-center gap-2 mb-7">
          <div className="w-7 h-7 rounded-lg bg-red-500 flex items-center justify-center">
            <span className="text-white text-sm font-bold">A</span>
          </div>
          <div>
            <span className="font-medium text-base text-white">gamjabox</span>
            <span className="block text-[10px] text-red-400 font-medium tracking-widest uppercase leading-none">admin</span>
          </div>
        </div>

        <h1 className="text-base font-medium text-white mb-1">관리자 로그인</h1>
        <p className="text-sm text-gray-500 mb-5">관리자 계정으로 로그인하세요</p>

        <form onSubmit={handleSubmit}>
          <div className="flex flex-col gap-1.5 mb-3.5">
            <label htmlFor="admin-login-email" className="text-xs text-gray-500">이메일</label>
            <input
              id="admin-login-email"
              name="admin-login-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="admin@example.com"
              required
              className="w-full h-9 px-3 bg-gray-800 border border-gray-700 rounded-md text-sm text-white placeholder-gray-600 focus:outline-none focus:ring-2 focus:ring-red-500/30 focus:border-red-500"
            />
          </div>

          <div className="flex flex-col gap-1.5 mb-2">
            <label htmlFor="admin-login-password" className="text-xs text-gray-500">비밀번호</label>
            <input
              id="admin-login-password"
              name="admin-login-password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="비밀번호"
              required
              className="w-full h-9 px-3 bg-gray-800 border border-gray-700 rounded-md text-sm text-white placeholder-gray-600 focus:outline-none focus:ring-2 focus:ring-red-500/30 focus:border-red-500"
            />
          </div>

          {error && <p className="text-xs text-red-400 mt-2">{error}</p>}

          <button
            type="submit"
            disabled={loading}
            className="w-full h-[38px] bg-red-600 text-white rounded-md text-sm font-medium mt-4 hover:bg-red-700 disabled:opacity-60 transition-colors"
          >
            {loading ? "로그인 중..." : "로그인"}
          </button>
        </form>
      </div>
    </div>
  );
}
