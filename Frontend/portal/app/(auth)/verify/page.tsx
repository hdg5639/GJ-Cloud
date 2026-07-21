"use client";

import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { api } from "@/lib/api-client";
import { AuthBrand } from "@/components/ui/auth-card";
import { AuthSplitLayout, AuthStepsPanel } from "@/components/ui/auth-split-layout";
import { Field, Input } from "@/components/ui/field";
import { Button } from "@/components/ui/button";

function VerifyForm() {
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
    <AuthSplitLayout visual={<AuthStepsPanel currentStep={2} />}>
      <div className="mb-7 lg:hidden">
        <AuthBrand />
      </div>

      <h1 className="mb-1 text-lg font-bold">이메일 인증</h1>
      <p className="mb-5 text-sm text-muted">
        <span className="font-bold text-[#3f4c43]">{email}</span>로 전송된 6자리 코드를 입력하세요
      </p>

      <form onSubmit={handleSubmit}>
        <Field label="인증 코드" htmlFor="verify-code" className="mb-5">
          <Input
            id="verify-code"
            name="verify-code"
            type="text"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="123456"
            maxLength={6}
            required
            className="text-center tracking-widest"
          />
        </Field>

        {error && <p className="mb-3 text-xs text-danger">{error}</p>}

        <Button type="submit" variant="primary" disabled={loading} className="mb-3 w-full">
          {loading ? "확인 중..." : "인증 완료"}
        </Button>
      </form>

      <p className="text-center text-sm text-muted">
        코드를 못 받으셨나요?{" "}
        <button onClick={handleResend} disabled={resending} className="font-bold text-brand-strong disabled:opacity-60">
          {resending ? "발송 중..." : resent ? "발송됨!" : "재발송"}
        </button>
      </p>
    </AuthSplitLayout>
  );
}

export default function VerifyPage() {
  return (
    <Suspense
      fallback={
        <div className="theme-dark flex min-h-screen items-center justify-center bg-background">
          <div className="h-40 w-[360px] rounded-panel border border-line bg-panel" />
        </div>
      }
    >
      <VerifyForm />
    </Suspense>
  );
}
