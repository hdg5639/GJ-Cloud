"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { VmAvailabilityResponse, SshKeyResponse } from "@/lib/types";

const PLAN_INFO = {
  FREE: { cores: 4, memory: "5GB", diskMin: 20, diskMax: 50 },
  PRO: { cores: 8, memory: "12GB", diskMin: 50, diskMax: 500 },
};

export default function CreateInstancePage() {
  const router = useRouter();
  const { accessToken } = useAuth();
  const [name, setName] = useState("");
  const [planType, setPlanType] = useState<"FREE" | "PRO">("FREE");
  const [diskSizeGb, setDiskSizeGb] = useState(20);
  const [sshKeyId, setSshKeyId] = useState("");
  const [availability, setAvailability] = useState<VmAvailabilityResponse | null>(null);
  const [sshKeys, setSshKeys] = useState<SshKeyResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!accessToken) return;
    api.vm.availability(accessToken).then(setAvailability).catch(() => {});
    api.user.sshKeys(accessToken).then((keys) => {
      setSshKeys(keys);
      if (keys.length > 0) setSshKeyId(keys[0].id);
    }).catch(() => {});
  }, [accessToken]);

  // 플랜 변경 시 디스크 크기를 플랜 최솟값으로 리셋
  function handlePlanChange(plan: "FREE" | "PRO") {
    setPlanType(plan);
    setDiskSizeGb(PLAN_INFO[plan].diskMin);
  }

  async function handleSubmit() {
    if (!accessToken) return;
    setError(null);
    setLoading(true);
    try {
      const vm = await api.vm.create(accessToken, { name, planType, diskSizeGb, sshKeyId });
      router.push(`/instances/${vm.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "생성에 실패했습니다");
    } finally {
      setLoading(false);
    }
  }

  const freeFull = availability?.free.isFull ?? false;
  const proFull = availability?.pro.isFull ?? false;
  const planInfo = PLAN_INFO[planType];

  return (
    <div className="max-w-xl">
      <button
        onClick={() => router.push("/instances")}
        className="flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-800 mb-4 transition-colors"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M10 12L6 8L10 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
        인스턴스 목록
      </button>

      <div className="bg-white border border-gray-200 rounded-xl p-6">
        <h1 className="text-lg font-medium text-gray-900 mb-1">인스턴스 생성</h1>
        <p className="text-sm text-gray-500 mb-5">플랜과 디스크 크기를 선택하세요</p>

        <div className="flex flex-col gap-1.5 mb-5">
          <label className="text-xs text-gray-500">인스턴스 이름</label>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="my-server"
            className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-[#03C75A]/30 focus:border-[#03C75A]"
          />
        </div>

        <div className="mb-5">
          <label className="text-xs text-gray-500 block mb-2">플랜</label>
          <div className="grid grid-cols-2 gap-2.5">
            {(["FREE", "PRO"] as const)
              .filter(plan => userPlan === "PRO" || plan === "FREE")
              .map((plan) => {
              const info = PLAN_INFO[plan];
              const full = plan === "FREE" ? freeFull : proFull;
              const used = availability?.[plan.toLowerCase() as "free" | "pro"].used ?? 0;
              const total = availability?.[plan.toLowerCase() as "free" | "pro"].total ?? 0;
              const selected = planType === plan;

              return (
                <button
                  key={plan}
                  disabled={full}
                  onClick={() => handlePlanChange(plan)}
                  className={`relative border rounded-lg p-4 text-left transition-colors ${
                    selected ? "border-2 border-[#03C75A]" : "border-gray-200 hover:border-gray-300"
                  } ${full ? "opacity-50 cursor-not-allowed" : ""}`}
                >
                  {selected && (
                    <span className="absolute -top-2.5 left-3 z-10 bg-[#e6faf0] text-[#03C75A] text-[11px] font-medium px-2 py-0.5 rounded-md">
                      선택됨
                    </span>
                  )}
                  <p className="text-sm font-medium text-gray-900 mb-1">{plan}</p>
                  <p className="text-xs text-gray-500 mb-2">
                    {info.cores} vCPU · {info.memory} RAM
                  </p>
                  <div className="flex items-center gap-1.5">
                    <div className="flex gap-0.5">
                      {Array.from({ length: total }).map((_, i) => (
                        <span
                          key={i}
                          className={`w-1.5 h-1.5 rounded-full ${
                            i < used ? (full ? "bg-red-500" : "bg-[#03C75A]") : "bg-gray-200"
                          }`}
                        />
                      ))}
                    </div>
                    <span className={`text-[11px] ${full ? "text-red-600 font-medium" : "text-gray-400"}`}>
                      {full ? `자리 없음 (${used}/${total})` : `${used}/${total} 사용 중`}
                    </span>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        <div className="mb-5">
          <div className="flex items-center justify-between mb-2">
            <label className="text-xs text-gray-500">디스크 크기</label>
            <span className="text-sm font-medium text-gray-900">{diskSizeGb}GB</span>
          </div>
          <input
            type="range"
            min={planInfo.diskMin}
            max={planInfo.diskMax}
            step={5}
            value={diskSizeGb}
            onChange={(e) => setDiskSizeGb(Number(e.target.value))}
            className="w-full accent-[#03C75A]"
          />
          <div className="flex justify-between text-[11px] text-gray-400 mt-1">
            <span>{planInfo.diskMin}GB</span>
            <span>{planInfo.diskMax}GB</span>
          </div>
        </div>

        <div className="flex flex-col gap-1.5 mb-5">
          <label className="text-xs text-gray-500">SSH 키</label>
          {sshKeys.length === 0 ? (
            <div className="text-xs text-gray-400 border border-dashed border-gray-300 rounded-md px-3 py-2.5">
              등록된 SSH 키가 없습니다.{" "}
              <a href="/ssh-keys" className="text-[#03C75A] font-medium">
                SSH 키 등록하기
              </a>
            </div>
          ) : (
            <select
              value={sshKeyId}
              onChange={(e) => setSshKeyId(e.target.value)}
              className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-[#03C75A]/30 focus:border-[#03C75A]"
            >
              {sshKeys.map((key) => (
                <option key={key.id} value={key.id}>
                  {key.name}
                </option>
              ))}
            </select>
          )}
        </div>

        {error && <p className="text-xs text-red-600 mb-3">{error}</p>}

        <button
          onClick={handleSubmit}
          disabled={loading || !name || !sshKeyId}
          className="w-full h-[38px] bg-[#03C75A] text-white rounded-md text-sm font-medium disabled:opacity-60"
        >
          {loading ? "생성 중..." : "인스턴스 생성"}
        </button>
      </div>
    </div>
  );
}
