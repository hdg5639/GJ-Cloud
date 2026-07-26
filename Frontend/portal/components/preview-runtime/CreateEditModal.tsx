"use client";

import { useState, type FormEvent } from "react";
import { Modal } from "@/components/ui/modal";
import { Field, Input } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { callCapability, isPasswordLikeField, rowId } from "./api";
import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

export function CreateEditModal({
  open,
  onClose,
  capability,
  config,
  initialValues,
  onSuccess,
  onSubmitOverride,
  pathParamId,
}: {
  open: boolean;
  onClose: () => void;
  capability: PreviewCapability;
  config: PreviewRuntimeConfig;
  initialValues?: Record<string, unknown>;
  onSuccess: () => void;
  // 이 페이지의 CREATE 액션에 FlowBlueprint가 배정돼 있으면(RuleBasedFlowGenerator) API 호출 자체를
  // 이 모달이 하지 않고 폼 값만 넘긴다 — 호출 측(PreviewPageRenderer)이 FlowExecutor로 API_CALL부터
  // NAVIGATE까지 전체 flow를 실행한다. 없으면 기존처럼 이 모달이 직접 callCapability를 호출한다.
  onSubmitOverride?: (values: Record<string, string>) => Promise<void>;
  // 중첩 리소스 생성(/parents/{id}/children)처럼 생성 폼 자체에도 부모 경로 파라미터가 필요한 경우.
  // 수정 폼의 row id보다 우선한다.
  pathParamId?: string;
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
      if (onSubmitOverride) {
        await onSubmitOverride(values);
      } else {
        const resolvedPathId = pathParamId ?? (initialValues ? rowId(initialValues) : "");
        const pathParams: Record<string, string> = resolvedPathId ? { id: resolvedPathId } : {};
        await callCapability(config, capability, { body: values, pathParams });
        onSuccess();
      }
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "저장에 실패했습니다");
    } finally {
      setLoading(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose}>
      <div className="mx-auto w-[420px] rounded-panel bg-panel p-6">
        <h2 className="mb-4 text-base font-bold">{capability.type === "CREATE" ? "생성" : "수정"}</h2>
        <form onSubmit={handleSubmit}>
          {fields.length === 0 && (
            <p className="mb-3 text-xs text-muted-soft">이 API의 요청 필드를 확인하지 못했습니다.</p>
          )}
          {fields.map((field) => (
            <Field key={field} label={field} htmlFor={`preview-field-${field}`}>
              <Input
                id={`preview-field-${field}`}
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
    </Modal>
  );
}
