"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";
import { AuthBrand } from "@/components/ui/auth-card";
import { AuthMarketingPanel, AuthSplitLayout, useBrandIntroPhase } from "@/components/ui/auth-split-layout";
import { Field, Input } from "@/components/ui/field";
import { Button } from "@/components/ui/button";

export default function LoginPage() {
  const router = useRouter();
  const { login } = useAuth();
  const introPhase = useBrandIntroPhase();
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
    <AuthSplitLayout visual={<AuthMarketingPanel introPhase={introPhase} />} introPhase={introPhase}>
      <div className="mb-7 lg:hidden">
        <AuthBrand />
      </div>

      <h1 className="mb-1 text-lg font-bold">로그인</h1>
      <p className="mb-5 text-sm text-muted">계정에 로그인하고 인스턴스를 관리하세요</p>

      <form onSubmit={handleSubmit}>
        <Field label="이메일" htmlFor="login-email">
          <Input
            id="login-email"
            name="login-email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
            required
          />
        </Field>

        <Field label="비밀번호" htmlFor="login-password" className="mb-2">
          <Input
            id="login-password"
            name="login-password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호"
            required
          />
        </Field>

        <div className="mt-3 flex items-center justify-between gap-2">
          <a href="/forgot-password" className="text-xs font-bold text-brand-strong">
            비밀번호를 잊으셨나요?
          </a>
          <div className="flex items-center gap-2">
            <label htmlFor="rememberMe" className="cursor-pointer select-none text-xs text-muted">
              로그인 유지
            </label>
            <input
              id="rememberMe"
              type="checkbox"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
              className="h-3.5 w-3.5 cursor-pointer accent-brand"
            />
          </div>
        </div>

        {error && <p className="mt-2 text-xs text-danger">{error}</p>}

        <Button type="submit" variant="primary" disabled={loading} className="mb-4 mt-4 w-full">
          {loading ? "로그인 중..." : "로그인"}
        </Button>
      </form>

      <p className="text-center text-sm text-muted">
        계정이 없으신가요?{" "}
        <a href="/register" className="font-bold text-brand-strong">
          회원가입
        </a>
      </p>
    </AuthSplitLayout>
  );
}
