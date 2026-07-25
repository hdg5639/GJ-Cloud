"use client";

import { useState } from "react";
import { Modal } from "@/components/ui/modal";
import { Button } from "@/components/ui/button";
import { callCapability } from "./api";
import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

export function DeleteConfirmModal({
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
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleConfirm() {
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
      <div className="mx-auto w-[340px] rounded-panel bg-panel p-6">
        <h2 className="mb-2 text-base font-bold">{capability.resourceName} 삭제</h2>
        <p className="mb-5 text-sm text-muted">삭제하면 복구할 수 없습니다. 계속하시겠습니까?</p>
        {error && <p className="mb-3 text-xs text-danger">{error}</p>}
        <div className="flex gap-2">
          <Button onClick={onClose} className="flex-1" disabled={loading}>
            취소
          </Button>
          <Button variant="danger-solid" onClick={handleConfirm} className="flex-1" disabled={loading}>
            {loading ? "삭제 중..." : "삭제"}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
