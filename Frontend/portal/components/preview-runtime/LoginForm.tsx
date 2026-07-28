"use client";

import { useState, type FormEvent } from "react";
import { Field, Input } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { callCapability, extractToken, isPasswordLikeField } from "./api";
import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

export function LoginForm({
  capability,
  config,
}: {
  capability: PreviewCapability;
  config: PreviewRuntimeConfig;
}) {
  // 요청 필드명을 확인 못 했을 때도(문서에 requestBody 스키마가 없는 등) 폼이 비어 보이지 않도록 최소한의
  // 기본값으로 대체 — 실제 필드명은 대부분의 로그인 API에서 이 둘 중 하나와 일치한다.
  const fields = capability.fields.length > 0 ? capability.fields : ["email", "password"];
  const [values, setValues] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const result = await callCapability(config, capability, { body: values });
      const token = extractToken(result, capability.accessTokenPath);
      if (!token) {
        setError("응답에서 토큰을 찾지 못했습니다. 위치를 확인해주세요.");
        return;
      }
      config.onAuthTokenChange(token);
    } catch (err) {
      setError(err instanceof Error ? err.message : "로그인에 실패했습니다");
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="mx-auto flex w-full max-w-sm flex-col">
      {fields.map((field) => (
        <Field key={field} label={field} htmlFor={`preview-login-${field}`}>
          <Input
            id={`preview-login-${field}`}
            type={isPasswordLikeField(field) ? "password" : "text"}
            value={values[field] ?? ""}
            onChange={(e) => setValues((prev) => ({ ...prev, [field]: e.target.value }))}
            required
          />
        </Field>
      ))}
      {error && <p className="mb-3 text-xs text-danger">{error}</p>}
      <Button type="submit" variant="primary" disabled={loading}>
        {loading ? "로그인 중..." : "로그인"}
      </Button>
    </form>
  );
}
