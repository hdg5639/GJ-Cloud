import Link from "next/link";
import type { VmResponse, VmStatus } from "@/lib/types";
import { Button, buttonClass } from "@/components/ui/button";
import { cn } from "@/components/ui/cn";

export type PreviewDeploymentTargetType = "MANAGED" | "USER_VM";

const VM_STATUS_LABEL: Record<VmStatus, string> = {
  PENDING: "대기 중",
  CREATING: "생성 중",
  BOOTING: "부팅 중",
  RUNNING: "실행 중",
  STARTING: "시작 중",
  STOPPING: "중지 중",
  STOPPED: "중지됨",
  SUSPENDING: "정지 처리 중",
  SUSPENDED: "정지됨",
  FAILED: "오류",
  DELETING: "삭제 중",
  DELETED: "삭제됨",
};

interface PreviewDeploymentTargetSectionProps {
  targetType: PreviewDeploymentTargetType;
  onTargetTypeChange: (targetType: PreviewDeploymentTargetType) => void;
  vms: VmResponse[];
  selectedVmId: string;
  onSelectedVmIdChange: (vmId: string) => void;
  loading: boolean;
  loaded: boolean;
  error: string | null;
  onRetry: () => void;
}

export function PreviewDeploymentTargetSection({
  targetType,
  onTargetTypeChange,
  vms,
  selectedVmId,
  onSelectedVmIdChange,
  loading,
  loaded,
  error,
  onRetry,
}: PreviewDeploymentTargetSectionProps) {
  const runningVms = vms.filter((vm) => vm.status === "RUNNING");

  return (
    <section className="mt-5 border-t border-line pt-5" aria-labelledby="preview-deployment-target-heading">
      <div className="mb-3">
        <h2 id="preview-deployment-target-heading" className="text-sm font-extrabold">
          실행 환경
        </h2>
        <p className="mt-1 text-xs leading-5 text-muted">
          잠깐 확인할 때는 공용 환경을, 계속 운영할 Preview는 내 인스턴스를 선택하세요.
        </p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <button
          type="button"
          aria-pressed={targetType === "MANAGED"}
          onClick={() => onTargetTypeChange("MANAGED")}
          className={cn(
            "min-w-0 rounded-xl border p-4 text-left transition-colors",
            targetType === "MANAGED"
              ? "border-brand/60 bg-brand/[0.08] ring-1 ring-brand/20"
              : "border-line-strong bg-background/35 hover:border-brand/35 hover:bg-soft"
          )}
        >
          <span className="flex flex-wrap items-center gap-2">
            <span className="text-sm font-extrabold text-foreground">공용 관리형</span>
            <span className="rounded-full bg-brand/15 px-2 py-0.5 text-[10px] font-extrabold text-brand-strong">
              빠른 확인
            </span>
          </span>
          <span className="mt-2 block text-xs leading-5 text-muted">
            별도 VM 없이 바로 배포합니다. FREE는 6시간, PRO는 24시간 뒤 자동 정리됩니다.
          </span>
        </button>

        <button
          type="button"
          aria-pressed={targetType === "USER_VM"}
          onClick={() => onTargetTypeChange("USER_VM")}
          className={cn(
            "min-w-0 rounded-xl border p-4 text-left transition-colors",
            targetType === "USER_VM"
              ? "border-brand/60 bg-brand/[0.08] ring-1 ring-brand/20"
              : "border-line-strong bg-background/35 hover:border-brand/35 hover:bg-soft"
          )}
        >
          <span className="text-sm font-extrabold text-foreground">내 인스턴스</span>
          <span className="mt-2 block text-xs leading-5 text-muted">
            선택한 VM의 배포 대상으로 추가하고 고정 서브도메인을 발급합니다.
          </span>
        </button>
      </div>

      {targetType === "USER_VM" && (
        <div className="mt-4 rounded-xl border border-line bg-background/35 p-4">
          {loading ? (
            <div className="flex min-h-24 items-center justify-center gap-2 text-xs font-bold text-muted" role="status">
              <span className="size-4 animate-spin rounded-full border-2 border-line-strong border-t-brand" aria-hidden />
              내 인스턴스를 불러오는 중
            </div>
          ) : error ? (
            <div className="flex min-h-24 flex-col items-start justify-center gap-3">
              <p className="text-xs leading-5 text-danger">{error}</p>
              <Button type="button" size="small" onClick={onRetry}>
                다시 불러오기
              </Button>
            </div>
          ) : loaded && vms.length === 0 ? (
            <div className="flex min-h-24 flex-col items-start justify-center gap-3">
              <div>
                <p className="text-sm font-extrabold">아직 내 인스턴스가 없습니다.</p>
                <p className="mt-1 text-xs leading-5 text-muted">공용 관리형을 사용하거나 새 인스턴스를 만든 뒤 다시 선택할 수 있습니다.</p>
              </div>
              <Link href="/instances/new" className={buttonClass({ size: "small" })}>
                인스턴스 만들기
              </Link>
            </div>
          ) : loaded ? (
            <div>
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <p className="text-xs font-extrabold text-foreground">배포할 인스턴스 선택</p>
                <p className="text-[11px] text-muted">실행 중 {runningVms.length}개 / 전체 {vms.length}개</p>
              </div>
              <div className="grid max-h-64 gap-2 overflow-y-auto pr-1">
                {vms.map((vm) => {
                  const selectable = vm.status === "RUNNING";
                  const selected = selectedVmId === vm.id;
                  return (
                    <button
                      key={vm.id}
                      type="button"
                      disabled={!selectable}
                      aria-pressed={selected}
                      onClick={() => onSelectedVmIdChange(vm.id)}
                      className={cn(
                        "flex min-w-0 items-center justify-between gap-3 rounded-lg border px-3 py-3 text-left transition-colors",
                        selected
                          ? "border-brand/60 bg-brand/[0.08]"
                          : "border-line bg-panel hover:border-brand/35",
                        !selectable && "cursor-not-allowed opacity-55 hover:border-line"
                      )}
                    >
                      <span className="min-w-0">
                        <span className="block truncate text-xs font-extrabold text-foreground">{vm.name}</span>
                        <span className="mt-0.5 block truncate text-[11px] text-muted">{vm.internalIp ?? vm.subdomain}</span>
                      </span>
                      <span
                        className={cn(
                          "shrink-0 rounded-full px-2 py-1 text-[10px] font-bold",
                          selectable ? "bg-brand/15 text-brand-strong" : "bg-white/[0.06] text-muted"
                        )}
                      >
                        {VM_STATUS_LABEL[vm.status]}
                      </span>
                    </button>
                  );
                })}
              </div>
              {runningVms.length === 0 && (
                <p className="mt-3 text-[11px] leading-5 text-muted">배포하려면 인스턴스를 먼저 실행해주세요.</p>
              )}
            </div>
          ) : null}
        </div>
      )}
    </section>
  );
}
