"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { callCapability } from "./api";
import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

// Direction Recovery Change Request §9.6 "quick-action-button-group" — command 계열(vm.start 등)의
// 첫 Variant. AutomationPolicy가 항상 USER_INITIATED로 고정 배정되므로(CapabilityExtractor)
// DeleteConfirmModal과 달리 확인 모달 없이 클릭하면 바로 실행한다.
export function QuickActionButtonGroup({
  capabilities,
  config,
  targetId,
  onSuccess,
}: {
  capabilities: PreviewCapability[];
  config: PreviewRuntimeConfig;
  targetId: string;
  onSuccess?: () => void;
}) {
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});

  async function handleClick(capability: PreviewCapability) {
    setErrors((prev) => ({ ...prev, [capability.id]: "" }));
    setPendingId(capability.id);
    try {
      await callCapability(config, capability, { pathParams: { id: targetId } });
      onSuccess?.();
    } catch (err) {
      setErrors((prev) => ({
        ...prev,
        [capability.id]: err instanceof Error ? err.message : "요청에 실패했습니다",
      }));
    } finally {
      setPendingId(null);
    }
  }

  if (capabilities.length === 0) {
    return null;
  }

  return (
    <div className="flex flex-wrap gap-2">
      {capabilities.map((capability) => (
        <div key={capability.id} className="flex flex-col gap-1">
          <Button
            variant="secondary"
            size="small"
            disabled={pendingId !== null}
            onClick={() => handleClick(capability)}
          >
            {pendingId === capability.id ? "처리 중..." : (capability.action ?? capability.id)}
          </Button>
          {errors[capability.id] && <p className="text-[11px] text-danger">{errors[capability.id]}</p>}
        </div>
      ))}
    </div>
  );
}
