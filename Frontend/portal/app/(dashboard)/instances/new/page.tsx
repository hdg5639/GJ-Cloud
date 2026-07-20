"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { VmAvailabilityResponse, SshKeyResponse } from "@/lib/types";
import { Card } from "@/components/ui/panel";
import { Field, Input, Select } from "@/components/ui/field";
import { Button } from "@/components/ui/button";

const PLAN_INFO = {
  FREE: { cores: 4, memory: "5GB", diskMin: 20, diskMax: 50 },
  PRO: { cores: 8, memory: "12GB", diskMin: 20, diskMax: 100 },
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
  const [userPlan, setUserPlan] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    Promise.all([
      api.vm.availability(accessToken),
      api.user.profile(accessToken),
      api.user.sshKeys(accessToken),
    ])
      .then(([availabilityData, profileData, keysData]) => {
        setAvailability(availabilityData);
        setUserPlan(profileData.planType);
        setSshKeys(keysData);
        if (keysData.length > 0) setSshKeyId(keysData[0].id);
      })
      .catch(() => {});
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
        className="flex items-center gap-1.5 text-sm text-muted hover:text-[#3f4c43] mb-4 transition-colors"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M10 12L6 8L10 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
        인스턴스 목록
      </button>

      <Card>
        <h1 className="mb-1 text-lg font-bold">인스턴스 생성</h1>
        <p className="mb-5 text-sm text-muted">플랜과 디스크 크기를 선택하세요</p>

        <Field label="인스턴스 이름" htmlFor="new-instance-name">
          <Input
            id="new-instance-name"
            name="new-instance-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="my-server"
          />
        </Field>

        <div className="mb-5">
          <span className="text-xs font-bold text-muted block mb-2">플랜</span>
          <div className="grid grid-cols-2 gap-2.5">
            {(["FREE", "PRO"] as const).map((plan) => {
              const info = PLAN_INFO[plan];
              const isFree = plan === "FREE";
              const full = isFree ? freeFull : proFull;
              const planLocked = plan === "PRO" && userPlan === "FREE";
              const used = availability?.[plan.toLowerCase() as "free" | "pro"].used ?? 0;
              const total = availability?.[plan.toLowerCase() as "free" | "pro"].total ?? 0;
              const selected = planType === plan;

              return (
                <button
                  key={plan}
                  disabled={full || planLocked}
                  onClick={() => handlePlanChange(plan)}
                  className={`relative rounded-[12px] border p-4 text-left transition-colors ${
                    selected ? "border-brand shadow-[inset_0_0_0_1px_var(--brand)]" : "border-line-strong hover:border-[#b9c4bd]"
                  } ${(full || planLocked) ? "opacity-50 cursor-not-allowed" : ""}`}
                >
                  {selected && (
                    <span className="absolute -top-2.5 left-3 z-10 bg-soft text-brand-strong text-[11px] font-bold px-2 py-0.5 rounded-md">
                      선택됨
                    </span>
                  )}
                  {planLocked && (
                    <span className="absolute -top-2.5 right-3 z-10 bg-[#fffaf0] text-[#9c6b1f] text-[11px] font-bold px-2 py-0.5 rounded-md">
                      프로 플랜만
                    </span>
                  )}
                  <p className="text-sm font-bold mb-1">{plan}</p>
                  <p className="text-xs text-muted mb-2">
                    {info.cores} vCPU · {info.memory} RAM
                  </p>
                  <div className="flex items-center gap-1.5">
                    <div className="flex gap-0.5">
                      {Array.from({ length: total }).map((_, i) => (
                        <span
                          key={i}
                          className={`w-1.5 h-1.5 rounded-full ${
                            i < used ? (full ? "bg-danger" : "bg-brand") : "bg-line-strong"
                          }`}
                        />
                      ))}
                    </div>
                    <span className={`text-[11px] ${full ? "text-danger font-bold" : "text-muted-soft"}`}>
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
            <label htmlFor="new-instance-disk" className="text-xs font-bold text-muted">디스크 크기</label>
            <span className="text-sm font-bold">{diskSizeGb}GB</span>
          </div>
          <input
            id="new-instance-disk"
            name="new-instance-disk"
            type="range"
            min={planInfo.diskMin}
            max={planInfo.diskMax}
            step={5}
            value={diskSizeGb}
            onChange={(e) => setDiskSizeGb(Number(e.target.value))}
            className="w-full accent-brand"
          />
          <div className="flex justify-between text-[11px] text-muted-soft mt-1">
            <span>{planInfo.diskMin}GB</span>
            <span>{planInfo.diskMax}GB</span>
          </div>
        </div>

        <Field label="SSH 키" htmlFor="new-instance-ssh-key">
          {sshKeys.length === 0 ? (
            <div className="text-xs text-muted-soft border border-dashed border-line-strong rounded-md px-3 py-2.5 font-normal">
              등록된 SSH 키가 없습니다.{" "}
              <a href="/ssh-keys" className="text-brand-strong font-bold">
                SSH 키 등록하기
              </a>
            </div>
          ) : (
            <Select
              id="new-instance-ssh-key"
              name="new-instance-ssh-key"
              value={sshKeyId}
              onChange={(e) => setSshKeyId(e.target.value)}
            >
              {sshKeys.map((key) => (
                <option key={key.id} value={key.id}>
                  {key.name}
                </option>
              ))}
            </Select>
          )}
        </Field>

        {error && <p className="text-xs text-danger mb-3">{error}</p>}

        <Button
          variant="primary"
          onClick={handleSubmit}
          disabled={loading || !name || !sshKeyId}
          className="w-full"
        >
          {loading ? "생성 중..." : "인스턴스 생성"}
        </Button>
      </Card>
    </div>
  );
}
