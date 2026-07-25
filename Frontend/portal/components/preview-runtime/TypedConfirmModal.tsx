"use client";

import { useState } from "react";
import { Modal } from "@/components/ui/modal";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { callCapability } from "./api";
import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

// Direction Recovery Change Request §9.4 "typed-confirm-modal" — DeleteConfirmModal
// (simple-confirm-modal)과 데이터 동작·props 시그니처는 완전히 동일하고, 삭제 버튼을 누르기 전에
// 리소스명을 정확히 입력해야만 활성화된다. ADMIN 목적일 때 고른다(§3 "Destructive-operation safeguards").
export function TypedConfirmModal({
  open,
  onClose,
  capability,
  config,
  targetId,
  onSuccess,
}: {
  open: boolean;
  onClose: () => void;
  capability: PreviewCapability;
  config: PreviewRuntimeConfig;
  targetId: string;
  onSuccess: () => void;
}) {
  const [confirmText, setConfirmText] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const matches = confirmText.trim() === capability.resourceName;

  async function handleConfirm() {
    if (!matches) {
      return;
    }
    setError(null);
    setLoading(true);
    try {
      await callCapability(config, capability, { pathParams: { id: targetId } });
      onSuccess();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "삭제에 실패했습니다");
    } finally {
      setLoading(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose}>
      <div className="mx-auto w-[360px] rounded-panel bg-panel p-6">
        <h2 className="mb-2 text-base font-bold">{capability.resourceName} 삭제</h2>
        <p className="mb-3 text-sm text-muted">
          삭제하면 복구할 수 없습니다. 계속하려면 <strong className="text-foreground">{capability.resourceName}</strong>
          을(를) 입력하세요.
        </p>
        <Input
          value={confirmText}
          onChange={(e) => setConfirmText(e.target.value)}
          placeholder={capability.resourceName}
          className="mb-3"
        />
        {error && <p className="mb-3 text-xs text-danger">{error}</p>}
        <div className="flex gap-2">
          <Button onClick={onClose} className="flex-1" disabled={loading}>
            취소
          </Button>
          <Button variant="danger-solid" onClick={handleConfirm} className="flex-1" disabled={loading || !matches}>
            {loading ? "삭제 중..." : "삭제"}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
