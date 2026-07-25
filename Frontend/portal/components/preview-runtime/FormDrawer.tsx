"use client";

import { useState, type FormEvent } from "react";
import { Field, Input } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { callCapability, isPasswordLikeField, rowId } from "./api";
import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

// Direction Recovery Change Request §9.3 "form-drawer" — CreateEditModal(simple-form-modal)과
// 데이터 동작·props 시그니처는 완전히 동일하고, 화면 오른쪽에서 열리는 패널로 보여준다. PRODUCT_LIKE
// 목적일 때 고른다(§3 "Cards, detail pages, drawers, and guided creation flows"). Modal(중앙 정렬 +
// 고정된 transform 애니메이션)은 재사용하지 않고 이 컴포넌트 전용의 가벼운 오른쪽 슬라이드 배경을 쓴다
// — 이번 첫 버전은 진입 애니메이션 없이 열림/닫힘만 즉시 토글한다(알려진 단순화).
export function FormDrawer({
  open,
  onClose,
  capability,
  config,
  initialValues,
  onSuccess,
}: {
  open: boolean;
  onClose: () => void;
  capability: PreviewCapability;
  config: PreviewRuntimeConfig;
  initialValues?: Record<string, unknown>;
  onSuccess: () => void;
}) {
  const fields = capability.fields;
  const [values, setValues] = useState<Record<string, string>>(() =>
    Object.fromEntries(fields.map((field) => [field, initialValues?.[field] != null ? String(initialValues[field]) : ""]))
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const pathParams: Record<string, string> = initialValues ? { id: rowId(initialValues) } : {};
      await callCapability(config, capability, { body: values, pathParams });
      onSuccess();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "저장에 실패했습니다");
    } finally {
      setLoading(false);
    }
  }

  if (!open) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-[100] flex justify-end bg-black/60" onClick={onClose} role="presentation">
      <div
        onClick={(e) => e.stopPropagation()}
        className="h-full w-full max-w-md overflow-y-auto bg-panel p-6 shadow-2xl"
        role="dialog"
        aria-modal="true"
      >
        <h2 className="mb-4 text-base font-bold">{capability.type === "CREATE" ? "생성" : "수정"}</h2>
        <form onSubmit={handleSubmit}>
          {fields.length === 0 && (
            <p className="mb-3 text-xs text-muted-soft">이 API의 요청 필드를 확인하지 못했습니다.</p>
          )}
          {fields.map((field) => (
            <Field key={field} label={field} htmlFor={`preview-drawer-field-${field}`}>
              <Input
                id={`preview-drawer-field-${field}`}
                type={isPasswordLikeField(field) ? "password" : "text"}
                value={values[field] ?? ""}
                onChange={(e) => setValues((prev) => ({ ...prev, [field]: e.target.value }))}
              />
            </Field>
          ))}
          {error && <p className="mb-3 text-xs text-danger">{error}</p>}
          <div className="mt-1 flex gap-2">
            <Button type="button" onClick={onClose} className="flex-1">
              취소
            </Button>
            <Button type="submit" variant="primary" disabled={loading} className="flex-1">
              {loading ? "저장 중..." : "저장"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
