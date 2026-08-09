"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { VmResponse } from "@/lib/types";
import { VM_PLAN_SPECS } from "@/lib/vm-plans";
import { Modal } from "@/components/ui/modal";

interface Props {
  open?: boolean;
  vm: VmResponse;
  onClose: () => void;
  onSuccess: (updated: VmResponse) => void;
}

export default function VmSpecModal({ open = true, vm, onClose, onSuccess }: Props) {
  const { accessToken } = useAuth();
  const [selectedPlan, setSelectedPlan] = useState<"FREE" | "PRO">(vm.planType);
  const [diskSizeGb, setDiskSizeGb] = useState(vm.diskSizeGb);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    // 모달을 다시 열 때 서버의 최신 VM 값으로 폼 초깃값을 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setSelectedPlan(vm.planType);
    setDiskSizeGb(vm.diskSizeGb);
    setError(null);
  }, [open, vm.diskSizeGb, vm.planType]);

  const planChanged = selectedPlan !== vm.planType;

  // PRO→FREE 다운그레이드는 현재 디스크가 FREE 최대치 초과하면 불가
  const canDowngrade = vm.diskSizeGb <= VM_PLAN_SPECS.FREE.diskMax;

  const diskMin = Math.max(vm.diskSizeGb, VM_PLAN_SPECS[selectedPlan].diskMin);
  const diskMax = VM_PLAN_SPECS[selectedPlan].diskMax;
  const diskStep = VM_PLAN_SPECS[selectedPlan].diskStep;

  function handlePlanSelect(plan: "FREE" | "PRO") {
    if (plan === "FREE" && !canDowngrade) return;
    setSelectedPlan(plan);
    // 새 플랜 범위 밖이면 최솟값으로 조정
    const newMin = Math.max(vm.diskSizeGb, VM_PLAN_SPECS[plan].diskMin);
    const newMax = VM_PLAN_SPECS[plan].diskMax;
    setDiskSizeGb((prev) => Math.min(Math.max(prev, newMin), newMax));
    setError(null);
  }

  async function handleSubmit() {
    if (!accessToken) return;
    if (selectedPlan === vm.planType && diskSizeGb === vm.diskSizeGb) {
      setError("변경된 항목이 없습니다");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const updated = await api.vm.updatePlan(accessToken, vm.id, {
        planType: selectedPlan,
        diskSizeGb,
      });
      onSuccess(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "스펙 변경에 실패했습니다");
    } finally {
      setLoading(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose}>
      <div className="mx-auto w-full max-w-[440px] rounded-xl border border-line bg-panel p-6 shadow-2xl shadow-black/30">
        <h2 className="text-base font-medium text-foreground mb-5">스펙 변경</h2>

        {/* 플랜 선택 */}
        <div className="mb-5">
          <p className="text-xs text-muted mb-2">플랜</p>
          <div className="grid grid-cols-2 gap-2">
            {(["FREE", "PRO"] as const).map((plan) => {
              const disabled = plan === "FREE" && vm.planType === "PRO" && !canDowngrade;
              const selected = selectedPlan === plan;
              return (
                <button
                  key={plan}
                  onClick={() => handlePlanSelect(plan)}
                  disabled={disabled}
                  className={`relative p-3 border rounded-lg text-left transition-colors disabled:opacity-40 disabled:cursor-not-allowed ${
                    selected
                      ? "border-brand bg-soft"
                      : "border-line-strong hover:bg-white/[0.04]"
                  }`}
                >
                  <p className="text-sm font-medium text-foreground">{plan}</p>
                  <p className="text-[11px] text-muted mt-0.5">
                    {VM_PLAN_SPECS[plan].cores} vCPU · {VM_PLAN_SPECS[plan].memory} RAM
                  </p>
                  {disabled && (
                    <p className="text-[10px] text-danger mt-1">
                      현재 디스크 {vm.diskSizeGb}GB가 FREE 최대 {VM_PLAN_SPECS.FREE.diskMax}GB 초과
                    </p>
                  )}
                </button>
              );
            })}
          </div>
        </div>

        {/* 디스크 슬라이더 */}
        <div className="mb-5">
          <div className="flex items-center justify-between mb-2">
            <label htmlFor="vm-spec-disk" className="text-xs text-muted">디스크</label>
            <p className="text-sm font-medium text-foreground">{diskSizeGb} GB</p>
          </div>
          <input
            id="vm-spec-disk"
            name="vm-spec-disk"
            type="range"
            min={diskMin}
            max={diskMax}
            step={diskStep}
            value={diskSizeGb}
            onChange={(e) => setDiskSizeGb(Number(e.target.value))}
            className="w-full accent-brand"
          />
          <div className="flex justify-between mt-1">
            <span className="text-[11px] text-muted-soft">{diskMin} GB</span>
            <span className="text-[11px] text-muted-soft">{diskMax} GB</span>
          </div>
          {diskSizeGb === vm.diskSizeGb && (
            <p className="text-[11px] text-muted-soft mt-1">현재 {vm.diskSizeGb}GB · 디스크는 축소 불가</p>
          )}
        </div>

        {/* 재부팅 필요 안내 */}
        {planChanged && (
          <div className="flex items-start gap-2 px-3 py-2.5 bg-[#d69e2e]/10 border border-[#d69e2e]/30 rounded-lg mb-4">
            <span className="text-[#fbbf24] text-sm mt-0.5">⚠</span>
            <p className="text-[11px] text-[#fbbf24]">
              플랜 변경(CPU/RAM)은 <strong>재부팅 후</strong> 적용됩니다.
            </p>
          </div>
        )}

        {error && (
          <div className="bg-danger/10 border border-danger-soft text-danger text-sm px-3 py-2 rounded-lg mb-4">
            {error}
          </div>
        )}

        <div className="flex gap-2">
          <button
            onClick={onClose}
            disabled={loading}
            className="flex-1 h-9 border border-line-strong rounded-md text-sm text-muted hover:bg-white/[0.04] disabled:opacity-60"
          >
            취소
          </button>
          <button
            onClick={handleSubmit}
            disabled={loading}
            className="flex-1 h-9 bg-brand text-[#0a0c08] font-bold rounded-md text-sm hover:bg-brand-strong disabled:opacity-60"
          >
            {loading ? "변경 중..." : "변경"}
          </button>
        </div>
      </div>
    </Modal>
  );
}
