"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api-client";
import { AuthBrand } from "@/components/ui/auth-card";
import { AuthMarketingPanel, AuthSplitLayout } from "@/components/ui/auth-split-layout";
import { Field, Input } from "@/components/ui/field";
import { Button } from "@/components/ui/button";

type Step = "email" | "code" | "reset" | "done";

export default function ForgotPasswordPage() {
  const router = useRouter();
  const [step, setStep] = useState<Step>("email");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [resetToken, setResetToken] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [resent, setResent] = useState(false);

  async function handleSendCode(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await api.auth.sendPasswordResetCode(email);
      setStep("code");
    } catch (err) {
      setError(err instanceof Error ? err.message : "코드 발송에 실패했습니다");
    } finally {
      setLoading(false);
    }
  }

  async function handleConfirmCode(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const { resetToken } = await api.auth.confirmPasswordResetCode(email, code);
      setResetToken(resetToken);
      setStep("reset");
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
      await api.auth.sendPasswordResetCode(email);
      setResent(true);
    } catch {
      // 실패해도 UI 유지
    } finally {
      setResending(false);
    }
  }

  async function handleResetPassword(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (newPassword !== confirmPassword) {
      setError("새 비밀번호가 일치하지 않습니다");
      return;
    }
    setLoading(true);
    try {
      await api.auth.resetPassword(resetToken, newPassword);
      setStep("done");
    } catch (err) {
      setError(err instanceof Error ? err.message : "비밀번호 재설정에 실패했습니다");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthSplitLayout visual={<AuthMarketingPanel />}>
      <div className="mb-7 lg:hidden">
        <AuthBrand />
      </div>

      {step === "email" && (
        <>
          <h1 className="mb-1 text-lg font-bold">비밀번호 찾기</h1>
          <p className="mb-5 text-sm text-muted">가입한 이메일로 재설정 코드를 보내드립니다</p>

          <form onSubmit={handleSendCode}>
            <Field label="이메일" htmlFor="forgot-email" className="mb-2">
              <Input
                id="forgot-email"
                name="forgot-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                required
              />
            </Field>

            {error && <p className="mt-2 text-xs text-danger">{error}</p>}

            <Button type="submit" variant="primary" disabled={loading} className="mb-4 mt-4 w-full">
              {loading ? "발송 중..." : "재설정 코드 받기"}
            </Button>
          </form>
        </>
      )}

      {step === "code" && (
        <>
          <h1 className="mb-1 text-lg font-bold">이메일 인증</h1>
          <p className="mb-5 text-sm text-muted">
            <span className="font-bold text-[#3f4c43]">{email}</span>로 전송된 6자리 코드를 입력하세요
          </p>

          <form onSubmit={handleConfirmCode}>
            <Field label="인증 코드" htmlFor="forgot-code" className="mb-5">
              <Input
                id="forgot-code"
                name="forgot-code"
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
              {loading ? "확인 중..." : "다음"}
            </Button>
          </form>

          <p className="text-center text-sm text-muted">
            코드를 못 받으셨나요?{" "}
            <button onClick={handleResend} disabled={resending} className="font-bold text-brand-strong disabled:opacity-60">
              {resending ? "발송 중..." : resent ? "발송됨!" : "재발송"}
            </button>
          </p>
        </>
      )}

      {step === "reset" && (
        <>
          <h1 className="mb-1 text-lg font-bold">새 비밀번호 설정</h1>
          <p className="mb-5 text-sm text-muted">새로 사용할 비밀번호를 입력하세요</p>

          <form onSubmit={handleResetPassword}>
            <Field label="새 비밀번호" htmlFor="forgot-new-password" className="mb-3">
              <Input
                id="forgot-new-password"
                name="forgot-new-password"
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="8자 이상"
                minLength={8}
                required
              />
            </Field>

            <Field label="새 비밀번호 확인" htmlFor="forgot-confirm-password" className="mb-2">
              <Input
                id="forgot-confirm-password"
                name="forgot-confirm-password"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="비밀번호 다시 입력"
                minLength={8}
                required
              />
            </Field>

            {error && <p className="mt-2 text-xs text-danger">{error}</p>}

            <Button type="submit" variant="primary" disabled={loading} className="mb-4 mt-4 w-full">
              {loading ? "변경 중..." : "비밀번호 변경"}
            </Button>
          </form>
        </>
      )}

      {step === "done" && (
        <>
          <h1 className="mb-1 text-lg font-bold">비밀번호가 변경되었습니다</h1>
          <p className="mb-5 text-sm text-muted">새 비밀번호로 다시 로그인해주세요</p>

          <Button type="button" variant="primary" className="mb-4 w-full" onClick={() => router.push("/login")}>
            로그인하러 가기
          </Button>
        </>
      )}

      {step !== "done" && (
        <p className="text-center text-sm text-muted">
          <a href="/login" className="font-bold text-brand-strong">
            로그인으로 돌아가기
          </a>
        </p>
      )}
    </AuthSplitLayout>
  );
}
