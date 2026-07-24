"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";
import { AuthBrand, AuthCard } from "@/components/ui/auth-card";
import { Field, Input } from "@/components/ui/field";
import { Button } from "@/components/ui/button";

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
    <AuthCard>
      <AuthBrand admin />

      <h1 className="mb-1 text-base font-bold">관리자 로그인</h1>
      <p className="mb-5 text-sm text-muted">관리자 계정으로 로그인하세요</p>

      <form onSubmit={handleSubmit}>
        <Field label="이메일" htmlFor="admin-login-email">
          <Input
            id="admin-login-email"
            name="admin-login-email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="admin@example.com"
            required
          />
        </Field>

        <Field label="비밀번호" htmlFor="admin-login-password" className="mb-2">
          <Input
            id="admin-login-password"
            name="admin-login-password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호"
            required
          />
        </Field>

        {error && <p className="mt-2 text-xs text-danger">{error}</p>}

        <Button type="submit" variant="danger-solid" disabled={loading} className="mt-4 w-full">
          {loading ? "로그인 중..." : "로그인"}
        </Button>
      </form>
    </AuthCard>
  );
}
