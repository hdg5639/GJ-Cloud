"use client";

import { useState, useEffect } from "react";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { VmResponse, ProfileResponse } from "@/lib/types";

interface Props {
  vm: VmResponse;
  onClose: () => void;
  onSuccess: () => void;
}

export default function UpgradeRequestModal({ vm, onClose, onSuccess }: Props) {
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
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div className="bg-white rounded-xl p-6 w-[420px]">
        <h2 className="text-base font-medium text-gray-900 mb-4">플랜 변경</h2>

        <div className="space-y-3 mb-6">
          <div>
            <p className="text-xs text-gray-500 mb-2">현재 플랜</p>
            <div className="px-4 py-3 bg-gray-50 border border-gray-200 rounded-lg">
              <p className="text-sm font-medium text-gray-900">{currentPlan}</p>
            </div>
          </div>

          <div>
            <p className="text-xs text-gray-500 mb-2">변경할 플랜</p>
            <div className="space-y-2">
              {availablePlans.map((plan) => (
                <label key={plan} htmlFor={`modal-plan-${plan}`} className="flex items-center p-3 border border-gray-200 rounded-lg cursor-pointer hover:bg-gray-50">
                  <input
                    id={`modal-plan-${plan}`}
                    type="radio"
                    name="plan"
                    value={plan}
                    checked={selectedPlan === plan}
                    onChange={() => setSelectedPlan(plan as "FREE" | "PRO")}
                    className="w-4 h-4 text-blue-600 border-gray-300"
                  />
                  <span className="ml-3 text-sm font-medium text-gray-900">{plan}</span>
                </label>
              ))}
            </div>
          </div>
        </div>

        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-3 py-2 rounded-lg mb-4">{error}</div>}

        <div className="flex gap-2">
          <button
            onClick={onClose}
            disabled={loading}
            className="flex-1 h-9 border border-gray-300 rounded-md text-sm text-gray-600 disabled:opacity-60"
          >
            취소
          </button>
          <button
            onClick={handleSubmit}
            disabled={loading || !selectedPlan}
            className="flex-1 h-9 bg-blue-600 text-white rounded-md text-sm disabled:opacity-60"
          >
            {loading ? "요청 중..." : "변경 요청"}
          </button>
        </div>
      </div>
    </div>
  );
}
