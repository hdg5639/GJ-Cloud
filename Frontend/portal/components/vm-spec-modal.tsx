"use client";

import { useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { VmResponse } from "@/lib/types";

const PLAN_DISK = {
  FREE: { min: 20, max: 50,  step: 5 },
  PRO:  { min: 20, max: 100, step: 5 },
} as const;

interface Props {
  vm: VmResponse;
  onClose: () => void;
  onSuccess: (updated: VmResponse) => void;
}

export default function VmSpecModal({ vm, onClose, onSuccess }: Props) {
  const { accessToken } = useAuth();
  const [selectedPlan, setSelectedPlan] = useState<"FREE" | "PRO">(vm.planType);
  const [diskSizeGb, setDiskSizeGb] = useState(vm.diskSizeGb);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const planChanged = selectedPlan !== vm.planType;

  // PRO→FREE 다운그레이드는 현재 디스크가 FREE 최대치 초과하면 불가
  const canDowngrade = vm.diskSizeGb <= PLAN_DISK.FREE.max;

  const diskMin = Math.max(vm.diskSizeGb, PLAN_DISK[selectedPlan].min);
  const diskMax = PLAN_DISK[selectedPlan].max;
  const diskStep = PLAN_DISK[selectedPlan].step;

  function handlePlanSelect(plan: "FREE" | "PRO") {
    if (plan === "FREE" && !canDowngrade) return;
    setSelectedPlan(plan);
    // 새 플랜 범위 밖이면 최솟값으로 조정
    const newMin = Math.max(vm.diskSizeGb, PLAN_DISK[plan].min);
    const newMax = PLAN_DISK[plan].max;
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
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div className="bg-white rounded-xl p-6 w-[440px]">
        <h2 className="text-base font-medium text-gray-900 mb-5">스펙 변경</h2>

        {/* 플랜 선택 */}
        <div className="mb-5">
          <p className="text-xs text-gray-500 mb-2">플랜</p>
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
                      ? "border-blue-500 bg-blue-50"
                      : "border-gray-200 hover:bg-gray-50"
                  }`}
                >
                  <p className="text-sm font-medium text-gray-900">{plan}</p>
                  <p className="text-[11px] text-gray-500 mt-0.5">
                    {plan === "FREE" ? "4 vCPU · 6GB RAM" : "8 vCPU · 16GB RAM"}
                  </p>
                  {disabled && (
                    <p className="text-[10px] text-red-500 mt-1">
                      현재 디스크 {vm.diskSizeGb}GB가 FREE 최대 {PLAN_DISK.FREE.max}GB 초과
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
            <label htmlFor="vm-spec-disk" className="text-xs text-gray-500">디스크</label>
            <p className="text-sm font-medium text-gray-900">{diskSizeGb} GB</p>
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
            className="w-full accent-blue-600"
          />
          <div className="flex justify-between mt-1">
            <span className="text-[11px] text-gray-400">{diskMin} GB</span>
            <span className="text-[11px] text-gray-400">{diskMax} GB</span>
          </div>
          {diskSizeGb === vm.diskSizeGb && (
            <p className="text-[11px] text-gray-400 mt-1">현재 {vm.diskSizeGb}GB · 디스크는 축소 불가</p>
          )}
        </div>

        {/* 재부팅 필요 안내 */}
        {planChanged && (
          <div className="flex items-start gap-2 px-3 py-2.5 bg-amber-50 border border-amber-200 rounded-lg mb-4">
            <span className="text-amber-500 text-sm mt-0.5">⚠</span>
            <p className="text-[11px] text-amber-800">
              플랜 변경(CPU/RAM)은 <strong>재부팅 후</strong> 적용됩니다.
            </p>
          </div>
        )}

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-3 py-2 rounded-lg mb-4">
            {error}
          </div>
        )}

        <div className="flex gap-2">
          <button
            onClick={onClose}
            disabled={loading}
            className="flex-1 h-9 border border-gray-300 rounded-md text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-60"
          >
            취소
          </button>
          <button
            onClick={handleSubmit}
            disabled={loading}
            className="flex-1 h-9 bg-blue-600 text-white rounded-md text-sm hover:bg-blue-700 disabled:opacity-60"
          >
            {loading ? "변경 중..." : "변경"}
          </button>
        </div>
      </div>
    </div>
  );
}
