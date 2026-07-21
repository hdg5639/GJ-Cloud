"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api-client";
import { AuthBrand } from "@/components/ui/auth-card";
import { AuthSplitLayout, AuthStepsPanel } from "@/components/ui/auth-split-layout";
import { Field, Input } from "@/components/ui/field";
import { Button } from "@/components/ui/button";

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
    <AuthSplitLayout visual={<AuthStepsPanel currentStep={1} />}>
      <div className="mb-7 lg:hidden">
        <AuthBrand />
      </div>

      <h1 className="mb-4 text-lg font-bold">회원가입</h1>

      <form onSubmit={handleSubmit}>
        <Field label="이메일" htmlFor="register-email">
          <Input
            id="register-email"
            name="register-email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
            required
          />
        </Field>

        <Field label="비밀번호" htmlFor="register-password">
          <Input
            id="register-password"
            name="register-password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="8자 이상"
            minLength={8}
            required
          />
        </Field>

        <Field label="비밀번호 확인" htmlFor="register-password-confirm" className="mb-5">
          <Input
            id="register-password-confirm"
            name="register-password-confirm"
            type="password"
            value={passwordConfirm}
            onChange={(e) => setPasswordConfirm(e.target.value)}
            placeholder="비밀번호 재입력"
            required
          />
        </Field>

        {error && <p className="mb-3 text-xs text-danger">{error}</p>}

        <Button type="submit" variant="primary" disabled={loading} className="mb-4 w-full">
          {loading ? "처리 중..." : "가입하고 인증 메일 받기"}
        </Button>
      </form>

      <p className="text-center text-sm text-muted">
        이미 계정이 있으신가요?{" "}
        <a href="/login" className="font-bold text-brand-strong">
          로그인
        </a>
      </p>
    </AuthSplitLayout>
  );
}
