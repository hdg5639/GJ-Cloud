"use client";

import { useState, useEffect } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { VmResponse, ProfileResponse } from "@/lib/types";
import { Modal } from "@/components/ui/modal";

interface Props {
  open?: boolean;
  vm: VmResponse;
  onClose: () => void;
  onSuccess: () => void;
}

export default function UpgradeRequestModal({ open = true, vm, onClose, onSuccess }: Props) {
  const { accessToken } = useAuth();
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedPlan, setSelectedPlan] = useState<"FREE" | "PRO" | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    api.user.profile(accessToken).then(setProfile).catch(console.error);
  }, [accessToken]);

  if (!accessToken || !profile) return null;

  const currentPlan = vm.planType;
  const availablePlans = currentPlan === "FREE" ? ["PRO"] : ["FREE"];

  const handleSubmit = async () => {
    if (!selectedPlan) {
      setError("변경할 플랜을 선택해주세요");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await api.user.createUpgradeRequest(accessToken, profile.userId, selectedPlan);
      onSuccess();
    } catch (err) {
      const message = err instanceof Error ? err.message : "요청 생성에 실패했습니다";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose}>
      <div className="mx-auto w-full max-w-[420px] rounded-xl border border-line bg-panel p-6 shadow-2xl shadow-black/30">
        <h2 className="text-base font-medium text-foreground mb-4">플랜 변경</h2>

        <div className="space-y-3 mb-6">
          <div>
            <p className="text-xs text-muted mb-2">현재 플랜</p>
            <div className="px-4 py-3 bg-white/[0.03] border border-line rounded-lg">
              <p className="text-sm font-medium text-foreground">{currentPlan}</p>
            </div>
          </div>

          <div>
            <p className="text-xs text-muted mb-2">변경할 플랜</p>
            <div className="space-y-2">
              {availablePlans.map((plan) => (
                <label key={plan} htmlFor={`modal-plan-${plan}`} className="flex items-center p-3 border border-line-strong rounded-lg cursor-pointer hover:bg-white/[0.04]">
                  <input
                    id={`modal-plan-${plan}`}
                    type="radio"
                    name="plan"
                    value={plan}
                    checked={selectedPlan === plan}
                    onChange={() => setSelectedPlan(plan as "FREE" | "PRO")}
                    className="w-4 h-4 accent-brand"
                  />
                  <span className="ml-3 text-sm font-medium text-foreground">{plan}</span>
                </label>
              ))}
            </div>
          </div>
        </div>

        {error && <div className="bg-danger/10 border border-danger-soft text-danger text-sm px-3 py-2 rounded-lg mb-4">{error}</div>}

        <div className="flex gap-2">
          <button
            onClick={onClose}
            disabled={loading}
            className="flex-1 h-9 border border-line-strong rounded-md text-sm text-muted disabled:opacity-60"
          >
            취소
          </button>
          <button
            onClick={handleSubmit}
            disabled={loading || !selectedPlan}
            className="flex-1 h-9 bg-brand text-[#0a0c08] font-bold rounded-md text-sm disabled:opacity-60"
          >
            {loading ? "요청 중..." : "변경 요청"}
          </button>
        </div>
      </div>
    </Modal>
  );
}
