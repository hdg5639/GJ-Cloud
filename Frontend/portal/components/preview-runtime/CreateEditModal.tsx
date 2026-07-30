"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { callCapability, rowId } from "./api";
import { BlueprintModalFrame } from "./blueprints/modals";
import { PreviewFormFields, humanizeFieldLabel } from "./formFields";
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
  onSubmitOverride?: (values: Record<string, string>) => Promise<boolean | void>;
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

  const isCreate = capability.type === "CREATE";
  const resource = humanizeFieldLabel(capability.resourceName || "항목");

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      if (onSubmitOverride) {
        const completed = await onSubmitOverride(values);
        if (completed === false) {
          return;
        }
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
    <BlueprintModalFrame
      open={open}
      onClose={onClose}
      eyebrow={resource}
      title={isCreate ? `새 ${resource} 만들기` : `${resource} 수정`}
      description={isCreate ? "필요한 정보를 입력하고 저장하세요." : "값을 수정한 뒤 저장하세요."}
      footer={(
        <>
          <Button type="button" onClick={onClose}>취소</Button>
          <Button type="submit" form="preview-create-edit-form" variant="primary" disabled={loading}>
            {loading ? "저장 중..." : "저장"}
          </Button>
        </>
      )}
    >
      <form id="preview-create-edit-form" onSubmit={handleSubmit}>
        {fields.length === 0 ? (
          <p className="rounded-[12px] border border-line bg-panel p-4 text-sm text-muted-soft">
            이 API의 요청 필드를 확인하지 못했습니다. 추가 입력 없이 저장할 수 있습니다.
          </p>
        ) : (
          <PreviewFormFields
            fields={fields}
            values={values}
            idPrefix="preview-field"
            onChange={(field, value) => setValues((prev) => ({ ...prev, [field]: value }))}
          />
        )}
        {error && (
          <p className="mt-2 rounded-[10px] border border-danger-soft bg-danger/10 px-3 py-2 text-xs font-semibold text-danger">
            {error}
          </p>
        )}
      </form>
    </BlueprintModalFrame>
  );
}
