"use client";

import type { PortResponse } from "@/lib/api-client";
import type { DeploymentTargetResponse } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/badge";
import { cn } from "@/components/ui/cn";

interface DeploymentTargetCardProps {
  target: DeploymentTargetResponse;
  publicPorts: PortResponse[];
  status: string | undefined;
  sourceLabel: string;
  updatedLabel: string;
  redeploying: boolean;
  toggling: boolean;
  deleting: boolean;
  onRedeploy: () => void;
  onViewLatest?: () => void;
  onToggleAutoDeploy: () => void;
  onManageCnames: () => void;
  onEditConfig?: () => void;
  onDelete: () => void;
}

const focusStyle = "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand";

function DomainLink({ port }: { port: PortResponse }) {
  if (port.protocol !== "HTTP") {
    return <span className="min-w-0 break-all font-mono text-xs text-muted">{port.fullDomain}</span>;
  }
  return (
    <a
      href={`https://${port.fullDomain}`}
      target="_blank"
      rel="noopener noreferrer"
      className={cn("inline-flex min-w-0 items-center gap-1 text-xs text-muted hover:text-brand-strong", focusStyle)}
      title={`${port.nickname} · ${port.port} 포트를 새 창에서 열기`}
    >
      <span className="break-all">{port.fullDomain}</span>
      <svg aria-hidden className="h-3 w-3 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5h5m0 0v5m0-5L10 14M19 14v5H5V5h5" />
      </svg>
    </a>
  );
}

export function DeploymentTargetCard({
  target, publicPorts, status, sourceLabel, updatedLabel, redeploying, toggling, deleting,
  onRedeploy, onViewLatest, onToggleAutoDeploy, onManageCnames, onDelete, onEditConfig,
}: DeploymentTargetCardProps) {
  const primaryPort = publicPorts.find((port) => port.protocol === "HTTP");
  return (
    <article className="min-w-0 px-4 py-3 sm:px-5" aria-label={`${target.name} 배포 대상`}>
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <h3 className="min-w-0 break-all text-sm font-bold">{target.name}</h3>
            <StatusBadge tone={status === "SUCCEEDED" ? "ok" : "off"} className="shrink-0">{status ?? "배포 대기"}</StatusBadge>
          </div>
          <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
            <span className="min-w-0 break-all" title={target.repositoryFullName ?? target.repositoryUrl}>
              {target.repositoryFullName ?? target.repositoryUrl}
            </span>
            <span className="min-w-0 break-all font-mono" title={`브랜치: ${target.branch}`}>{target.branch}</span>
            <span className="font-mono" title={`운영 리비전: ${target.latestDeployedRevision ?? "없음"}`}>
              {target.latestDeployedRevision?.slice(0, 8) ?? "리비전 없음"}
            </span>
          </div>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-2">
          {onEditConfig && <Button type="button" size="small" className={focusStyle} onClick={onEditConfig}>구성 편집</Button>}
          {onViewLatest && <Button type="button" size="small" className={focusStyle} onClick={onViewLatest}>최근 배포</Button>}
          <Button type="button" size="small" variant="primary" className={focusStyle} onClick={onRedeploy} disabled={redeploying}>
            {redeploying ? "요청 중..." : "재배포"}
          </Button>
        </div>
      </div>

      <div className="mt-2 flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div className="min-w-0 flex-1">
          {primaryPort ? <DomainLink port={primaryPort} /> : <span className="text-xs text-muted">공개 HTTP 주소 없음</span>}
        </div>
        <button
          type="button"
          onClick={onToggleAutoDeploy}
          disabled={toggling || !target.repositoryFullName}
          aria-pressed={target.autoDeployEnabled}
          aria-label={`${target.name} 자동 배포`}
          className={cn("inline-flex min-h-8 shrink-0 items-center gap-1.5 rounded-md px-1 text-xs transition-colors disabled:cursor-not-allowed disabled:opacity-50", target.autoDeployEnabled ? "text-brand-strong" : "text-muted", focusStyle)}
          title={!target.repositoryFullName ? "GitHub App으로 연결된 대상만 자동 배포를 사용할 수 있습니다." : "Git push 시 자동 배포 설정 변경"}
        >
          <span aria-hidden className={cn("h-1.5 w-1.5 rounded-full", target.autoDeployEnabled ? "bg-brand" : "bg-muted")} />
          {toggling ? "변경 중..." : `자동 배포 ${target.autoDeployEnabled ? "ON" : "OFF"}`}
        </button>
      </div>

      <details className="group mt-1">
        <summary className={cn("flex min-h-8 w-fit cursor-pointer list-none items-center gap-1.5 rounded-md text-xs text-muted hover:text-foreground [&::-webkit-details-marker]:hidden", focusStyle)}>
          <svg aria-hidden className="h-3 w-3 transition-transform group-open:rotate-90 motion-reduce:transition-none" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="m9 5 7 7-7 7" />
          </svg>
          설정 · CNAME {publicPorts.length}개
        </summary>
        <div className="mt-2 border-t border-line pt-3">
          <dl className="flex flex-wrap gap-x-5 gap-y-2 text-xs">
            <div className="flex gap-2"><dt className="text-muted">배포 방식</dt><dd>{sourceLabel}</dd></div>
            <div className="flex gap-2"><dt className="text-muted">최근 변경</dt><dd>{updatedLabel}</dd></div>
          </dl>
          <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
            <h4 className="text-xs font-bold">연결된 공개 CNAME</h4>
            <Button type="button" size="small" className={focusStyle} onClick={onManageCnames}>CNAME 연결 관리</Button>
          </div>
          {publicPorts.length > 0 ? (
            <ul className="mt-1 divide-y divide-line">
              {publicPorts.map((port) => (
                <li key={port.id} className="flex min-w-0 flex-wrap items-center gap-2 py-2">
                  <span className="text-xs text-muted">{port.deploymentAppId ? "자동" : "수동"}</span>
                  <div className="min-w-0 flex-1"><DomainLink port={port} /></div>
                  <span className="font-mono text-xs text-muted">{port.protocol} :{port.port}</span>
                </li>
              ))}
            </ul>
          ) : <p className="mt-2 text-xs text-muted">연결된 CNAME이 없습니다. VM에 등록한 공개 CNAME을 연결할 수 있습니다.</p>}
          <div className="mt-3 flex justify-end border-t border-line pt-3">
            <Button type="button" size="small" variant="danger" className={focusStyle} onClick={onDelete} disabled={deleting}>
              {deleting ? "삭제 중..." : "배포 대상 삭제"}
            </Button>
          </div>
        </div>
      </details>
    </article>
  );
}
