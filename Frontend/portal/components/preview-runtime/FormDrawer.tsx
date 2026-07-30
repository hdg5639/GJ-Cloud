"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { callCapability, rowId } from "./api";
import { PreviewFormFields, humanizeFieldLabel } from "./formFields";
import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

// Direction Recovery Change Request §9.3 "form-drawer" — CreateEditModal(simple-form-modal)과
// 데이터 동작·props 시그니처는 완전히 동일하고, 화면 오른쪽에서 열리는 패널로 보여준다. PRODUCT_LIKE
// 목적일 때 고른다(§3 "Cards, detail pages, drawers, and guided creation flows").
export function FormDrawer({
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
  // CreateEditModal과 동일 — FlowBlueprint가 배정된 CREATE 액션이면 이 드로어는 폼 값만 넘기고
  // 실제 API 호출/후속 단계는 호출 측이 FlowExecutor로 실행한다.
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

  if (!open) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-[120] flex justify-end bg-black/60 backdrop-blur-[2px]" onClick={onClose} role="presentation">
      <div
        onClick={(e) => e.stopPropagation()}
        className="flex h-full w-full max-w-lg flex-col border-l border-line bg-background shadow-2xl"
        role="dialog"
        aria-modal="true"
      >
        <header className="flex items-start justify-between gap-4 border-b border-line bg-panel px-5 py-4">
          <div>
            <p className="text-[10px] font-extrabold uppercase tracking-[0.18em] text-brand-strong">{resource}</p>
            <h2 className="mt-1 text-lg font-extrabold">{isCreate ? `새 ${resource} 만들기` : `${resource} 수정`}</h2>
            <p className="mt-1 text-xs leading-5 text-muted-soft">
              {isCreate ? "필요한 정보를 입력하고 저장하세요." : "값을 수정한 뒤 저장하세요."}
            </p>
          </div>
          <button type="button" onClick={onClose} className="grid h-8 w-8 shrink-0 place-items-center rounded-[9px] border border-line bg-white/[0.02] text-lg text-muted hover:text-foreground" aria-label="Close">×</button>
        </header>

        <form id="preview-drawer-form" onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-5">
          {fields.length === 0 ? (
            <p className="rounded-[12px] border border-line bg-panel p-4 text-sm text-muted-soft">
              이 API의 요청 필드를 확인하지 못했습니다. 추가 입력 없이 저장할 수 있습니다.
            </p>
          ) : (
            <PreviewFormFields
              fields={fields}
              values={values}
              idPrefix="preview-drawer-field"
              onChange={(field, value) => setValues((prev) => ({ ...prev, [field]: value }))}
            />
          )}
          {error && (
            <p className="mt-2 rounded-[10px] border border-danger-soft bg-danger/10 px-3 py-2 text-xs font-semibold text-danger">
              {error}
            </p>
          )}
        </form>

        <footer className="flex items-center justify-end gap-2 border-t border-line bg-panel px-5 py-4">
          <Button type="button" onClick={onClose}>취소</Button>
          <Button type="submit" form="preview-drawer-form" variant="primary" disabled={loading}>
            {loading ? "저장 중..." : "저장"}
          </Button>
        </footer>
      </div>
    </div>
  );
}
