"use client";

import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api, type PortResponse } from "@/lib/api-client";
import type {
  DeploymentResponse,
  DeploymentSpec,
  EnvironmentFile,
  ExposedRoute,
  HealthCheck,
  ServiceCard,
  InfraSelection,
  ComposeSpecResponse,
  GenerationStatus,
  UnresolvedField,
  ComposeReviewFinding,
  ComposeDetectionResult,
  DetectedComposeFile,
  ComposeRouterPlanResult,
  ComposeRouterRoute,
  ComposeRouterRouteOverride,
  DiscoveredService,
  NetworkInfo,
  DeploymentTargetResponse,
  GithubRepositoryResponse,
} from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";
import { Modal } from "@/components/ui/modal";
import { Field, Input, Select, Textarea } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { Table, Th, Td } from "@/components/ui/table";
import { StatusBadge } from "@/components/ui/badge";
import { cn } from "@/components/ui/cn";
import { InstanceSectionNav } from "@/components/ui/instance-section-nav";

type NetworkMode = "create" | "reuse";

const STATUS_TONE: Record<string, "ok" | "off"> = {
  QUEUED: "off",
  CLONING: "off",
  UPLOADING: "off",
  VALIDATING: "off",
  BUILDING: "off",
  SWAPPING: "off",
  HEALTH_CHECKING: "off",
  ROUTING: "off",
  SUCCEEDED: "ok",
  FAILED: "off",
  ROLLING_BACK: "off",
  ROLLED_BACK: "off",
  STOPPING: "off",
  STOPPED: "off",
};

const SOURCE_TYPE_LABEL: Record<string, string> = {
  RAW_COMPOSE: "사용자 지정",
  TEMPLATE_SPEC: "기본 템플릿",
  AI_SPEC: "AI 자동생성",
};

const TRIGGER_TYPE_LABEL: Record<string, string> = {
  MANUAL: "수동",
  GIT_PUSH: "Git push",
  RETRY: "재시도",
  ROLLBACK: "롤백",
};

function formatDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

type CreateTab = "compose" | "ai";
type RepositorySource = "github" | "url";
type SubdomainCheckStatus = "idle" | "checking" | "available" | "taken" | "reserved" | "pro-only";
type CreateStep = 1 | 2 | 3 | 4;

// 배포 상세 페이지의 "재시도" 버튼 → 이 목록 페이지로 넘어올 때 프리필 스펙을 임시로 담아두는 키
// (같은 형식의 키를 deployments/[deploymentId]/page.tsx에서도 그대로 사용)
function retryStorageKey(vmId: string): string {
  return `retryDeployment:${vmId}`;
}

const emptyExposedRoute = (): ExposedRoute => ({ serviceName: "", port: 80, protocol: "HTTP", visibility: "PUBLIC", nickname: "", customSubdomain: "" });
const emptyHealthCheck = (): HealthCheck => ({ serviceName: "", path: "/", hostPort: undefined, containerPort: undefined });
const emptyEnvFile = (): EnvironmentFile => ({ vmPath: ".env", content: "" });
const emptyServiceCard = (): ServiceCard => ({ name: "", runtime: "docker", context: ".", containerPort: 3000, expose: true });
const emptyInfra = (): InfraSelection => ({ type: "postgres", version: "" });

// 모달 내 섹션 하나가 어떤 역할인지 한눈에 보이도록 제목+설명을 통일된 형태로 감싸는 래퍼
function Section({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <section className="rounded-panel border border-line bg-panel p-5">
      <h3 className="mb-1 text-sm font-extrabold">{title}</h3>
      <p className="mb-4 text-xs text-muted">{description}</p>
      {children}
    </section>
  );
}

function ComposeReviewFindingsView({ findings }: { findings: ComposeReviewFinding[] | null }) {
  if (!findings) return null;
  return (
    <div className="space-y-2 rounded-md border border-line bg-white/[0.03] p-3 text-xs text-foreground">
      <p className="text-[11px] text-muted-soft">AI 검수는 참고용이며 배포를 막지 않습니다.</p>
      {findings.length === 0 ? (
        <p className="text-muted-soft">특이사항이 없습니다.</p>
      ) : (
        findings.map((finding, index) => (
          <div
            key={`${finding.code}-${index}`}
            className="border-l-2 pl-2"
            style={{
              borderColor: finding.severity === "CRITICAL"
                ? "#ff6b6b"
                : finding.severity === "WARNING" ? "#e8b657" : "#9aa39a",
            }}
          >
            <p className="font-bold text-foreground">
              [{finding.severity}] {finding.service && <span className="font-mono">{finding.service}</span>} {finding.message}
            </p>
            {finding.remediation && <p className="mt-0.5 text-muted">→ {finding.remediation}</p>}
          </div>
        ))
      )}
    </div>
  );
}

function ComposeDetectionCard({
  result,
  selectedPath,
  detecting,
  reviewing,
  reviewFindings,
  onSelect,
  onApply,
  onReview,
  onRefresh,
}: {
  result: ComposeDetectionResult;
  selectedPath: string;
  detecting: boolean;
  reviewing: boolean;
  reviewFindings: ComposeReviewFinding[] | null;
  onSelect: (path: string) => void;
  onApply: (file: DetectedComposeFile) => void;
  onReview: (file: DetectedComposeFile) => void;
  onRefresh: () => void;
}) {
  const selected = result.files.find((file) => file.path === selectedPath) ?? result.files[0];
  if (!selected) return null;

  return (
    <section className="rounded-panel border border-[#64d98b]/35 bg-[#64d98b]/[0.055] p-5">
      <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="mb-1 flex items-center gap-2">
            <span className="flex h-5 w-5 items-center justify-center rounded-full bg-[#64d98b]/15 text-xs text-[#73e09a]">✓</span>
            <h3 className="text-sm font-extrabold text-foreground">Compose 파일이 감지되었습니다</h3>
          </div>
          <p className="text-xs text-muted">
            저장소의 실제 파일을 불러왔습니다. 내용을 확인하거나 AI 검수를 거친 뒤 그대로 배포에 사용할 수 있습니다.
          </p>
        </div>
        <button
          type="button"
          onClick={onRefresh}
          disabled={detecting || reviewing}
          className="text-xs font-bold text-muted hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
        >
          {detecting ? "탐지 중..." : "다시 탐지"}
        </button>
      </div>

      {result.files.length > 1 ? (
        <Select value={selected.path} onChange={(event) => onSelect(event.target.value)} className="mb-3">
          {result.files.map((file) => (
            <option key={file.path} value={file.path}>{file.path}</option>
          ))}
        </Select>
      ) : (
        <div className="mb-3 rounded-md border border-[#64d98b]/20 bg-black/10 px-3 py-2 font-mono text-xs text-[#8be5aa]">
          {selected.path}
        </div>
      )}

      <pre className="max-h-56 overflow-auto rounded-[10px] border border-line bg-[#0c0e12] p-3 font-mono text-[11px] leading-[1.6] text-foreground">
        {selected.content}
      </pre>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <Button type="button" variant="primary" onClick={() => onApply(selected)} disabled={detecting || reviewing}>
          이 Compose로 배포
        </Button>
        <Button type="button" onClick={() => onReview(selected)} disabled={detecting || reviewing}>
          {reviewing ? "AI 검수 중..." : "AI 검수"}
        </Button>
        <span className="text-[11px] text-muted-soft">
          적용 시 배포 디렉터리도 <span className="font-mono">{selected.directory}</span>(으)로 맞춰집니다.
        </span>
      </div>
      {result.warnings.length > 0 && (
        <div className="mt-3 space-y-1 text-[11px] text-[#e8b657]">
          {result.warnings.map((warning) => <p key={warning}>⚠ {warning}</p>)}
        </div>
      )}
      <div className="mt-3">
        <ComposeReviewFindingsView findings={reviewFindings} />
      </div>
    </section>
  );
}

// 한 서비스의 현재 라우팅 보정값을 계산 — 사용자가 아직 손대지 않았으면 플래너가 추론한 route 값을 기본으로 쓴다.
function effectiveRouteOverride(
  route: ComposeRouterRoute,
  overrides: Record<string, ComposeRouterRouteOverride>
): ComposeRouterRouteOverride {
  const existing = overrides[route.serviceName];
  if (existing) return existing;
  return route.mode === "DOMAIN"
    ? { mode: "DOMAIN", routePath: null, stripPrefix: false, customSubdomain: route.customSubdomain ?? "" }
    : { mode: "PREFIX", routePath: route.routePath ?? `/${route.serviceName}`, stripPrefix: route.stripPrefix, customSubdomain: null };
}

// 서비스별 라우팅 편집 행 — 각 서비스를 '경로(Prefix, 통합 도메인 아래 경로)' 또는 '도메인(전용 서브도메인,
// 호스트 기반)' 중 하나로 지정한다. compose 흐름(ComposeRouterPlanCard)과 AI 분석 결과 흐름에서 재사용한다.
function RouteModeRows({
  routes,
  overrides,
  planType,
  subdomainCheck,
  disabled,
  onOverrideChange,
  onDomainSubdomainChange,
}: {
  routes: ComposeRouterRoute[];
  overrides: Record<string, ComposeRouterRouteOverride>;
  planType: string | null;
  subdomainCheck: Record<string, SubdomainCheckStatus>;
  disabled: boolean;
  onOverrideChange: (service: string, override: ComposeRouterRouteOverride) => void;
  onDomainSubdomainChange: (service: string, value: string) => void;
}) {
  if (routes.length === 0) return null;
  const isPro = planType === "PRO";
  return (
    <div className="mb-4 overflow-hidden rounded-[10px] border border-line">
      {routes.map((route: ComposeRouterRoute) => {
        const override = effectiveRouteOverride(route, overrides);
        const isDomain = override.mode === "DOMAIN";
        const check = subdomainCheck[route.serviceName] ?? "idle";
        return (
          <div
            key={route.serviceName}
            className={cn(
              "border-b border-line px-3 py-3 text-xs last:border-b-0",
              !route.root && !isDomain && route.confidence === "LOW" && "bg-[#e8b657]/[0.045]"
            )}
          >
            <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <span className="font-mono font-bold text-foreground">{route.serviceName}</span>
                {!route.root && !isDomain && (
                  <>
                    <span className={cn(
                      "rounded-full border px-1.5 py-0.5 text-[9px] font-extrabold",
                      route.confidence === "LOW"
                        ? "border-[#e8b657]/30 bg-[#e8b657]/10 text-[#e8b657]"
                        : "border-[#64d98b]/25 bg-[#64d98b]/10 text-[#8be5aa]"
                    )}>
                      {route.confidence === "LOW" ? "낮은 신뢰도" : "자동 인식"}
                    </span>
                    <span className="text-[10px] text-muted-soft">
                      {route.source === "HEALTHCHECK" && "healthcheck 근거"}
                      {route.source === "SERVICE_NAME" && "서비스명 추정"}
                      {route.source === "USER" && "사용자 지정"}
                      {route.source === "ROOT_DEFAULT" && "루트 진입점"}
                    </span>
                  </>
                )}
              </div>
              <span className="font-mono text-[11px] text-muted">→ {route.upstream}</span>
            </div>
            {route.root ? (
              <div className="rounded border border-line bg-black/10 px-2.5 py-2 font-mono text-foreground">
                / <span className="ml-2 font-sans text-[10px] text-muted-soft">루트 서비스는 기본 진입점(공용 도메인)을 담당합니다.</span>
              </div>
            ) : (
              <div className="space-y-2">
                <div className="inline-flex overflow-hidden rounded border border-line-strong text-[11px]">
                  <button
                    type="button"
                    disabled={disabled}
                    onClick={() => onOverrideChange(route.serviceName, {
                      mode: "PREFIX",
                      routePath: override.routePath ?? route.routePath ?? `/${route.serviceName}`,
                      stripPrefix: override.stripPrefix,
                      customSubdomain: null,
                    })}
                    className={cn("px-2.5 py-1 font-bold", !isDomain ? "bg-brand text-white" : "text-muted")}
                  >
                    경로(Prefix)
                  </button>
                  <button
                    type="button"
                    disabled={disabled}
                    onClick={() => onOverrideChange(route.serviceName, {
                      mode: "DOMAIN",
                      routePath: null,
                      stripPrefix: false,
                      customSubdomain: override.customSubdomain ?? route.customSubdomain ?? "",
                    })}
                    className={cn("border-l border-line-strong px-2.5 py-1 font-bold", isDomain ? "bg-brand text-white" : "text-muted")}
                  >
                    도메인
                  </button>
                </div>
                {isDomain ? (
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <div className={cn(
                        "flex h-9 min-w-[220px] flex-1 items-center overflow-hidden rounded border border-line-strong",
                        isPro ? "bg-background" : "bg-black/10"
                      )}>
                        <input
                          value={override.customSubdomain ?? ""}
                          disabled={disabled || !isPro}
                          onChange={(event) => onDomainSubdomainChange(route.serviceName, event.target.value)}
                          placeholder="예: community"
                          maxLength={30}
                          className="h-full min-w-0 flex-1 bg-transparent px-2.5 text-xs outline-none disabled:cursor-not-allowed disabled:text-muted-soft"
                        />
                        <span className="shrink-0 border-l border-line-strong px-2.5 text-[11px] text-muted-soft">.gamjabox.cloud</span>
                      </div>
                      {isPro && override.customSubdomain && (
                        <span className={cn(
                          "shrink-0 text-[10px]",
                          check === "available" ? "text-brand-strong" : check === "checking" ? "text-muted-soft" : "text-danger"
                        )}>
                          {check === "checking" && "확인 중..."}
                          {check === "available" && "✓ 사용 가능"}
                          {check === "taken" && "이미 사용 중"}
                          {check === "reserved" && "예약된 이름"}
                          {check === "pro-only" && "PRO 전용"}
                        </span>
                      )}
                    </div>
                    <p className={cn("mt-1 text-[10px]", isPro ? "text-muted-soft" : "font-medium text-[#e8b657]")}>
                      {isPro
                        ? "이 서비스는 전용 서브도메인으로 노출되고 Caddy가 Host로 라우팅합니다."
                        : "도메인 모드(전용 서브도메인)는 PRO 플랜에서 사용할 수 있습니다."}
                    </p>
                  </div>
                ) : (
                  <div className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
                    <Input
                      value={override.routePath ?? ""}
                      disabled={disabled}
                      onChange={(event) => onOverrideChange(route.serviceName, {
                        mode: "PREFIX",
                        routePath: event.target.value,
                        stripPrefix: override.stripPrefix,
                        customSubdomain: null,
                      })}
                      placeholder="/api/service"
                      className="font-mono"
                    />
                    <label className="flex items-center gap-1.5 whitespace-nowrap text-[11px] text-muted">
                      <input
                        type="checkbox"
                        checked={override.stripPrefix}
                        disabled={disabled}
                        onChange={(event) => onOverrideChange(route.serviceName, {
                          mode: "PREFIX",
                          routePath: override.routePath ?? route.routePath ?? `/${route.serviceName}`,
                          stripPrefix: event.target.checked,
                          customSubdomain: null,
                        })}
                        className="accent-brand"
                      />
                      전달 전 Prefix 제거
                    </label>
                  </div>
                )}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

function ComposeRouterPlanCard({
  plan,
  planning,
  applied,
  hostPort,
  servicePorts,
  routeOverrides,
  planType,
  subdomainCheck,
  onHostPortChange,
  onServicePortChange,
  onRouteOverrideChange,
  onDomainSubdomainChange,
  onReplan,
  onApply,
}: {
  plan: ComposeRouterPlanResult;
  planning: boolean;
  applied: boolean;
  hostPort: number;
  servicePorts: Record<string, number>;
  routeOverrides: Record<string, ComposeRouterRouteOverride>;
  planType: string | null;
  subdomainCheck: Record<string, SubdomainCheckStatus>;
  onHostPortChange: (port: number) => void;
  onServicePortChange: (service: string, port: number) => void;
  onRouteOverrideChange: (service: string, override: ComposeRouterRouteOverride) => void;
  onDomainSubdomainChange: (service: string, value: string) => void;
  onReplan: () => void;
  onApply: () => void;
}) {
  const lowConfidenceRoutes = plan.routes.filter((route) => route.confidence === "LOW");

  if (plan.status === "ALREADY_CONFIGURED") {
    return (
      <div className="rounded-panel border border-[#64d98b]/30 bg-[#64d98b]/[0.05] p-4 text-xs text-muted">
        <p className="font-bold text-[#8be5aa]">기존 라우터 설정을 감지했습니다</p>
        <p className="mt-1">
          <span className="font-mono text-foreground">{plan.routerServiceName}</span> 서비스를 그대로 유지하며 자동 변경하지 않습니다.
        </p>
      </div>
    );
  }

  if (plan.status === "NOT_REQUIRED") {
    const direct = plan.routes[0];
    return (
      <div className="rounded-panel border border-line bg-white/[0.025] p-4 text-xs text-muted">
        <p className="font-bold text-foreground">단일 서비스 직접 연결</p>
        <p className="mt-1">
          별도 Caddy 없이 <span className="font-mono text-foreground">{direct?.serviceName ?? "서비스"}</span>
          {direct?.hostPort ? `의 호스트 ${direct.hostPort}번 포트` : " 포트"}로 직접 연결합니다.
        </p>
      </div>
    );
  }

  return (
    <section className={cn(
      "rounded-panel border p-5",
      plan.status === "NEEDS_INPUT"
        ? "border-[#e8b657]/30 bg-[#e8b657]/[0.055]"
        : "border-[#75a9ff]/30 bg-[#75a9ff]/[0.045]"
    )}>
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-sm font-extrabold text-foreground">
            {plan.status === "NEEDS_INPUT"
              ? "Caddy 라우터 구성에 추가 정보가 필요합니다"
              : lowConfidenceRoutes.length > 0
                ? "Caddy Prefix 확인이 필요합니다"
                : "Caddy 다중 서비스 라우터"}
          </h3>
          <p className="mt-1 text-xs text-muted">
            서비스 직접 노출을 내부 네트워크로 전환하고 하나의 공개 진입점에서 경로별로 전달합니다.
          </p>
        </div>
        {applied && (
          <span className="rounded-full border border-[#64d98b]/25 bg-[#64d98b]/10 px-2.5 py-1 text-[10px] font-extrabold text-[#8be5aa]">
            Compose에 자동 적용됨
          </span>
        )}
      </div>

      <div className="mb-4 grid gap-3 sm:grid-cols-2">
        <Field label="Caddy 호스트 진입 포트" htmlFor="router-host-port">
          <Input
            id="router-host-port"
            type="number"
            min={1}
            max={65535}
            value={hostPort}
            disabled={applied}
            onChange={(event) => onHostPortChange(Number(event.target.value))}
          />
        </Field>
        {plan.unresolvedServices.map((service) => (
          service.portRequired ? (
            <Field
              key={service.serviceName}
              label={`${service.serviceName} 컨테이너 포트`}
              htmlFor={`router-service-${service.serviceName}`}
            >
              <Input
                id={`router-service-${service.serviceName}`}
                type="number"
                min={1}
                max={65535}
                value={servicePorts[service.serviceName] || ""}
                placeholder="예: 3000"
                onChange={(event) => onServicePortChange(service.serviceName, Number(event.target.value))}
              />
              <p className="mt-1 text-[11px] font-normal normal-case text-muted-soft">{service.reason}</p>
            </Field>
          ) : (
            <div key={service.serviceName} className="rounded-md border border-[#e8b657]/20 bg-black/10 p-3 text-xs text-[#e8b657]">
              <p className="font-bold">{service.serviceName}</p>
              <p className="mt-1 text-[11px]">{service.reason} Compose에서 해당 옵션을 정리한 뒤 다시 분석하세요.</p>
            </div>
          )
        ))}
      </div>

      <RouteModeRows
        routes={plan.routes}
        overrides={routeOverrides}
        planType={planType}
        subdomainCheck={subdomainCheck}
        disabled={applied}
        onOverrideChange={onRouteOverrideChange}
        onDomainSubdomainChange={onDomainSubdomainChange}
      />

      {plan.routerConfig && (
        <details className="mb-4 rounded-[10px] border border-line bg-black/10">
          <summary className="cursor-pointer px-3 py-2 text-xs font-bold text-muted">생성될 Caddyfile 보기</summary>
          <pre className="max-h-56 overflow-auto border-t border-line bg-[#0c0e12] p-3 font-mono text-[11px] leading-[1.6] text-foreground">
            {plan.routerConfig}
          </pre>
        </details>
      )}

      {plan.warnings.length > 0 && (
        <div className="mb-4 space-y-1 text-[11px] text-[#e8b657]">
          {plan.warnings.map((warning) => <p key={warning}>⚠ {warning}</p>)}
        </div>
      )}

      <div className="flex flex-wrap gap-2">
        {!applied && (
          <Button type="button" onClick={onReplan} disabled={planning}>
            {planning ? "라우터 분석 중..." : "설정 다시 계산"}
          </Button>
        )}
        {plan.status === "ADDED" && !applied && (
          <Button type="button" variant="primary" onClick={onApply} disabled={planning}>
            Compose에 적용
          </Button>
        )}
      </div>
    </section>
  );
}

function PublicEndpointCnameCard({
  endpoint,
  throughCaddy,
  planType,
  customSubdomain,
  check,
  onChange,
}: {
  endpoint: ExposedRoute | null;
  throughCaddy: boolean;
  planType: string | null;
  customSubdomain: string;
  check: SubdomainCheckStatus;
  onChange: (value: string) => void;
}) {
  if (!endpoint) return null;
  const isPro = planType === "PRO";
  return (
    <section className="rounded-panel border border-[#75a9ff]/30 bg-[#75a9ff]/[0.045] p-5">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-bold text-[#a9c8ff]">최종 공개 진입점</p>
          <h3 className="mt-1 text-sm font-extrabold text-foreground">
            {throughCaddy ? "Caddy 통합 라우터" : "단일 서비스 직접 연결"}
          </h3>
          <p className="mt-1 text-xs text-muted">
            <span className="font-mono text-foreground">{endpoint.serviceName}</span>
            <span className="mx-1.5">·</span>
            호스트 <span className="font-mono text-foreground">{endpoint.port}</span>번 포트
          </p>
        </div>
        <span className="rounded-full border border-[#64d98b]/25 bg-[#64d98b]/10 px-2.5 py-1 text-[10px] font-extrabold text-[#8be5aa]">
          {throughCaddy ? "멀티 서비스" : "단일 서비스"}
        </span>
      </div>

      <div className={cn(
        "rounded-md border p-3",
        isPro ? "border-line-strong bg-white/[0.025]" : "border-[#e8b657]/25 bg-[#e8b657]/[0.045]"
      )}>
        <div className="mb-2 flex items-center gap-1.5">
          <span className="text-xs font-bold text-foreground">커스텀 CNAME</span>
          {!isPro ? (
            <span className="rounded border border-[#e8b657]/30 bg-[#e8b657]/10 px-1.5 py-0.5 text-[10px] font-bold text-[#e8b657]">
              PRO에서 잠금 해제
            </span>
          ) : (
            <span className="text-[10px] text-accent">PRO</span>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <div className={cn(
            "flex h-9 min-w-[240px] flex-1 items-center overflow-hidden rounded border border-line-strong",
            isPro ? "bg-background" : "bg-black/10"
          )}>
            <input
              value={customSubdomain}
              onChange={(event) => onChange(event.target.value)}
              placeholder="예: portfolio"
              maxLength={30}
              pattern="^[a-z0-9]+(-[a-z0-9]+)*$"
              disabled={!isPro}
              className="h-full min-w-0 flex-1 bg-transparent px-2.5 text-xs outline-none disabled:cursor-not-allowed disabled:text-muted-soft"
            />
            <span className="shrink-0 border-l border-line-strong px-2.5 text-[11px] text-muted-soft">
              .gamjabox.cloud
            </span>
          </div>
          {isPro && customSubdomain && (
            <span className={cn(
              "shrink-0 text-[10px]",
              check === "available"
                ? "text-brand-strong"
                : check === "checking" ? "text-muted-soft" : "text-danger"
            )}>
              {check === "checking" && "확인 중..."}
              {check === "available" && "✓ 사용 가능"}
              {check === "taken" && "이미 사용 중"}
              {check === "reserved" && "예약된 이름"}
              {check === "pro-only" && "PRO 전용"}
            </span>
          )}
        </div>
        <p className={cn("mt-2 text-[10px]", isPro ? "text-muted-soft" : "font-medium text-[#e8b657]")}>
          {isPro
            ? "비워두면 VM 식별자가 포함된 기본 주소를 자동 생성합니다."
            : "PRO 플랜에서는 자동 식별자 없이 원하는 주소를 최종 공개 진입점에 연결할 수 있습니다."}
        </p>
      </div>
    </section>
  );
}

function discoveredServiceCard(service: DiscoveredService): ServiceCard {
  return {
    name: service.name,
    runtime: service.runtime,
    context: service.context,
    containerPort: service.containerPort,
    javaVersion: service.runtime === "java" ? 21 : undefined,
    buildTool: service.runtime === "java" ? "maven" : undefined,
    nodeVersion: service.runtime === "node" ? 22 : undefined,
    pythonVersion: service.runtime === "python" ? "3.11" : undefined,
    expose: service.expose,
  };
}

function normalizeServiceContext(context: string | undefined): string {
  const normalized = (context || ".").trim().replaceAll("\\", "/").replace(/^\.\/+/, "").replace(/\/+$/, "");
  return normalized || ".";
}

function mergeDiscoveredServiceCards(
  previous: ServiceCard[],
  discovered: DiscoveredService[]
): ServiceCard[] {
  if (discovered.length === 0) return previous;
  const consumed = new Set<number>();
  const merged = discovered.map((service) => {
    const hintIndex = previous.findIndex((hint, index) => (
      !consumed.has(index)
      && (
        normalizeServiceContext(hint.context) === normalizeServiceContext(service.context)
        || (hint.name && hint.name.toLowerCase() === service.name.toLowerCase())
      )
    ));
    if (hintIndex < 0) return discoveredServiceCard(service);
    consumed.add(hintIndex);
    const hint = previous[hintIndex];
    return {
      ...discoveredServiceCard(service),
      ...hint,
      context: service.context,
    };
  });
  const hasSubmodules = discovered.some((service) => normalizeServiceContext(service.context) !== ".");
  previous.forEach((hint, index) => {
    if (consumed.has(index) || !hint.name.trim()) return;
    if (hasSubmodules && normalizeServiceContext(hint.context) === ".") return;
    merged.push(hint);
  });
  return merged;
}

function resolveServiceContext(
  rootContext: string,
  serviceContext: string,
  discovered: DiscoveredService[] | undefined
): string {
  const normalized = normalizeServiceContext(serviceContext);
  const isRepositoryRelativeDiscovery = (discovered ?? []).some(
    (service) => normalizeServiceContext(service.context) === normalized
  );
  return isRepositoryRelativeDiscovery
    ? normalized
    : joinRepositoryContext(rootContext, serviceContext);
}

function DeploymentWizardProgress({
  createTab,
  currentStep,
  furthestStep,
  onStepChange,
}: {
  createTab: CreateTab;
  currentStep: CreateStep;
  furthestStep: CreateStep;
  onStepChange: (step: CreateStep) => void;
}) {
  const steps: Array<{ step: CreateStep; label: string }> = [
    { step: 1, label: "방식 선택" },
    { step: 2, label: "저장소 설정" },
    { step: 3, label: createTab === "ai" ? "서비스 힌트" : "Compose 작성" },
    { step: 4, label: "검토 및 배포" },
  ];

  return (
    <ol className="grid grid-cols-4 border-b border-line bg-panel px-6 py-3">
      {steps.map(({ step, label }, index) => {
        const active = currentStep === step;
        const visited = step <= furthestStep;
        return (
          <li key={step} className="relative">
            {index < steps.length - 1 && (
              <span
                className={cn(
                  "absolute left-[calc(50%+18px)] right-[calc(-50%+18px)] top-[15px] h-px",
                  step < furthestStep ? "bg-brand/60" : "bg-line-strong"
                )}
              />
            )}
            <button
              type="button"
              onClick={() => onStepChange(step)}
              disabled={!visited}
              className="relative z-10 flex w-full flex-col items-center gap-1.5 disabled:cursor-not-allowed"
              aria-current={active ? "step" : undefined}
            >
              <span
                className={cn(
                  "flex h-[30px] w-[30px] items-center justify-center rounded-full border text-xs font-extrabold transition-colors",
                  active
                    ? "border-brand bg-brand text-[#0a0c08]"
                    : visited
                      ? "border-brand/60 bg-panel text-brand-strong"
                      : "border-line-strong bg-panel text-muted-soft"
                )}
              >
                {step < currentStep ? "✓" : step}
              </span>
              <span className={cn("text-[11px] font-bold", active ? "text-foreground" : "text-muted-soft")}>
                {label}
              </span>
            </button>
          </li>
        );
      })}
    </ol>
  );
}

function joinRepositoryContext(rootContext: string, serviceContext: string): string {
  const root = rootContext.trim().replace(/^\.?\/+|\/+$/g, "");
  const service = serviceContext.trim().replace(/^\.?\/+|\/+$/g, "");
  if (!root) return service || ".";
  if (!service || service === ".") return root;
  return `${root}/${service}`;
}

export default function DeploymentsPage() {
  const params = useParams();
  const router = useRouter();
  const vmId = params.id as string;
  const { accessToken } = useAuth();

  const [deployments, setDeployments] = useState<DeploymentResponse[]>([]);
  const [deploymentTargets, setDeploymentTargets] = useState<DeploymentTargetResponse[]>([]);
  const [deploymentPorts, setDeploymentPorts] = useState<PortResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [togglingTargetId, setTogglingTargetId] = useState<string | null>(null);
  const [deletingTargetId, setDeletingTargetId] = useState<string | null>(null);
  const [targetPendingDeletion, setTargetPendingDeletion] = useState<DeploymentTargetResponse | null>(null);
  const [deleteTargetError, setDeleteTargetError] = useState<string | null>(null);

  const [showCreate, setShowCreate] = useState(false);
  const [createTab, setCreateTab] = useState<CreateTab>("compose");
  const [createStep, setCreateStep] = useState<CreateStep>(1);
  const [furthestStepByTab, setFurthestStepByTab] = useState<Record<CreateTab, CreateStep>>({
    compose: 1,
    ai: 1,
  });
  const [submitting, setSubmitting] = useState(false);
  const [retryingId, setRetryingId] = useState<string | null>(null);
  const [rollingBackId, setRollingBackId] = useState<string | null>(null);
  const [retryNotice, setRetryNotice] = useState(false);

  // 공통 (repo)
  const [repositorySource, setRepositorySource] = useState<RepositorySource>("github");
  const [manualRepoUrl, setManualRepoUrl] = useState("");
  const [manualBranch, setManualBranch] = useState("main");
  const [githubBranch, setGithubBranch] = useState("main");
  const [patToken, setPatToken] = useState("");
  const [targetName, setTargetName] = useState("");
  const [autoDeploy, setAutoDeploy] = useState(true);
  const [githubRepositories, setGithubRepositories] = useState<GithubRepositoryResponse[]>([]);
  const [selectedGithubRepositoryKey, setSelectedGithubRepositoryKey] = useState("");
  const [githubLoading, setGithubLoading] = useState(false);
  const [githubConnecting, setGithubConnecting] = useState(false);
  const selectedGithubRepository = githubRepositories.find(
    (repository) => `${repository.installationId}:${repository.id}` === selectedGithubRepositoryKey
  );
  const activeGithubRepository = repositorySource === "github"
    ? selectedGithubRepository
    : undefined;
  const repoUrl = activeGithubRepository?.cloneUrl ?? (
    repositorySource === "url" ? manualRepoUrl : ""
  );
  const branch = repositorySource === "github" ? githubBranch : manualBranch;

  // 공통 — VM 내 배포 경로 (심볼릭 링크)
  const [installPath, setInstallPath] = useState("");

  // 공통 — Docker 네트워크 (AI는 실제로 선택 반영, Compose는 참고용 목록만 노출)
  const [dockerNetworks, setDockerNetworks] = useState<NetworkInfo[]>([]);
  const [networkMode, setNetworkMode] = useState<NetworkMode>("create");
  const [existingNetworkName, setExistingNetworkName] = useState("");

  // Raw Compose
  const [composeContent, setComposeContent] = useState("");
  const [context, setContext] = useState("");
  const [composeDetection, setComposeDetection] = useState<ComposeDetectionResult | null>(null);
  const [selectedDetectedComposePath, setSelectedDetectedComposePath] = useState("");
  const [lastComposeDetectionKey, setLastComposeDetectionKey] = useState("");
  const [composeDetectionError, setComposeDetectionError] = useState<string | null>(null);
  const [detectingCompose, setDetectingCompose] = useState(false);
  const [composeReviewFindings, setComposeReviewFindings] = useState<ComposeReviewFinding[] | null>(null);
  const [reviewingCompose, setReviewingCompose] = useState(false);
  const [routerPlan, setRouterPlan] = useState<ComposeRouterPlanResult | null>(null);
  const [planningRouter, setPlanningRouter] = useState(false);
  const [routerApplied, setRouterApplied] = useState(false);
  const [routerHostPort, setRouterHostPort] = useState(18080);
  const [routerServicePorts, setRouterServicePorts] = useState<Record<string, number>>({});
  const [routerRouteOverrides, setRouterRouteOverrides] = useState<Record<string, ComposeRouterRouteOverride>>({});
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [envFiles, setEnvFiles] = useState<EnvironmentFile[]>([]);
  const [routes, setRoutes] = useState<ExposedRoute[]>([]);
  const [healthChecks, setHealthChecks] = useState<HealthCheck[]>([]);
  const [planType, setPlanType] = useState<string | null>(null);
  const [deploymentCustomSubdomain, setDeploymentCustomSubdomain] = useState("");
  const [deploymentSubdomainCheck, setDeploymentSubdomainCheck] = useState<SubdomainCheckStatus>("idle");
  const deploymentSubdomainTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // 도메인 모드 서비스별 서브도메인 가용성 — 여러 서비스가 각자 전용 도메인을 가질 수 있어 서비스명으로 구분한다.
  const [domainSubdomainCheck, setDomainSubdomainCheck] = useState<Record<string, SubdomainCheckStatus>>({});
  const domainSubdomainTimers = useRef<Record<string, ReturnType<typeof setTimeout>>>({});

  // AI 자동생성
  const [serviceCards, setServiceCards] = useState<ServiceCard[]>([emptyServiceCard()]);
  const [infraSelections, setInfraSelections] = useState<InfraSelection[]>([]);
  const [generatedSpec, setGeneratedSpec] = useState<string>("");
  const [renderedSpec, setRenderedSpec] = useState<ComposeSpecResponse | null>(null);
  const [generating, setGenerating] = useState(false);
  const [renderingSpec, setRenderingSpec] = useState(false);
  const [reviewFindings, setReviewFindings] = useState<ComposeReviewFinding[] | null>(null);
  const [reviewing, setReviewing] = useState(false);
  // 결정론적 저장소 분석 + 명시적 불확실성 상태 — status가 READY가 아니면 generatedSpec은 비어있고
  // unresolvedFields에 이유가 담김 (AI가 근거 없이 완전한 스펙을 지어내지 않았다는 뜻)
  const [generationStatus, setGenerationStatus] = useState<GenerationStatus | null>(null);
  const [unresolvedFields, setUnresolvedFields] = useState<UnresolvedField[]>([]);
  const [evidenceRefs, setEvidenceRefs] = useState<string[]>([]);
  const [generationWarnings, setGenerationWarnings] = useState<string[]>([]);

  const furthestCreateStep = furthestStepByTab[createTab];

  function visitCreateStep(step: CreateStep) {
    if (generating || submitting || reviewing || detectingCompose || reviewingCompose || planningRouter) return;
    if (step <= furthestCreateStep) setCreateStep(step);
  }

  function advanceCreateStep(step: CreateStep) {
    setCreateStep(step);
    setFurthestStepByTab((prev) => ({
      ...prev,
      [createTab]: Math.max(prev[createTab], step) as CreateStep,
    }));
  }

  function invalidateAiGeneration() {
    setGeneratedSpec("");
    setRenderedSpec(null);
    setReviewFindings(null);
    setGenerationStatus(null);
    setUnresolvedFields([]);
    setEvidenceRefs([]);
    setGenerationWarnings([]);
    setFurthestStepByTab((prev) => ({
      ...prev,
      ai: Math.min(prev.ai, 3) as CreateStep,
    }));
  }

  function invalidateComposeDetection() {
    setComposeDetection(null);
    setSelectedDetectedComposePath("");
    setLastComposeDetectionKey("");
    setComposeDetectionError(null);
    setComposeReviewFindings(null);
    setRouterPlan(null);
    setRouterApplied(false);
    setRouterServicePorts({});
  }

  function handleRepoUrlChange(value: string) {
    setManualRepoUrl(value);
    invalidateComposeDetection();
    invalidateAiGeneration();
  }

  function handleBranchChange(value: string) {
    if (repositorySource === "github") {
      setGithubBranch(value);
    } else {
      setManualBranch(value);
    }
    invalidateComposeDetection();
    invalidateAiGeneration();
  }

  function handleRepositorySourceChange(source: RepositorySource) {
    if (source === repositorySource) return;
    setRepositorySource(source);
    invalidateComposeDetection();
    invalidateAiGeneration();
  }

  function handlePatTokenChange(value: string) {
    setPatToken(value);
    invalidateComposeDetection();
    invalidateAiGeneration();
  }

  function handleRepositoryContextChange(value: string) {
    setContext(value);
    invalidateComposeDetection();
    invalidateAiGeneration();
  }

  function updateServiceCard(index: number, patch: Partial<ServiceCard>) {
    setServiceCards((prev) => prev.map((service, serviceIndex) => (
      serviceIndex === index ? { ...service, ...patch } : service
    )));
    invalidateAiGeneration();
  }

  function addServiceCard() {
    setServiceCards((prev) => [...prev, emptyServiceCard()]);
    invalidateAiGeneration();
  }

  function removeServiceCard(index: number) {
    setServiceCards((prev) => prev.filter((_, serviceIndex) => serviceIndex !== index));
    invalidateAiGeneration();
  }

  function addInfrastructure() {
    setInfraSelections((prev) => [...prev, emptyInfra()]);
    invalidateAiGeneration();
  }

  function updateInfrastructure(index: number, patch: Partial<InfraSelection>) {
    setInfraSelections((prev) => prev.map((infra, infraIndex) => (
      infraIndex === index ? { ...infra, ...patch } : infra
    )));
    invalidateAiGeneration();
  }

  function removeInfrastructure(index: number) {
    setInfraSelections((prev) => prev.filter((_, infraIndex) => infraIndex !== index));
    invalidateAiGeneration();
  }

  function handleNetworkModeChange(mode: NetworkMode) {
    setNetworkMode(mode);
    if (mode === "create") setExistingNetworkName("");
    invalidateAiGeneration();
  }

  function handleExistingNetworkChange(value: string) {
    setExistingNetworkName(value);
    invalidateAiGeneration();
  }

  // 마지막 렌더 결과의 라우터 계획에서 기본 진입점(루트) 서비스명을 찾는다. 게이트웨이 도메인(공용 CNAME)은
  // 이 루트 서비스의 expose.customSubdomain으로 전달된다(백엔드 렌더러가 그렇게 읽음).
  function aiRootServiceName(): string | null {
    return renderedSpec?.routerPlan?.routes.find((route) => route.root)?.serviceName ?? null;
  }

  function applyRoutingSettingsToSpec(
    spec: DeploymentSpec,
    overrides = routerRouteOverrides,
    gatewaySubdomain = deploymentCustomSubdomain,
    rootServiceName = aiRootServiceName()
  ): DeploymentSpec {
    return {
      ...spec,
      services: spec.services.map((service) => {
        if (!service.expose?.enabled || service.expose.protocol?.toLowerCase() !== "http") {
          return service;
        }
        // 루트/기본 진입점 서비스: 항상 PREFIX '/', 공용 게이트웨이 도메인(CNAME)을 사용.
        if (rootServiceName && service.name === rootServiceName) {
          return {
            ...service,
            expose: {
              ...service.expose,
              routeMode: "PREFIX",
              routePath: "/",
              stripPrefix: false,
              customSubdomain: gatewaySubdomain || null,
            },
          };
        }
        const override = overrides[service.name];
        if (!override) return service;
        if (override.mode === "DOMAIN") {
          return {
            ...service,
            expose: {
              ...service.expose,
              routeMode: "DOMAIN",
              customSubdomain: override.customSubdomain || null,
              routePath: null,
              stripPrefix: null,
            },
          };
        }
        return {
          ...service,
          expose: {
            ...service.expose,
            routeMode: "PREFIX",
            routePath: override.routePath ?? service.expose.routePath ?? null,
            stripPrefix: override.stripPrefix,
            customSubdomain: null,
          },
        };
      }),
    };
  }

  // 기본 진입점(게이트웨이) 공용 도메인 CNAME — 루트/Prefix 서비스가 공유한다.
  function handleDeploymentCustomSubdomainChange(value: string) {
    const lower = value.toLowerCase().replace(/[^a-z0-9-]/g, "").slice(0, 30);
    setDeploymentCustomSubdomain(lower);
    setDeploymentSubdomainCheck("idle");
    const gatewayName = routerPlan?.routerServiceName;
    setRoutes((previous) => previous.map((route) => {
      if (route.protocol !== "HTTP" || route.visibility !== "PUBLIC") return route;
      // 게이트웨이 라우트가 있으면 그 라우트에만, 없으면(단일 서비스) 공개 HTTP 라우트에 적용.
      if (gatewayName && route.serviceName !== gatewayName) return route;
      return { ...route, customSubdomain: lower };
    }));
    setGeneratedSpec((previous) => {
      if (!previous) return previous;
      try {
        return JSON.stringify(
          applyRoutingSettingsToSpec(JSON.parse(previous) as DeploymentSpec, routerRouteOverrides, lower),
          null,
          2
        );
      } catch {
        return previous;
      }
    });
    if (deploymentSubdomainTimer.current) clearTimeout(deploymentSubdomainTimer.current);
    if (!lower || !accessToken) return;
    setDeploymentSubdomainCheck("checking");
    deploymentSubdomainTimer.current = setTimeout(async () => {
      try {
        const result = await api.vm.checkSubdomain(accessToken, vmId, lower);
        setDeploymentSubdomainCheck(
          result.available
            ? "available"
            : ((result.reason as "taken" | "reserved" | "pro-only") ?? "taken")
        );
      } catch {
        setDeploymentSubdomainCheck("taken");
      }
    }, 500);
  }

  // 도메인 모드 서비스의 전용 서브도메인 입력 — override에 반영하고 가용성을 서비스별로 확인한다.
  function handleDomainSubdomainChange(service: string, value: string) {
    const lower = value.toLowerCase().replace(/[^a-z0-9-]/g, "").slice(0, 30);
    setRouterRouteOverrides((previous) => ({
      ...previous,
      [service]: { mode: "DOMAIN", routePath: null, stripPrefix: false, customSubdomain: lower },
    }));
    setRouterApplied(false);
    setDomainSubdomainCheck((previous) => ({ ...previous, [service]: "idle" }));
    if (domainSubdomainTimers.current[service]) clearTimeout(domainSubdomainTimers.current[service]);
    if (!lower || !accessToken) return;
    setDomainSubdomainCheck((previous) => ({ ...previous, [service]: "checking" }));
    domainSubdomainTimers.current[service] = setTimeout(async () => {
      try {
        const result = await api.vm.checkSubdomain(accessToken, vmId, lower);
        setDomainSubdomainCheck((previous) => ({
          ...previous,
          [service]: result.available
            ? "available"
            : ((result.reason as "taken" | "reserved" | "pro-only") ?? "taken"),
        }));
      } catch {
        setDomainSubdomainCheck((previous) => ({ ...previous, [service]: "taken" }));
      }
    }, 500);
  }

  function handleServiceExposureChange(index: number, expose: boolean) {
    setServiceCards((prev) => prev.map((x, xi) => (
      xi === index ? { ...x, expose } : x
    )));
    invalidateAiGeneration();
  }

  useEffect(() => () => {
    if (deploymentSubdomainTimer.current) clearTimeout(deploymentSubdomainTimer.current);
    Object.values(domainSubdomainTimers.current).forEach(clearTimeout);
  }, []);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setError(null);
    try {
      const [deploymentResult, targetResult, portResult] = await Promise.all([
        api.ops.deployments.list(accessToken, vmId),
        api.ops.deployments.listTargets(accessToken, vmId),
        api.vm.getPorts(accessToken, vmId).catch(() => []),
      ]);
      setDeployments(deploymentResult);
      setDeploymentTargets(targetResult);
      setDeploymentPorts(portResult);
    } catch (err) {
      setError(err instanceof Error ? err.message : "배포 이력 조회에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  }, [accessToken, vmId]);

  useEffect(() => {
    const timer = window.setTimeout(load, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  // 배포 생성 모달을 열 때 VM에 이미 존재하는 Docker 네트워크 목록을 가져옴 — Compose 탭에서는 참고용으로
  // 그대로 보여주고, AI 탭에서는 "기존 네트워크 재사용" 선택지의 후보로 사용
  useEffect(() => {
    if (!showCreate || !accessToken) return;
    api.ops.docker.listNetworks(accessToken, vmId).then(setDockerNetworks).catch(() => setDockerNetworks([]));
  }, [showCreate, accessToken, vmId]);

  const loadGithubRepositories = useCallback(async () => {
    if (!accessToken) return;
    try {
      setGithubRepositories(await api.ops.github.listRepositories(accessToken));
    } catch {
      setGithubRepositories([]);
    } finally {
      setGithubLoading(false);
    }
  }, [accessToken]);

  useEffect(() => {
    if (!showCreate || !accessToken) return;
    const timer = window.setTimeout(loadGithubRepositories, 0);
    return () => window.clearTimeout(timer);
  }, [showCreate, accessToken, loadGithubRepositories]);

  useEffect(() => {
    function refreshAfterGithubConnection(connectedVmId: unknown) {
      if (connectedVmId !== vmId) return;
      setGithubConnecting(false);
      setGithubLoading(true);
      loadGithubRepositories();
    }

    function handleGithubConnected(event: MessageEvent) {
      if (event.origin !== window.location.origin
          || event.data?.type !== "gamjabox:github-connected") {
        return;
      }
      refreshAfterGithubConnection(event.data.vmId);
    }

    function handleGithubStorage(event: StorageEvent) {
      if (event.key !== "gamjabox:github-connected" || !event.newValue) return;
      try {
        refreshAfterGithubConnection(JSON.parse(event.newValue).vmId);
      } catch {
        // 다른 탭의 손상된 알림은 무시하고 현재 입력 상태를 유지한다.
      }
    }

    window.addEventListener("message", handleGithubConnected);
    window.addEventListener("storage", handleGithubStorage);
    return () => {
      window.removeEventListener("message", handleGithubConnected);
      window.removeEventListener("storage", handleGithubStorage);
    };
  }, [loadGithubRepositories, vmId]);

  // 라우트 편집 UI의 PRO 커스텀 CNAME 게이팅에 필요 — 기존 포트 추가 폼과 동일하게 플랜을 조회해둠
  useEffect(() => {
    if (!showCreate || !accessToken) return;
    api.user.profile(accessToken).then((p) => setPlanType(p.planType)).catch(() => {});
  }, [showCreate, accessToken]);

  // SEC-010: 배포 상세 페이지의 "재시도" 버튼에서 넘어올 때 복호화된 스펙(시크릿 포함 가능)을
  // sessionStorage에 담지 않고 deploymentId만 전달받아, 여기서 handleRetry로 직접 새로 조회한다.
  useEffect(() => {
    if (!accessToken) return;
    const deploymentId = sessionStorage.getItem(retryStorageKey(vmId));
    if (!deploymentId) return;
    sessionStorage.removeItem(retryStorageKey(vmId));
    handleRetry(deploymentId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [vmId, accessToken]);

  function applyComposeSpec(spec: ComposeSpecResponse) {
    setComposeContent(spec.composeContent);
    setContext(spec.context ?? "");
    invalidateComposeDetection();
    setInstallPath(spec.installPath ?? "");
    setRepositorySource("url");
    setSelectedGithubRepositoryKey("");
    setAutoDeploy(false);
    setEnvFiles(spec.environmentFiles);
    setRoutes(spec.exposedRoutes);
    setHealthChecks(spec.healthChecks);
    setShowAdvanced(spec.environmentFiles.length > 0 || spec.exposedRoutes.length > 0 || spec.healthChecks.length > 0);
    setCreateTab("compose");
    setCreateStep(2);
    setFurthestStepByTab((prev) => ({ ...prev, compose: 2 }));
    setRetryNotice(true);
    setShowCreate(true);
  }

  async function handleRetry(deploymentId: string) {
    if (!accessToken) return;
    setRetryingId(deploymentId);
    setError(null);
    try {
      const deployment = deployments.find((item) => item.id === deploymentId)
        ?? await api.ops.deployments.get(accessToken, vmId, deploymentId);
      if (deployment?.deploymentTargetId) {
        const retried = await api.ops.deployments.redeployTarget(
          accessToken, vmId, deployment.deploymentTargetId
        );
        router.push(`/instances/${vmId}/deployments/${retried.id}`);
        return;
      }
      const spec = await api.ops.deployments.getComposeSpec(accessToken, vmId, deploymentId);
      applyComposeSpec(spec);
    } catch (err) {
      setError(err instanceof Error ? err.message : "이전 배포 스펙을 불러오지 못했습니다.");
    } finally {
      setRetryingId(null);
    }
  }

  async function handleRollback(deploymentId: string) {
    if (!accessToken) return;
    if (!confirm("이 배포로 롤백하시겠습니까? 재빌드 없이 이 시점의 이미지로 컨테이너만 재기동합니다.")) return;
    setRollingBackId(deploymentId);
    setError(null);
    try {
      const rollback = await api.ops.deployments.rollback(accessToken, vmId, deploymentId);
      router.push(`/instances/${vmId}/deployments/${rollback.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "롤백 요청에 실패했습니다.");
      setRollingBackId(null);
    }
  }

  function resetCreateForm() {
    setRepositorySource("github");
    setManualRepoUrl("");
    setManualBranch("main");
    setGithubBranch("main");
    setPatToken("");
    setTargetName("");
    setAutoDeploy(true);
    setSelectedGithubRepositoryKey("");
    setComposeContent("");
    setContext("");
    setComposeDetection(null);
    setSelectedDetectedComposePath("");
    setLastComposeDetectionKey("");
    setComposeDetectionError(null);
    setDetectingCompose(false);
    setComposeReviewFindings(null);
    setReviewingCompose(false);
    setRouterPlan(null);
    setPlanningRouter(false);
    setRouterApplied(false);
    setRouterHostPort(18080);
    setRouterServicePorts({});
    setRouterRouteOverrides({});
    setInstallPath("");
    setNetworkMode("create");
    setExistingNetworkName("");
    setShowAdvanced(false);
    setEnvFiles([]);
    setRoutes([]);
    setDeploymentCustomSubdomain("");
    setDeploymentSubdomainCheck("idle");
    setDomainSubdomainCheck({});
    setHealthChecks([]);
    setServiceCards([emptyServiceCard()]);
    setInfraSelections([]);
    setGeneratedSpec("");
    setRenderedSpec(null);
    setRenderingSpec(false);
    setReviewFindings(null);
    setGenerationStatus(null);
    setUnresolvedFields([]);
    setEvidenceRefs([]);
    setGenerationWarnings([]);
    setRetryNotice(false);
    setCreateStep(1);
    setFurthestStepByTab({ compose: 1, ai: 1 });
  }

  function closeCreate() {
    if (submitting || generating || reviewing || detectingCompose || reviewingCompose || planningRouter) return;
    setShowCreate(false);
    setError(null);
    resetCreateForm();
  }

  function openCreate() {
    setError(null);
    setGithubLoading(true);
    setShowCreate(true);
  }

  async function handleConnectGithub() {
    if (!accessToken) return;
    const popup = window.open(
      "about:blank",
      "gamjabox-github-connect",
      "popup=yes,width=760,height=820,resizable=yes,scrollbars=yes"
    );
    if (!popup) {
      setError("팝업이 차단되어 GitHub 연결을 열 수 없습니다. 이 사이트의 팝업을 허용한 뒤 다시 시도해주세요.");
      return;
    }
    const popupWatcher = window.setInterval(() => {
      if (!popup.closed) return;
      window.clearInterval(popupWatcher);
      setGithubConnecting(false);
    }, 500);
    setGithubConnecting(true);
    setError(null);
    try {
      const result = await api.ops.github.createInstallUrl(accessToken, vmId);
      popup.location.replace(result.url);
    } catch (err) {
      window.clearInterval(popupWatcher);
      popup.close();
      setError(err instanceof Error ? err.message : "GitHub 연결을 시작하지 못했습니다.");
      setGithubConnecting(false);
    }
  }

  function handleGithubRepositoryChange(key: string) {
    setSelectedGithubRepositoryKey(key);
    invalidateComposeDetection();
    const repository = githubRepositories.find((item) => `${item.installationId}:${item.id}` === key);
    if (!repository) {
      setGithubBranch("main");
      setAutoDeploy(false);
      invalidateAiGeneration();
      return;
    }
    setGithubBranch(repository.defaultBranch || "main");
    setAutoDeploy(repositorySource === "github");
    if (!targetName.trim()) {
      setTargetName(repository.fullName.split("/").pop() ?? "");
    }
    invalidateAiGeneration();
  }

  async function handleAutoDeployToggle(target: DeploymentTargetResponse) {
    if (!accessToken) return;
    setTogglingTargetId(target.id);
    setError(null);
    try {
      const updated = await api.ops.deployments.setAutoDeploy(
        accessToken,
        vmId,
        target.id,
        !target.autoDeployEnabled
      );
      setDeploymentTargets((prev) => prev.map((item) => item.id === updated.id ? updated : item));
    } catch (err) {
      setError(err instanceof Error ? err.message : "자동 배포 설정을 변경하지 못했습니다.");
    } finally {
      setTogglingTargetId(null);
    }
  }

  function openDeleteTargetModal(target: DeploymentTargetResponse) {
    setDeleteTargetError(null);
    setTargetPendingDeletion(target);
  }

  function closeDeleteTargetModal() {
    if (deletingTargetId) return;
    setDeleteTargetError(null);
    setTargetPendingDeletion(null);
  }

  async function handleDeleteTarget() {
    if (!accessToken || !targetPendingDeletion) return;
    const target = targetPendingDeletion;
    setDeletingTargetId(target.id);
    setDeleteTargetError(null);
    setError(null);
    try {
      await api.ops.deployments.deleteTarget(accessToken, vmId, target.id);
      setTargetPendingDeletion(null);
      await load();
    } catch (err) {
      setDeleteTargetError(err instanceof Error ? err.message : "배포 대상 삭제에 실패했습니다.");
    } finally {
      setDeletingTargetId(null);
    }
  }

  function nextAvailableRouterPort(): number {
    const occupied = new Set(deploymentPorts.map((port) => port.port));
    for (let port = 18080; port <= 18999; port += 1) {
      if (!occupied.has(port)) return port;
    }
    return 19080;
  }

  function routerNickname(): string {
    const normalized = (targetName || "gateway")
      .toLowerCase()
      .replace(/[^a-z0-9-]/g, "-")
      .replace(/^-+|-+$/g, "")
      .slice(0, 20)
      .replace(/-+$/g, "");
    return normalized || "gateway";
  }

  function applyRouterPlan(plan: ComposeRouterPlanResult) {
    if (plan.status !== "ADDED" || !plan.routerHostPort) return;
    setComposeContent(plan.enhancedComposeContent);
    setRouterPlan(plan);
    setRouterApplied(true);
    setRouterHostPort(plan.routerHostPort);
    setComposeReviewFindings(null);

    const routedServices = new Set(plan.routes.map((route) => route.serviceName));
    const domainRoutes = plan.routes.filter((route) => route.mode === "DOMAIN");
    setRoutes((previous) => {
      const replacedRoutes = previous.filter((route) => routedServices.has(route.serviceName));
      const preservedRoutes = previous.filter(
        (route) => !routedServices.has(route.serviceName) && route.serviceName !== plan.routerServiceName
      );
      const inheritedCustomSubdomain = replacedRoutes
        .map((route) => route.customSubdomain)
        .find((value) => Boolean(value));
      const usedNicknames = new Set(preservedRoutes.map((route) => route.nickname));
      let gatewayNickname = routerNickname();
      if (usedNicknames.has(gatewayNickname)) gatewayNickname = "gateway";
      while (usedNicknames.has(gatewayNickname)) gatewayNickname = `${gatewayNickname}-2`;
      usedNicknames.add(gatewayNickname);
      // 도메인 모드 서비스는 각자 전용 서브도메인으로 같은 router 포트를 통해 노출된다.
      const domainExposedRoutes: ExposedRoute[] = domainRoutes.map((route) => ({
        serviceName: route.serviceName,
        port: plan.routerHostPort!,
        protocol: "HTTP",
        visibility: "PUBLIC",
        nickname: uniqueNicknameFor(route.serviceName, usedNicknames),
        customSubdomain: route.customSubdomain ?? "",
      }));
      return [
        {
          serviceName: plan.routerServiceName,
          port: plan.routerHostPort!,
          protocol: "HTTP",
          visibility: "PUBLIC",
          nickname: gatewayNickname,
          customSubdomain: deploymentCustomSubdomain || inheritedCustomSubdomain || "",
        },
        ...domainExposedRoutes,
        ...preservedRoutes,
      ];
    });
    const routeByService = new Map(plan.routes.map((route) => [route.serviceName, route]));
    setHealthChecks((previous) => previous.map((check) => {
      const route = routeByService.get(check.serviceName);
      if (!route) return check;
      const originalPath = check.path?.startsWith("/") ? check.path : `/${check.path || ""}`;
      // 루트/도메인 라우트는 자기 도메인 루트에서 그대로 서비스되므로 경로를 접두하지 않는다.
      return {
        serviceName: plan.routerServiceName,
        path: (route.root || route.mode === "DOMAIN") ? originalPath : `${route.routePath}${originalPath}`,
        hostPort: plan.routerHostPort!,
        containerPort: undefined,
      };
    }));
    setShowAdvanced(true);
  }

  // ExposedRoute.nickname 제약(소문자/숫자/하이픈, 최대 20자) + 이번 배포 내 유일성을 만족하는 닉네임 생성.
  function uniqueNicknameFor(base: string, used: Set<string>): string {
    let slug = base.toLowerCase().replace(/[^a-z0-9-]/g, "-").replace(/-{2,}/g, "-").replace(/^-+|-+$/g, "");
    if (!slug) slug = "svc";
    if (slug.length > 20) slug = slug.slice(0, 20).replace(/-+$/g, "");
    let candidate = slug;
    let suffix = 2;
    while (used.has(candidate)) {
      const suffixPart = `-${suffix}`;
      const trimmed = slug.length + suffixPart.length > 20
        ? slug.slice(0, 20 - suffixPart.length).replace(/-+$/g, "")
        : slug;
      candidate = `${trimmed}${suffixPart}`;
      suffix += 1;
    }
    used.add(candidate);
    return candidate;
  }

  async function handlePlanComposeRouter(
    content: string,
    autoApply: boolean,
    forcePort?: number
  ): Promise<ComposeRouterPlanResult | null> {
    if (!accessToken || !content.trim()) return null;
    const occupied = new Set(deploymentPorts.map((port) => port.port));
    const requestedPort = forcePort
      ?? (routerHostPort >= 1 && routerHostPort <= 65535 && !occupied.has(routerHostPort)
        ? routerHostPort
        : nextAvailableRouterPort());
    setRouterHostPort(requestedPort);
    setPlanningRouter(true);
    setError(null);
    try {
      const planned = await api.ops.deployments.planComposeRouter(accessToken, vmId, {
        composeContent: content,
        routerHostPort: requestedPort,
        servicePorts: Object.fromEntries(
          Object.entries(routerServicePorts).filter(([, port]) => port >= 1 && port <= 65535)
        ),
        routeOverrides: Object.keys(routerRouteOverrides).length > 0 ? routerRouteOverrides : undefined,
      });
      let plan = planned;
      if (planned.status === "ADDED") {
        const routedServices = new Set(planned.routes.map((route) => route.serviceName));
        const distinctCustomSubdomains = Array.from(new Set(
          routes
            .filter((route) => routedServices.has(route.serviceName))
            .map((route) => route.customSubdomain?.trim())
            .filter((value): value is string => Boolean(value))
        ));
        if (distinctCustomSubdomains.length > 1) {
          plan = {
            ...planned,
            status: "NEEDS_INPUT",
            enhancedComposeContent: content,
            unresolvedServices: [{
              serviceName: "공개 HTTP 라우트",
              reason: "서로 다른 커스텀 서브도메인은 하나의 Caddy 진입점으로 합칠 수 없습니다. 하나의 도메인으로 통일하거나 기존 개별 노출을 유지하세요.",
              portRequired: false,
            }],
            warnings: [
              ...planned.warnings,
              "커스텀 서브도메인 설정을 보호하기 위해 Compose를 자동 변경하지 않았습니다.",
            ],
          };
        }
      }
      setRouterPlan(plan);
      setRouterApplied(false);
      if (autoApply && plan.status === "ADDED") {
        applyRouterPlan(plan);
      }
      return plan;
    } catch (err) {
      setRouterPlan(null);
      setRouterApplied(false);
      setError(err instanceof Error ? err.message : "Compose 라우터 구성을 분석하지 못했습니다.");
      return null;
    } finally {
      setPlanningRouter(false);
    }
  }

  async function applyDetectedCompose(file: DetectedComposeFile) {
    let content = file.content;
    const plan = await handlePlanComposeRouter(file.content, false);
    if (plan?.status === "ADDED") {
      applyRouterPlan(plan);
      content = plan.enhancedComposeContent;
    } else {
      setComposeContent(content);
    }
    setContext(file.directory === "." ? "" : file.directory);
    setSelectedDetectedComposePath(file.path);
    setCreateTab("compose");
    setCreateStep(3);
    setFurthestStepByTab((prev) => ({
      ...prev,
      compose: Math.max(prev.compose, 3) as CreateStep,
    }));
    invalidateAiGeneration();
  }

  async function handleDetectCompose(force = false): Promise<ComposeDetectionResult | null> {
    if (!accessToken || !repoUrl || !branch) return null;
    const detectionKey = JSON.stringify([
      repoUrl,
      branch,
      context.trim() || ".",
      activeGithubRepository?.installationId ?? null,
      activeGithubRepository?.id ?? null,
    ]);
    if (!force && detectionKey === lastComposeDetectionKey && composeDetection) {
      return composeDetection;
    }

    setDetectingCompose(true);
    setComposeDetectionError(null);
    setComposeReviewFindings(null);
    try {
      const result = await api.ops.deployments.detectCompose(accessToken, vmId, {
        repoUrl,
        branch,
        patToken: repositorySource === "url" ? patToken || undefined : undefined,
        context: context.trim() || undefined,
        githubInstallationId: activeGithubRepository?.installationId,
        githubRepositoryId: activeGithubRepository?.id,
      });
      setComposeDetection(result);
      setLastComposeDetectionKey(detectionKey);
      const primary = result.files.find((file) => file.primary) ?? result.files[0];
      setSelectedDetectedComposePath(primary?.path ?? "");
      const discoveredServices = result.discoveredServices ?? [];
      if (!result.detected && createTab === "ai" && discoveredServices.length > 0) {
        setServiceCards((previous) => mergeDiscoveredServiceCards(previous, discoveredServices));
        invalidateAiGeneration();
      }
      if (primary && createTab === "compose" && !composeContent.trim()) {
        setComposeContent(primary.content);
        setContext(primary.directory === "." ? "" : primary.directory);
        await handlePlanComposeRouter(primary.content, true);
      }
      return result;
    } catch (err) {
      setComposeDetection(null);
      setSelectedDetectedComposePath("");
      setComposeDetectionError(
        err instanceof Error ? err.message : "저장소의 Compose 파일을 확인하지 못했습니다."
      );
      return null;
    } finally {
      setDetectingCompose(false);
    }
  }

  async function handleReviewCompose(content: string) {
    if (!accessToken || !content.trim()) return;
    setReviewingCompose(true);
    setComposeReviewFindings(null);
    setError(null);
    try {
      setComposeReviewFindings(await api.ops.deployments.reviewCompose(accessToken, vmId, content));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Compose AI 검수에 실패했습니다.");
    } finally {
      setReviewingCompose(false);
    }
  }

  async function handleCreateFromCompose() {
    if (!accessToken || !repoUrl || !branch || !composeContent) return;
    if (!routingSubdomainsReady) return;
    setSubmitting(true);
    setError(null);
    try {
      const deployment = await api.ops.deployments.create(accessToken, vmId, {
        repoUrl,
        branch,
        patToken: repositorySource === "url" ? patToken || undefined : undefined,
        targetName: targetName.trim() || undefined,
        autoDeploy: Boolean(activeGithubRepository && autoDeploy),
        githubInstallationId: activeGithubRepository?.installationId,
        githubRepositoryId: activeGithubRepository?.id,
        composeContent,
        context: context.trim() || undefined,
        installPath: installPath.trim() || undefined,
        environmentFiles: envFiles.filter((f) => f.vmPath && f.content),
        exposedRoutes: routes.filter((r) => r.serviceName && r.nickname),
        healthChecks: healthChecks.filter((h) => h.serviceName && h.path),
      });
      setShowCreate(false);
      resetCreateForm();
      router.push(`/instances/${vmId}/deployments/${deployment.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "배포 생성에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleGenerateSpec() {
    if (!accessToken || !repoUrl || !branch) return false;
    setGenerating(true);
    setError(null);
    setReviewFindings(null);
    setGenerationStatus(null);
    setUnresolvedFields([]);
    setEvidenceRefs([]);
    setGenerationWarnings([]);
    try {
      const result = await api.ops.deployments.generateSpec(accessToken, vmId, {
        repoUrl,
        branch,
        patToken: repositorySource === "url" ? patToken || undefined : undefined,
        githubInstallationId: activeGithubRepository?.installationId,
        githubRepositoryId: activeGithubRepository?.id,
        services: serviceCards
          .filter((s) => s.name && s.runtime && s.context)
          .map((service) => ({
            ...service,
            context: resolveServiceContext(
              context,
              service.context,
              composeDetection?.discoveredServices
            ),
          })),
        infrastructure: infraSelections.filter((i) => i.type),
        existingNetworkName: networkMode === "reuse" ? existingNetworkName || undefined : undefined,
      });
      setGenerationStatus(result.status);
      setEvidenceRefs(result.evidenceRefs);
      setGenerationWarnings(result.warnings);
      setUnresolvedFields(result.unresolved);
      setGeneratedSpec(result.status === "READY" && result.spec ? JSON.stringify(result.spec, null, 2) : "");
      if (result.status === "READY" && result.spec) {
        setRenderedSpec(await api.ops.deployments.renderSpec(accessToken, vmId, result.spec));
      } else {
        setRenderedSpec(null);
      }
      advanceCreateStep(4);
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 스펙 생성에 실패했습니다.");
      return false;
    } finally {
      setGenerating(false);
    }
  }

  async function handleReviewSpec() {
    if (!accessToken || !generatedSpec) return;
    setReviewing(true);
    setError(null);
    try {
      const spec: DeploymentSpec = JSON.parse(generatedSpec);
      const findings = await api.ops.deployments.reviewSpec(accessToken, vmId, spec);
      setReviewFindings(findings);
    } catch (err) {
      setError(err instanceof Error ? err.message : "스펙 JSON이 올바르지 않거나 검수에 실패했습니다.");
    } finally {
      setReviewing(false);
    }
  }

  async function handleRenderSpec() {
    if (!accessToken || !generatedSpec) return;
    setRenderingSpec(true);
    setError(null);
    try {
      const spec: DeploymentSpec = JSON.parse(generatedSpec);
      setRenderedSpec(await api.ops.deployments.renderSpec(accessToken, vmId, spec));
    } catch (err) {
      setRenderedSpec(null);
      setError(err instanceof Error ? err.message : "최종 Compose를 생성하지 못했습니다.");
    } finally {
      setRenderingSpec(false);
    }
  }

  async function handleCreateFromSpec() {
    if (!accessToken || !repoUrl || !branch || !generatedSpec) return;
    if (!routingSubdomainsReady) return;
    setSubmitting(true);
    setError(null);
    try {
      // 분석 결과 단계에서 지정한 공개 진입점 CNAME과 서비스별 Prefix 보정을 배포 직전에 스펙에 반영한다.
      const spec: DeploymentSpec = applyRoutingSettingsToSpec(JSON.parse(generatedSpec));
      const deployment = await api.ops.deployments.createFromSpec(accessToken, vmId, {
        repoUrl,
        branch,
        patToken: repositorySource === "url" ? patToken || undefined : undefined,
        spec,
        installPath: installPath.trim() || undefined,
        targetName: targetName.trim() || undefined,
        autoDeploy: Boolean(activeGithubRepository && autoDeploy),
        githubInstallationId: activeGithubRepository?.installationId,
        githubRepositoryId: activeGithubRepository?.id,
      });
      setShowCreate(false);
      resetCreateForm();
      router.push(`/instances/${vmId}/deployments/${deployment.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "스펙 JSON이 올바르지 않거나 배포 생성에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  const repositoryStepReady = Boolean(
    repoUrl.trim() && branch.trim() && (retryNotice || targetName.trim())
  );
  const composeStepReady = Boolean(composeContent.trim());
  const aiHintsStepReady = (
    Boolean(composeDetection?.discoveredServices?.length)
    || serviceCards.some((service) => (
      service.name.trim() && service.runtime.trim() && service.context.trim()
    ))
  ) && (networkMode === "create" || Boolean(existingNetworkName));
  // 공개 진입점 CNAME은 분석 결과 단계(step 4)에서 단일 값으로 지정한다. 값이 없으면 자동 주소를 쓰므로
  // 통과, 값이 있으면 가용성 확인이 끝나야 배포를 진행한다.
  const deploymentSubdomainReady = !deploymentCustomSubdomain || deploymentSubdomainCheck === "available";
  // 도메인 모드로 지정한 서비스는 전용 서브도메인이 채워지고 가용성 확인이 끝나야 배포할 수 있다.
  const domainSubdomainsReady = Object.entries(routerRouteOverrides).every(([service, override]) => (
    override.mode !== "DOMAIN"
    || (Boolean(override.customSubdomain) && domainSubdomainCheck[service] === "available")
  ));
  const routingSubdomainsReady = deploymentSubdomainReady && domainSubdomainsReady;

  // 최종 공개 진입점 도출 — 단일 서비스면 그 서비스 포트, 다중 서비스면 Caddy 진입점 하나가 CNAME 대상이다.
  // compose 흐름은 라우터 적용 후 routes에 남는 공개 HTTP 라우트를, AI 흐름은 렌더 결과의 routerPlan을 근거로 한다.
  const composePublicEndpoint: { endpoint: ExposedRoute; throughCaddy: boolean } | null = (() => {
    // 다중 서비스 라우터가 필요한데 아직 Compose에 적용하지 않았다면 진입점이 확정되지 않았으므로 숨긴다.
    if (routerPlan && (routerPlan.status === "ADDED" || routerPlan.status === "NEEDS_INPUT") && !routerApplied) {
      return null;
    }
    const publicHttp = routes.filter((route) => route.visibility === "PUBLIC" && route.protocol === "HTTP");
    if (publicHttp.length === 0) return null;
    const routerName = routerPlan?.routerServiceName;
    const throughCaddy = Boolean(routerApplied && routerName && publicHttp.some((route) => route.serviceName === routerName));
    const endpoint = throughCaddy
      ? publicHttp.find((route) => route.serviceName === routerName)!
      : publicHttp[0];
    return { endpoint, throughCaddy };
  })();

  const aiRouterPlan = renderedSpec?.routerPlan ?? null;
  const aiThroughCaddy = Boolean(
    aiRouterPlan
    && (aiRouterPlan.status === "ADDED" || aiRouterPlan.status === "ALREADY_CONFIGURED")
    && aiRouterPlan.routerHostPort
  );
  const aiPublicEndpoint: { endpoint: ExposedRoute; throughCaddy: boolean } | null = (() => {
    if (!renderedSpec) return null;
    if (aiThroughCaddy && aiRouterPlan?.routerHostPort) {
      return {
        endpoint: {
          serviceName: aiRouterPlan.routerServiceName,
          port: aiRouterPlan.routerHostPort,
          protocol: "HTTP",
          visibility: "PUBLIC",
          nickname: "",
        },
        throughCaddy: true,
      };
    }
    const publicRoute = renderedSpec.exposedRoutes.find((route) => route.protocol?.toUpperCase() === "HTTP")
      ?? renderedSpec.exposedRoutes[0];
    return publicRoute ? { endpoint: publicRoute, throughCaddy: false } : null;
  })();

  // AI 흐름에서 서비스별 Prefix를 보정한 뒤 최종 Compose를 다시 렌더링한다 — 보정값을 스펙에 반영하고 재렌더.
  async function handleRegenerateAiRouting() {
    if (!accessToken || !generatedSpec) return;
    setRenderingSpec(true);
    setError(null);
    try {
      const spec = applyRoutingSettingsToSpec(JSON.parse(generatedSpec) as DeploymentSpec);
      setGeneratedSpec(JSON.stringify(spec, null, 2));
      setRenderedSpec(await api.ops.deployments.renderSpec(accessToken, vmId, spec));
    } catch (err) {
      setError(err instanceof Error ? err.message : "라우팅을 다시 생성하지 못했습니다.");
    } finally {
      setRenderingSpec(false);
    }
  }

  async function handleNextCreateStep() {
    if (createStep === 1) {
      advanceCreateStep(2);
      return;
    }
    if (createStep === 2 && repositoryStepReady) {
      const detection = await handleDetectCompose();
      if (createTab === "ai" && detection?.detected) {
        const primary = detection.files.find((file) => file.primary) ?? detection.files[0];
        if (primary) {
          await applyDetectedCompose(primary);
          return;
        }
      }
      advanceCreateStep(3);
      return;
    }
    if (createStep === 3 && createTab === "compose" && composeStepReady) {
      let effectivePlan = routerPlan;
      if (!effectivePlan) {
        effectivePlan = await handlePlanComposeRouter(composeContent, true);
      } else if (effectivePlan.status === "ADDED" && !routerApplied) {
        applyRouterPlan(effectivePlan);
      }
      if (!effectivePlan || effectivePlan.status === "NEEDS_INPUT") {
        return;
      }
      advanceCreateStep(4);
      return;
    }
    if (createStep === 3 && createTab === "ai" && aiHintsStepReady) {
      if (generationStatus) {
        advanceCreateStep(4);
      } else {
        await handleGenerateSpec();
      }
    }
  }

  function handlePreviousCreateStep() {
    setCreateStep((step) => Math.max(1, step - 1) as CreateStep);
  }

  if (!accessToken) return <PageLoader />;

  return (
    <div className="flex flex-col h-[calc(100vh-170px)]">
      <InstanceSectionNav vmId={vmId} />
      <div className="mb-3 flex items-center rounded-panel border border-line bg-panel">
        <div className="flex h-10 shrink-0 items-center gap-2.5 pl-4 pr-3.5">
          <button onClick={() => router.back()} className="flex h-7 w-7 items-center justify-center rounded-md text-muted-soft transition-colors hover:bg-white/[0.06] hover:text-muted" aria-label="뒤로가기">
            <svg className="w-[15px] h-[15px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="text-[15px] font-bold whitespace-nowrap">배포</h1>
        </div>
        <div className="ml-auto flex h-10 shrink-0 items-center">
          <button onClick={openCreate} className="flex h-10 shrink-0 items-center gap-1.5 whitespace-nowrap px-3.5 text-sm font-bold text-brand-strong transition-colors hover:bg-white/[0.06]">
            ＋ 새 배포
          </button>
          <div className="h-5 w-px shrink-0 bg-line" />
          <button onClick={load} title="새로고침" className="flex h-10 w-10 shrink-0 items-center justify-center text-muted transition-colors hover:bg-white/[0.06] rounded-r-panel">
            <svg className="w-[15px] h-[15px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
        </div>
      </div>

      {error && !showCreate && (
        <div className="bg-danger/10 border border-danger-soft text-danger px-4 py-3 rounded-md mb-3 text-sm flex items-center justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-danger/60 hover:text-danger">✕</button>
        </div>
      )}

      <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-auto">
        {loading ? (
          <div className="flex-1 rounded-panel border border-line">
            <PageLoader label="불러오는 중" />
          </div>
        ) : (
          <>
            {deploymentTargets.length > 0 && (
              <section className="rounded-panel border border-line bg-panel p-4">
                <div className="mb-3">
                  <h2 className="text-sm font-extrabold">배포 대상</h2>
                  <p className="mt-0.5 text-xs text-muted">
                    VM 하나에서 앱별 컨테이너·릴리스·라우트를 분리해 운영합니다.
                  </p>
                </div>
                <div className="grid gap-2 lg:grid-cols-2">
                  {deploymentTargets.map((target) => {
                    const publicPorts = deploymentPorts.filter(
                      (port) => port.deploymentAppId === target.id && port.visibility === "PUBLIC"
                    );
                    return (
                    <article key={target.id} className="rounded-[10px] border border-line-strong bg-white/[0.025] p-3">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <h3 className="truncate text-sm font-bold">{target.name}</h3>
                            <span className="rounded bg-white/[0.06] px-1.5 py-0.5 text-[10px] font-bold text-muted">
                              {SOURCE_TYPE_LABEL[target.sourceType] ?? target.sourceType}
                            </span>
                          </div>
                          <p className="mt-1 truncate font-mono text-[11px] text-muted-soft">
                            {target.repositoryFullName ?? target.repositoryUrl} · {target.branch}
                          </p>
                        </div>
                        <div className="flex shrink-0 items-center gap-1.5">
                          <button
                            type="button"
                            onClick={() => handleAutoDeployToggle(target)}
                            disabled={togglingTargetId === target.id || !target.repositoryFullName}
                            className={cn(
                              "rounded-full border px-2.5 py-1 text-[11px] font-bold transition-colors disabled:cursor-not-allowed disabled:opacity-45",
                              target.autoDeployEnabled
                                ? "border-brand/35 bg-brand/10 text-brand-strong"
                                : "border-line-strong text-muted"
                            )}
                            title={!target.repositoryFullName ? "GitHub App으로 연결된 대상만 자동 배포를 사용할 수 있습니다." : undefined}
                          >
                            {togglingTargetId === target.id
                              ? "변경 중..."
                              : target.autoDeployEnabled ? "자동 배포 ON" : "자동 배포 OFF"}
                          </button>
                          <button
                            type="button"
                            onClick={() => openDeleteTargetModal(target)}
                            disabled={deletingTargetId === target.id}
                            aria-label={`${target.name} 배포 대상 삭제`}
                            title="배포 대상 완전 삭제 (컨테이너·이미지·저장소·라우트 전체 정리)"
                            className="flex h-6 w-6 items-center justify-center rounded-md text-muted-soft transition-colors hover:text-danger disabled:cursor-not-allowed disabled:opacity-45"
                          >
                            {deletingTargetId === target.id ? (
                              <span className="text-[10px] font-bold">...</span>
                            ) : (
                              <svg aria-hidden className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 7h12M9 7V5a1 1 0 011-1h4a1 1 0 011 1v2m2 0v13a2 2 0 01-2 2H8a2 2 0 01-2-2V7h12z" />
                              </svg>
                            )}
                          </button>
                        </div>
                      </div>
                      <div className="mt-3 grid grid-cols-2 gap-2 text-[11px]">
                        <div>
                          <span className="text-muted-soft">최근 요청 </span>
                          <span className="font-mono text-muted">
                            {target.latestRequestedRevision?.slice(0, 10) ?? "—"}
                          </span>
                        </div>
                        <div>
                          <span className="text-muted-soft">현재 활성 </span>
                          <span className="font-mono text-muted">
                            {target.latestDeployedRevision?.slice(0, 10) ?? "—"}
                          </span>
                        </div>
                      </div>
                      {publicPorts.length > 0 && (
                        <div className="mt-3 border-t border-line pt-3">
                          <p className="mb-1.5 text-[10px] font-bold uppercase tracking-[0.08em] text-muted-soft">
                            공개 CNAME
                          </p>
                          <div className="flex flex-wrap gap-1.5">
                            {publicPorts.map((port) => (
                              port.protocol === "HTTP" ? (
                                <a
                                  key={port.id}
                                  href={`https://${port.fullDomain}`}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  title={`${port.nickname} · ${port.port} 포트를 새 창에서 열기`}
                                  className="inline-flex max-w-full items-center gap-1 rounded-md border border-brand/25 bg-brand/[0.07] px-2 py-1 font-mono text-[11px] text-brand-strong transition-colors hover:border-brand/45 hover:bg-brand/[0.12]"
                                >
                                  <span className="truncate">{port.fullDomain}</span>
                                  <svg aria-hidden className="h-3 w-3 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5h5m0 0v5m0-5L10 14M19 14v5H5V5h5" />
                                  </svg>
                                </a>
                              ) : (
                                <span
                                  key={port.id}
                                  title={`${port.nickname} · TCP ${port.port} 포트`}
                                  className="inline-flex max-w-full items-center rounded-md border border-line-strong bg-white/[0.03] px-2 py-1 font-mono text-[11px] text-muted"
                                >
                                  <span className="truncate">{port.fullDomain}</span>
                                </span>
                              )
                            ))}
                          </div>
                        </div>
                      )}
                    </article>
                    );
                  })}
                </div>
              </section>
            )}

            <section className="min-h-[280px] flex-1 overflow-auto rounded-panel border border-line">
              {deployments.length === 0 ? (
                <p className="py-16 text-center text-sm text-muted-soft">배포 이력이 없습니다</p>
              ) : (
                <Table>
                  <thead className="sticky top-0">
                    <tr>
                      <Th>생성일시</Th>
                      <Th>배포 대상</Th>
                      <Th>방식</Th>
                      <Th>트리거</Th>
                      <Th>리비전</Th>
                      <Th>상태</Th>
                      <Th className="w-48">작업</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {deployments.map((d) => {
                      const target = deploymentTargets.find((item) => item.id === d.deploymentTargetId);
                      return (
                        <tr key={d.id} className="hover:bg-white/[0.03]">
                          <Td className="text-xs text-muted-soft">{formatDate(d.createdAt)}</Td>
                          <Td className="text-xs font-bold">{target?.name ?? "레거시"}</Td>
                          <Td>{SOURCE_TYPE_LABEL[d.sourceType] ?? d.sourceType}</Td>
                          <Td className="text-xs text-muted">{TRIGGER_TYPE_LABEL[d.triggerType] ?? d.triggerType}</Td>
                          <Td className="font-mono text-xs text-muted-soft">
                            {(d.sourceRevision ?? d.requestedRevision)?.slice(0, 10) ?? "—"}
                          </Td>
                          <Td>
                            <StatusBadge tone={STATUS_TONE[d.status] ?? "off"}>{d.status}</StatusBadge>
                          </Td>
                          <Td>
                            <div className="flex items-center gap-3">
                              <button
                                onClick={() => router.push(`/instances/${vmId}/deployments/${d.id}`)}
                                className="text-xs font-bold text-brand-strong hover:underline"
                              >
                                보기
                              </button>
                              {d.status === "FAILED" && (
                                <button
                                  onClick={() => handleRetry(d.id)}
                                  disabled={retryingId === d.id}
                                  className="text-xs text-muted hover:underline disabled:opacity-50"
                                >
                                  {retryingId === d.id ? "불러오는 중..." : "재시도"}
                                </button>
                              )}
                              {d.status === "SUCCEEDED" && (
                                <button
                                  onClick={() => handleRollback(d.id)}
                                  disabled={rollingBackId === d.id}
                                  className="text-xs text-muted hover:underline disabled:opacity-50"
                                >
                                  {rollingBackId === d.id ? "롤백 요청 중..." : "롤백"}
                                </button>
                              )}
                            </div>
                          </Td>
                        </tr>
                      );
                    })}
                  </tbody>
                </Table>
              )}
            </section>
          </>
        )}
      </div>

      <Modal open={Boolean(targetPendingDeletion)} onClose={closeDeleteTargetModal}>
        {targetPendingDeletion && (
          <div className="mx-auto w-full max-w-[460px] rounded-panel border border-danger-soft bg-panel p-5">
            <div className="mb-4 flex items-start gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-danger/10 text-danger">
                <svg aria-hidden className="h-[18px] w-[18px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 7h12M9 7V5a1 1 0 011-1h4a1 1 0 011 1v2m2 0v13a2 2 0 01-2 2H8a2 2 0 01-2-2V7h12z" />
                </svg>
              </div>
              <div>
                <h2 className="text-base font-extrabold text-danger">배포 대상 완전 삭제</h2>
                <p className="mt-1 text-sm text-muted">
                  <span className="font-bold text-foreground">{targetPendingDeletion.name}</span>
                  을(를) 삭제하면 되돌릴 수 없습니다.
                </p>
              </div>
            </div>

            <div className="mb-4 rounded-[10px] border border-danger-soft bg-danger/[0.06] px-4 py-3">
              <p className="mb-2 text-xs font-bold text-danger">다음 항목이 모두 삭제됩니다.</p>
              <ul className="space-y-1 text-xs text-muted">
                <li>· 실행 중인 컨테이너와 이 대상이 만든 모든 Docker 이미지</li>
                <li>· Git 저장소, 릴리스 디렉터리와 설치 경로 링크</li>
                <li>· 연결된 모든 공개 라우트</li>
              </ul>
              <p className="mt-2 text-[11px] text-muted-soft">배포 이력은 감사 목적으로 유지됩니다.</p>
            </div>

            {deleteTargetError && (
              <div className="mb-4 rounded-md border border-danger-soft bg-danger/10 px-3 py-2.5 text-xs text-danger">
                {deleteTargetError}
              </div>
            )}

            <div className="flex justify-end gap-2">
              <Button size="small" onClick={closeDeleteTargetModal} disabled={Boolean(deletingTargetId)}>
                취소
              </Button>
              <Button
                variant="danger-solid"
                size="small"
                onClick={handleDeleteTarget}
                disabled={Boolean(deletingTargetId)}
              >
                {deletingTargetId ? "삭제 중..." : "완전히 삭제"}
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* 배포 생성 모달 — 목업의 위저드 톤(큰 패널, eyebrow+제목+설명 헤더, 섹션별 설명)을 따르되
          실제 흐름(Compose 직접 작성 / AI 자동생성 두 갈래, 각기 다른 하위 단계 수)은 그대로 유지 */}
      <Modal open={showCreate} onClose={closeCreate}>
        <div className="mx-auto flex h-[min(880px,92vh)] w-[min(980px,96vw)] flex-col overflow-hidden rounded-[20px] bg-background">
          {/* 헤더 */}
          <div className="flex items-center justify-between border-b border-line bg-panel px-6 py-5 shrink-0">
            <div>
              <span className="text-[11px] font-extrabold tracking-[.11em] text-muted-soft">DEPLOYMENT</span>
              <h2 className="mt-[5px] text-xl font-extrabold">{retryNotice ? "재시도 / 수정 후 재배포" : "새 배포"}</h2>
              <p className="mt-1 text-sm text-muted">
                {createTab === "compose"
                  ? "직접 작성한 docker-compose.yaml로 서비스를 배포합니다. 세부 설정을 완전히 제어할 수 있어요."
                  : "저장소를 분석해 AI가 배포 구성을 자동으로 만들어 드립니다. 검토 후 배포하세요."}
              </p>
            </div>
            <button onClick={closeCreate} className="flex h-[34px] w-[34px] shrink-0 items-center justify-center rounded-[9px] text-lg text-muted-soft hover:bg-white/[0.06]">
              ×
            </button>
          </div>

          <DeploymentWizardProgress
            createTab={createTab}
            currentStep={createStep}
            furthestStep={furthestCreateStep}
            onStepChange={visitCreateStep}
          />

          {/* 본문 (스크롤 영역) */}
          <div className="flex-1 overflow-y-auto p-6">
            <div className="mx-auto max-w-[820px] space-y-4">
              {error && (
                <div className="flex items-center justify-between rounded-md border border-danger-soft bg-danger/10 px-4 py-3 text-sm text-danger">
                  <span>{error}</span>
                  <button onClick={() => setError(null)} className="text-danger/60 hover:text-danger">✕</button>
                </div>
              )}

              {retryNotice && (
                <div className="rounded-md border border-[#e8b657]/25 bg-[#e8b657]/[0.06] px-3 py-2.5 text-xs text-[#e8b657]">
                  이전 배포의 compose 내용을 불러왔습니다. Git 저장소 URL/브랜치/PAT는 보안상 저장되지 않아 다시 입력해야 합니다. 필요하면 내용을 수정한 뒤 배포를 시작하세요.
                </div>
              )}

              {/* 배포 방식 선택 */}
              {createStep === 1 && <div className="wizard-step-enter">
                <h3 className="mb-1 text-sm font-extrabold">배포 방식</h3>
                <p className="mb-3 text-xs text-muted">두 가지 방식 중 하나를 선택하세요. 언제든 전환할 수 있습니다.</p>
                <div className="grid grid-cols-2 gap-3">
                  <button
                    type="button"
                    onClick={() => setCreateTab("compose")}
                    className={cn(
                      "rounded-[12px] border p-4 text-left transition-colors",
                      createTab === "compose" ? "border-brand shadow-[inset_0_0_0_1px_var(--brand)] bg-panel" : "border-line-strong bg-panel hover:border-white/20"
                    )}
                  >
                    <p className="mb-1 text-sm font-bold">Compose 직접 작성</p>
                    <p className="text-xs text-muted">docker-compose.yaml을 직접 작성해 배포합니다.</p>
                  </button>
                  <button
                    type="button"
                    onClick={() => setCreateTab("ai")}
                    className={cn(
                      "rounded-[12px] border p-4 text-left transition-colors",
                      createTab === "ai" ? "border-brand shadow-[inset_0_0_0_1px_var(--brand)] bg-panel" : "border-line-strong bg-panel hover:border-white/20"
                    )}
                  >
                    <p className="mb-1 text-sm font-bold">AI 자동 생성</p>
                    <p className="text-xs text-muted">저장소를 분석해 배포 구성을 자동으로 만들어 줍니다.</p>
                  </button>
                </div>
              </div>}

              {/* 공통: 저장소 연결 */}
              {createStep === 2 && <div className="wizard-step-enter">
                <Section
                  title="저장소 설정"
                  description="GitHub에 연결해 자동 배포를 사용하거나, Git URL을 직접 입력해 한 번만 배포할 수 있습니다."
                >
                  <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <button
                      type="button"
                      aria-pressed={repositorySource === "github"}
                      onClick={() => handleRepositorySourceChange("github")}
                      className={cn(
                        "rounded-[12px] border p-4 text-left transition-colors",
                        repositorySource === "github"
                          ? "border-brand bg-brand/[0.06] shadow-[inset_0_0_0_1px_var(--brand)]"
                          : "border-line-strong bg-white/[0.02] hover:border-white/20"
                      )}
                    >
                      <span className="flex items-center justify-between gap-3">
                        <span className="text-sm font-bold text-foreground">GitHub 저장소 연결</span>
                        <span className="rounded-full bg-brand/15 px-2 py-0.5 text-[10px] font-extrabold text-brand-strong">
                          자동 배포
                        </span>
                      </span>
                      <span className="mt-1.5 block text-xs leading-relaxed text-muted">
                        GitHub App으로 저장소를 선택하고 push마다 다시 배포합니다.
                      </span>
                    </button>
                    <button
                      type="button"
                      aria-pressed={repositorySource === "url"}
                      onClick={() => handleRepositorySourceChange("url")}
                      className={cn(
                        "rounded-[12px] border p-4 text-left transition-colors",
                        repositorySource === "url"
                          ? "border-brand bg-brand/[0.06] shadow-[inset_0_0_0_1px_var(--brand)]"
                          : "border-line-strong bg-white/[0.02] hover:border-white/20"
                      )}
                    >
                      <span className="flex items-center justify-between gap-3">
                        <span className="text-sm font-bold text-foreground">Git URL 직접 입력</span>
                        <span className="rounded-full bg-white/[0.06] px-2 py-0.5 text-[10px] font-extrabold text-muted">
                          단발성
                        </span>
                      </span>
                      <span className="mt-1.5 block text-xs leading-relaxed text-muted">
                        공개 URL이나 PAT를 입력해 이번 배포에만 사용합니다.
                      </span>
                    </button>
                  </div>

                  {repositorySource === "github" ? (
                    <div className="mb-4 space-y-3 rounded-[10px] border border-brand/25 bg-brand/[0.04] p-4">
                      <div className="grid grid-cols-[minmax(0,1fr)_auto] items-end gap-x-2 gap-y-[7px]">
                        <label
                          htmlFor="deploy-github-repository"
                          className="col-start-1 row-start-1 text-xs font-bold text-muted"
                        >
                          GitHub 저장소
                        </label>
                        <Select
                          id="deploy-github-repository"
                          value={selectedGithubRepositoryKey}
                          onChange={(e) => handleGithubRepositoryChange(e.target.value)}
                          disabled={githubLoading}
                          className="col-start-1 row-start-2 h-[42px]"
                        >
                          <option value="">
                            {githubLoading ? "저장소 불러오는 중..." : "연결된 저장소 선택"}
                          </option>
                          {githubRepositories.map((repository) => (
                            <option
                              key={`${repository.installationId}:${repository.id}`}
                              value={`${repository.installationId}:${repository.id}`}
                            >
                              {repository.fullName}{repository.privateRepository ? " · Private" : ""}
                            </option>
                          ))}
                        </Select>
                        <Button
                          type="button"
                          onClick={handleConnectGithub}
                          disabled={githubConnecting}
                          className="col-start-2 row-start-2 h-[42px]"
                        >
                          {githubConnecting ? "GitHub 이동 중..." : githubRepositories.length > 0 ? "저장소 권한 추가" : "GitHub 연결"}
                        </Button>
                      </div>
                      <Field label="배포 브랜치" htmlFor="deploy-github-branch">
                        <Input
                          id="deploy-github-branch"
                          name="deploy-github-branch"
                          value={githubBranch}
                          onChange={(e) => handleBranchChange(e.target.value)}
                          disabled={!selectedGithubRepositoryKey}
                        />
                      </Field>
                      <label className="flex items-start gap-2 rounded-[10px] border border-brand/20 bg-white/[0.02] p-3">
                        <input
                          type="checkbox"
                          checked={Boolean(selectedGithubRepositoryKey && autoDeploy)}
                          onChange={(e) => setAutoDeploy(e.target.checked)}
                          disabled={!selectedGithubRepositoryKey}
                          className="mt-0.5 accent-brand"
                        />
                        <span>
                          <span className="block text-xs font-bold text-foreground">커밋 시 자동 재배포</span>
                          <span className="mt-0.5 block text-[11px] text-muted-soft">
                            지정 브랜치에 push되면 정확한 커밋 SHA를 배포합니다. 연속 push는 최신 커밋 하나로 합쳐집니다.
                          </span>
                        </span>
                      </label>
                      <p className="text-[11px] text-muted-soft">
                        GitHub App은 선택한 저장소의 코드 읽기와 push 이벤트만 사용합니다.
                      </p>
                    </div>
                  ) : (
                    <div className="mb-4 grid grid-cols-1 gap-3 rounded-[10px] border border-line-strong bg-white/[0.025] p-4 sm:grid-cols-2">
                      <Field label="Git 저장소 URL" htmlFor="deploy-repo-url">
                        <Input
                          id="deploy-repo-url"
                          name="deploy-repo-url"
                          value={manualRepoUrl}
                          onChange={(e) => handleRepoUrlChange(e.target.value)}
                          placeholder="https://github.com/user/repo.git"
                        />
                      </Field>
                      <Field label="브랜치" htmlFor="deploy-manual-branch">
                        <Input
                          id="deploy-manual-branch"
                          name="deploy-manual-branch"
                          value={manualBranch}
                          onChange={(e) => handleBranchChange(e.target.value)}
                        />
                      </Field>
                      <Field label="PAT (비공개 저장소인 경우)" htmlFor="deploy-pat" className="sm:col-span-2">
                        <Input
                          id="deploy-pat"
                          name="deploy-pat"
                          type="password"
                          value={patToken}
                          onChange={(e) => handlePatTokenChange(e.target.value)}
                          placeholder="공개 저장소면 비워두세요"
                        />
                        <p className="mt-1 text-[11px] font-normal normal-case text-muted-soft">
                          URL과 PAT는 이번 수동 배포에만 사용하며 저장하지 않습니다.
                        </p>
                      </Field>
                    </div>
                  )}

                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    {!retryNotice && (
                      <Field label="배포 대상 이름" htmlFor="deploy-target-name" className="sm:col-span-2">
                        <Input
                          id="deploy-target-name"
                          name="deploy-target-name"
                          value={targetName}
                          onChange={(e) => setTargetName(e.target.value.slice(0, 60))}
                          placeholder="예: api, web, admin"
                        />
                        <p className="mt-1 text-[11px] font-normal normal-case text-muted-soft">
                          같은 VM 안에서 이 앱의 컨테이너, 이미지, 릴리스와 라우트를 다른 앱과 분리하는 이름입니다.
                        </p>
                      </Field>
                    )}
                    <Field label="저장소 배포 디렉토리 (선택)" htmlFor="deploy-context" className="sm:col-span-2">
                      <Input
                        id="deploy-context"
                        name="deploy-context"
                        value={context}
                        onChange={(e) => handleRepositoryContextChange(e.target.value)}
                        placeholder="예: backend (비워두면 저장소 루트에서 배포)"
                      />
                      <p className="mt-1 text-[11px] font-normal normal-case text-muted-soft">
                        {createTab === "compose"
                          ? "모노레포에서 특정 폴더만 배포하고 싶을 때 입력하세요. Compose 파일 업로드와 이미지 빌드가 이 디렉토리를 기준으로 실행됩니다."
                          : "모노레포에서 분석할 기준 폴더를 입력하세요. AI 서비스 힌트의 경로는 이 디렉토리를 기준으로 계산되고 최종 이미지 빌드에도 그대로 반영됩니다."}
                        {" "}(저장소 내부 경로)
                      </p>
                    </Field>
                    <Field label="VM 배포 경로 (선택)" htmlFor="deploy-install-path" className="sm:col-span-2">
                      <Input
                        id="deploy-install-path"
                        name="deploy-install-path"
                        value={installPath}
                        onChange={(e) => setInstallPath(e.target.value)}
                        placeholder="예: /home/ubuntu/myapp (비워두면 gamjabox가 자동 관리하는 경로만 사용)"
                      />
                      <p className="mt-1 text-[11px] font-normal normal-case text-muted-soft">
                        VM 내부의 이 경로에 현재 배포를 가리키는 심볼릭 링크를 만듭니다. (VM 파일시스템 절대경로)
                      </p>
                    </Field>
                  </div>
                </Section>
              </div>}

              {createTab === "compose" ? (
                createStep === 3 ? (
                <div className="wizard-step-enter space-y-4">
                  {composeDetection?.detected && (
                    <ComposeDetectionCard
                      result={composeDetection}
                      selectedPath={selectedDetectedComposePath}
                      detecting={detectingCompose || planningRouter}
                      reviewing={reviewingCompose}
                      reviewFindings={composeReviewFindings}
                      onSelect={(path) => {
                        setSelectedDetectedComposePath(path);
                        setComposeReviewFindings(null);
                      }}
                      onApply={applyDetectedCompose}
                      onReview={(file) => handleReviewCompose(file.content)}
                      onRefresh={() => { void handleDetectCompose(true); }}
                    />
                  )}
                  {composeDetection && !composeDetection.detected && (
                    <div className="rounded-md border border-line bg-white/[0.025] px-4 py-3 text-xs text-muted">
                      <p>지정한 배포 디렉터리에서 Compose 파일을 찾지 못했습니다. 아래 편집기에 직접 작성하면 됩니다.</p>
                      {composeDetection.warnings.map((warning) => (
                        <p key={warning} className="mt-1 text-[#e8b657]">⚠ {warning}</p>
                      ))}
                    </div>
                  )}
                  {composeDetectionError && (
                    <div className="rounded-md border border-[#e8b657]/25 bg-[#e8b657]/[0.06] px-4 py-3 text-xs text-[#e8b657]">
                      Compose 자동 탐지는 완료하지 못했습니다: {composeDetectionError} 직접 작성은 계속할 수 있습니다.
                    </div>
                  )}
                  <Section title="Compose 작성" description="저장소(또는 위에서 지정한 디렉토리)에서 실행할 서비스를 docker-compose.yaml 형식으로 작성하세요.">
                    <Field label="docker-compose.yaml" htmlFor="deploy-compose">
                      <Textarea
                        id="deploy-compose"
                        name="deploy-compose"
                        value={composeContent}
                        onChange={(e) => {
                          setComposeContent(e.target.value);
                          setComposeReviewFindings(null);
                          setRouterPlan(null);
                          setRouterApplied(false);
                          setRouterServicePorts({});
                        }}
                        rows={10}
                        spellCheck={false}
                        placeholder={"services:\n  web:\n    build: .\n    ports:\n      - \"3000:3000\""}
                        className="font-mono resize-none"
                      />
                    </Field>
                    <Button
                      type="button"
                      onClick={() => { void handlePlanComposeRouter(composeContent, true); }}
                      disabled={!composeContent.trim() || planningRouter || routerApplied}
                    >
                      {routerApplied
                        ? "Caddy 라우팅 적용 완료"
                        : planningRouter ? "다중 서비스 라우팅 분석 중..." : "다중 서비스 라우팅 자동 구성"}
                    </Button>

                    <button
                      type="button"
                      onClick={() => setShowAdvanced((v) => !v)}
                      className="text-xs text-muted hover:text-foreground text-left"
                    >
                      고급 설정 (환경변수 파일 · 라우트 노출 · 헬스체크) {showAdvanced ? "▴" : "▾"}
                    </button>

                    {showAdvanced && (
                      <div className="mt-3 space-y-4 rounded-md border border-line p-3">
                        <p className="text-[11px] text-muted-soft">모두 선택 사항입니다. 필요한 항목만 채우세요.</p>
                        {/* 환경변수 파일 */}
                        <div>
                          <div className="flex items-center justify-between mb-1">
                            <p className="text-xs font-bold text-foreground">환경변수 파일</p>
                            <button type="button" onClick={() => setEnvFiles((prev) => [...prev, emptyEnvFile()])} className="text-xs text-brand-strong font-bold">+ 추가</button>
                          </div>
                          <p className="mb-1.5 text-[11px] text-muted-soft">VM에 업로드할 .env 파일의 경로와 내용을 지정합니다.</p>
                          {envFiles.map((f, i) => (
                            <div key={i} className="flex gap-2 mb-2">
                              <input
                                value={f.vmPath}
                                onChange={(e) => setEnvFiles((prev) => prev.map((x, xi) => (xi === i ? { ...x, vmPath: e.target.value } : x)))}
                                placeholder="경로 (예: .env)"
                                className="w-32 h-8 px-2 border border-line-strong rounded text-xs shrink-0"
                              />
                              <textarea
                                value={f.content}
                                onChange={(e) => setEnvFiles((prev) => prev.map((x, xi) => (xi === i ? { ...x, content: e.target.value } : x)))}
                                placeholder="KEY=value"
                                rows={2}
                                className="flex-1 px-2 py-1.5 border border-line-strong rounded text-xs font-mono resize-none"
                              />
                              <button type="button" onClick={() => setEnvFiles((prev) => prev.filter((_, xi) => xi !== i))} className="text-muted-soft hover:text-danger self-start mt-1.5">✕</button>
                            </div>
                          ))}
                        </div>

                        {/* 라우트 노출 */}
                        <div>
                          <div className="flex items-center justify-between mb-1">
                            <p className="text-xs font-bold text-foreground">라우트 노출</p>
                            <button type="button" onClick={() => setRoutes((prev) => [...prev, emptyExposedRoute()])} className="text-xs text-brand-strong font-bold">+ 추가</button>
                          </div>
                          <p className="mb-1.5 text-[11px] text-muted-soft">외부에서 접근 가능하게 노출할 서비스 포트를 지정합니다. 공개 주소(CNAME)는 아래 라우팅 분석 결과에서 진입점 단위로 지정합니다.</p>
                          {routes.map((r, i) => (
                            <div key={i} className="mb-2 grid grid-cols-6 gap-1.5 items-center">
                              <input value={r.serviceName} onChange={(e) => setRoutes((prev) => prev.map((x, xi) => (xi === i ? { ...x, serviceName: e.target.value } : x)))} placeholder="서비스명" className="h-8 px-2 border border-line-strong rounded text-xs col-span-1" />
                              <input type="number" value={r.port} onChange={(e) => setRoutes((prev) => prev.map((x, xi) => (xi === i ? { ...x, port: Number(e.target.value) } : x)))} placeholder="포트" className="h-8 px-2 border border-line-strong rounded text-xs col-span-1" />
                              <select value={r.protocol} onChange={(e) => setRoutes((prev) => prev.map((x, xi) => (xi === i ? { ...x, protocol: e.target.value } : x)))} className="h-8 px-1 border border-line-strong rounded text-xs col-span-1">
                                <option value="HTTP">HTTP</option>
                                <option value="TCP">TCP</option>
                              </select>
                              <select value={r.visibility} onChange={(e) => setRoutes((prev) => prev.map((x, xi) => (xi === i ? { ...x, visibility: e.target.value } : x)))} className="h-8 px-1 border border-line-strong rounded text-xs col-span-1">
                                <option value="PUBLIC">공개</option>
                                <option value="PRIVATE">비공개</option>
                              </select>
                              <input value={r.nickname} onChange={(e) => setRoutes((prev) => prev.map((x, xi) => (xi === i ? { ...x, nickname: e.target.value } : x)))} placeholder="닉네임" className="h-8 px-2 border border-line-strong rounded text-xs col-span-1" />
                              <button type="button" onClick={() => setRoutes((prev) => prev.filter((_, xi) => xi !== i))} className="text-muted-soft hover:text-danger col-span-1">✕</button>
                            </div>
                          ))}
                        </div>

                        {/* 헬스체크 */}
                        <div>
                          <div className="flex items-center justify-between mb-1">
                            <p className="text-xs font-bold text-foreground">헬스체크</p>
                            <button type="button" onClick={() => setHealthChecks((prev) => [...prev, emptyHealthCheck()])} className="text-xs text-brand-strong font-bold">+ 추가</button>
                          </div>
                          <p className="mb-1.5 text-[11px] text-muted-soft">컨테이너 교체 후 정상 기동을 확인할 방법을 지정합니다. 실패 시 자동 롤백됩니다.</p>
                          {healthChecks.map((h, i) => (
                            <div key={i} className="grid grid-cols-5 gap-1.5 mb-2 items-center">
                              <input value={h.serviceName} onChange={(e) => setHealthChecks((prev) => prev.map((x, xi) => (xi === i ? { ...x, serviceName: e.target.value } : x)))} placeholder="서비스명" className="h-8 px-2 border border-line-strong rounded text-xs col-span-1" />
                              <input value={h.path} onChange={(e) => setHealthChecks((prev) => prev.map((x, xi) => (xi === i ? { ...x, path: e.target.value } : x)))} placeholder="경로 (예: /health)" className="h-8 px-2 border border-line-strong rounded text-xs col-span-1" />
                              <input type="number" value={h.hostPort ?? ""} onChange={(e) => setHealthChecks((prev) => prev.map((x, xi) => (xi === i ? { ...x, hostPort: e.target.value ? Number(e.target.value) : undefined } : x)))} placeholder="호스트포트" className="h-8 px-2 border border-line-strong rounded text-xs col-span-1" />
                              <input type="number" value={h.containerPort ?? ""} onChange={(e) => setHealthChecks((prev) => prev.map((x, xi) => (xi === i ? { ...x, containerPort: e.target.value ? Number(e.target.value) : undefined } : x)))} placeholder="컨테이너포트" className="h-8 px-2 border border-line-strong rounded text-xs col-span-1" />
                              <button type="button" onClick={() => setHealthChecks((prev) => prev.filter((_, xi) => xi !== i))} className="text-muted-soft hover:text-danger col-span-1">✕</button>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </Section>

                  {routerPlan && (
                    <ComposeRouterPlanCard
                      plan={routerPlan}
                      planning={planningRouter}
                      applied={routerApplied}
                      hostPort={routerHostPort}
                      servicePorts={routerServicePorts}
                      routeOverrides={routerRouteOverrides}
                      planType={planType}
                      subdomainCheck={domainSubdomainCheck}
                      onHostPortChange={(port) => {
                        setRouterHostPort(port);
                        setRouterApplied(false);
                      }}
                      onServicePortChange={(service, port) => {
                        setRouterServicePorts((previous) => ({ ...previous, [service]: port }));
                        setRouterApplied(false);
                      }}
                      onRouteOverrideChange={(service, override) => {
                        setRouterRouteOverrides((previous) => ({ ...previous, [service]: override }));
                        setRouterApplied(false);
                      }}
                      onDomainSubdomainChange={handleDomainSubdomainChange}
                      onReplan={() => { void handlePlanComposeRouter(composeContent, false, routerHostPort); }}
                      onApply={() => applyRouterPlan(routerPlan)}
                    />
                  )}

                  {composePublicEndpoint && (
                    <PublicEndpointCnameCard
                      endpoint={composePublicEndpoint.endpoint}
                      throughCaddy={composePublicEndpoint.throughCaddy}
                      planType={planType}
                      customSubdomain={deploymentCustomSubdomain}
                      check={deploymentSubdomainCheck}
                      onChange={handleDeploymentCustomSubdomainChange}
                    />
                  )}

                  <Section title="Docker 네트워크" description="VM에 이미 존재하는 네트워크를 재사용하려면 compose 파일에 아래처럼 external 네트워크로 선언하세요.">
                    {dockerNetworks.length === 0 ? (
                      <p className="text-xs text-muted-soft">이 VM에는 아직 조회 가능한 Docker 네트워크가 없습니다. (새로 생성하는 경우 무시해도 됩니다)</p>
                    ) : (
                      <div className="flex flex-wrap gap-1.5">
                        {dockerNetworks.map((n) => (
                          <span key={n.ID} className="rounded-md border border-line-strong bg-white/[0.03] px-2 py-1 text-xs font-mono text-foreground">
                            {n.Name}
                          </span>
                        ))}
                      </div>
                    )}
                    <pre className="mt-3 overflow-x-auto rounded-[10px] border border-line bg-[#0c0e12] p-3 font-mono text-[11px] leading-[1.6] text-foreground">
{`networks:\n  default:\n    external: true\n    name: <재사용할 네트워크 이름>`}
                    </pre>
                  </Section>
                </div>
                ) : createStep === 4 ? (
                  <div className="wizard-step-enter">
                    <Section title="검토 및 배포" description="입력한 저장소와 Compose 설정을 확인하세요. 수정이 필요하면 이전 단계로 돌아가도 현재 입력은 그대로 유지됩니다.">
                      <dl className="grid grid-cols-2 gap-3 text-xs">
                        <div className="rounded-md border border-line bg-white/[0.025] p-3">
                          <dt className="mb-1 text-muted-soft">배포 대상</dt>
                          <dd className="font-bold text-foreground">{targetName || "기존 배포 재시도"}</dd>
                        </div>
                        <div className="rounded-md border border-line bg-white/[0.025] p-3">
                          <dt className="mb-1 text-muted-soft">자동 재배포</dt>
                          <dd className="text-foreground">
                            {activeGithubRepository && autoDeploy ? "Git push 시 자동 실행" : "사용 안 함"}
                          </dd>
                        </div>
                        <div className="rounded-md border border-line bg-white/[0.025] p-3">
                          <dt className="mb-1 text-muted-soft">
                            저장소 · {repositorySource === "github" ? "GitHub 연결" : "Git URL 단발성"}
                          </dt>
                          <dd className="break-all font-mono text-foreground">{repoUrl}</dd>
                        </div>
                        <div className="rounded-md border border-line bg-white/[0.025] p-3">
                          <dt className="mb-1 text-muted-soft">브랜치</dt>
                          <dd className="font-mono text-foreground">{branch}</dd>
                        </div>
                        <div className="rounded-md border border-line bg-white/[0.025] p-3">
                          <dt className="mb-1 text-muted-soft">저장소 배포 디렉토리</dt>
                          <dd className="font-mono text-foreground">{context.trim() || "저장소 루트"}</dd>
                        </div>
                        <div className="rounded-md border border-line bg-white/[0.025] p-3">
                          <dt className="mb-1 text-muted-soft">VM 배포 경로</dt>
                          <dd className="font-mono text-foreground">{installPath.trim() || "자동 관리 경로"}</dd>
                        </div>
                      </dl>
                      <div className="mt-4">
                        <div className="mb-2 flex items-center justify-between">
                          <p className="text-xs font-bold">docker-compose.yaml</p>
                          <span className="text-[11px] text-muted-soft">
                            환경 파일 {envFiles.filter((file) => file.vmPath && file.content).length} · 라우트 {routes.filter((route) => route.serviceName && route.nickname).length} · 헬스체크 {healthChecks.filter((check) => check.serviceName && check.path).length}
                          </span>
                        </div>
                        <pre className="max-h-64 overflow-auto rounded-[10px] border border-line bg-[#0c0e12] p-3 font-mono text-[11px] leading-[1.6] text-foreground">
                          {composeContent}
                        </pre>
                        <div className="mt-3 flex flex-wrap items-center gap-2">
                          <Button
                            type="button"
                            onClick={() => handleReviewCompose(composeContent)}
                            disabled={reviewingCompose}
                          >
                            {reviewingCompose ? "AI 검수 중..." : "AI 검수 요청"}
                          </Button>
                          <span className="text-[11px] text-muted-soft">
                            비밀값은 전송 전에 마스킹되며 검수 결과는 배포를 차단하지 않습니다.
                          </span>
                        </div>
                        <div className="mt-3">
                          <ComposeReviewFindingsView findings={composeReviewFindings} />
                        </div>
                      </div>
                    </Section>
                  </div>
                ) : null
              ) : (
                <>
                  {createStep === 3 && <div className="wizard-step-enter space-y-4">
                  {composeDetection?.detected && (
                    <ComposeDetectionCard
                      result={composeDetection}
                      selectedPath={selectedDetectedComposePath}
                      detecting={detectingCompose || planningRouter}
                      reviewing={reviewingCompose}
                      reviewFindings={composeReviewFindings}
                      onSelect={(path) => {
                        setSelectedDetectedComposePath(path);
                        setComposeReviewFindings(null);
                      }}
                      onApply={applyDetectedCompose}
                      onReview={(file) => handleReviewCompose(file.content)}
                      onRefresh={() => { void handleDetectCompose(true); }}
                    />
                  )}
                  {composeDetectionError && (
                    <div className="rounded-md border border-[#e8b657]/25 bg-[#e8b657]/[0.06] px-4 py-3 text-xs text-[#e8b657]">
                      Compose 자동 탐지는 완료하지 못했습니다: {composeDetectionError} AI 자동 생성은 계속할 수 있습니다.
                    </div>
                  )}
                  {composeDetection && !composeDetection.detected && Boolean(composeDetection.discoveredServices?.length) && (
                    <div className="rounded-md border border-[#75a9ff]/25 bg-[#75a9ff]/[0.055] px-4 py-3 text-xs text-[#a9c8ff]">
                      <p className="font-bold text-foreground">
                        실행 가능한 서비스 {composeDetection.discoveredServices?.length ?? 0}개를 저장소에서 자동 발견했습니다
                      </p>
                      <p className="mt-1">
                        {(composeDetection.discoveredServices ?? []).map((service) => service.context).join(", ")}
                      </p>
                      <p className="mt-1 text-[11px] text-muted">
                        아래 항목은 발견 결과를 바탕으로 채워졌습니다. 서비스 힌트는 전체 목록을 직접 작성하는 곳이 아니라,
                        자동 인식이 틀렸거나 추가 정보가 필요할 때만 보완하면 됩니다.
                      </p>
                    </div>
                  )}
                  <Section title="서비스 힌트" description="저장소에서 실행 가능한 모듈을 자동 인식합니다. 아래 값은 자동 인식 결과를 확인하거나 포트·실행 환경을 보완할 때만 수정하세요.">
                    <div className="mb-4">
                      <div className="flex items-center justify-between mb-1.5">
                        <p className="text-xs font-bold text-foreground">서비스</p>
                        <button type="button" onClick={addServiceCard} className="text-xs text-brand-strong font-bold">+ 서비스 추가</button>
                      </div>
                      {serviceCards.map((s, i) => (
                        <div key={i} className="rounded-md border border-line p-3 mb-2 space-y-2">
                          <div className="grid grid-cols-3 gap-2">
                            <input value={s.name} onChange={(e) => updateServiceCard(i, { name: e.target.value })} placeholder="서비스명" className="h-8 px-2 border border-line-strong rounded text-xs" />
                            <select value={s.runtime} onChange={(e) => updateServiceCard(i, { runtime: e.target.value })} className="h-8 px-2 border border-line-strong rounded text-xs">
                              <option value="docker">Docker (직접 빌드)</option>
                              <option value="java">Java</option>
                              <option value="node">Node.js</option>
                              <option value="python">Python</option>
                              <option value="static">정적 사이트</option>
                            </select>
                            <input value={s.context} onChange={(e) => updateServiceCard(i, { context: e.target.value })} placeholder={context.trim() ? "기준 디렉토리 내부 경로 (예: api)" : "저장소 내부 경로 (예: .)"} className="h-8 px-2 border border-line-strong rounded text-xs" />
                          </div>
                          {context.trim() && (
                            <p className="text-[10px] text-muted-soft">
                              실제 분석·빌드 경로: <span className="font-mono text-muted">
                                {resolveServiceContext(context, s.context, composeDetection?.discoveredServices)}
                              </span>
                            </p>
                          )}
                          <div className="grid grid-cols-3 gap-2">
                            <input type="number" value={s.containerPort} onChange={(e) => updateServiceCard(i, { containerPort: Number(e.target.value) })} placeholder="컨테이너 포트" className="h-8 px-2 border border-line-strong rounded text-xs" />
                            <input value={s.buildCommand ?? ""} onChange={(e) => updateServiceCard(i, { buildCommand: e.target.value })} placeholder="빌드 명령 (선택)" className="h-8 px-2 border border-line-strong rounded text-xs" />
                            <input value={s.startCommand ?? ""} onChange={(e) => updateServiceCard(i, { startCommand: e.target.value })} placeholder="시작 명령 (선택)" className="h-8 px-2 border border-line-strong rounded text-xs" />
                          </div>
                          <div className="flex items-center justify-between">
                            <label className="flex items-center gap-1.5 text-xs text-muted">
                              <input type="checkbox" checked={s.expose} onChange={(e) => handleServiceExposureChange(i, e.target.checked)} className="accent-brand" />
                              외부 노출
                            </label>
                            <button type="button" onClick={() => removeServiceCard(i)} className="text-xs text-muted-soft hover:text-danger">삭제</button>
                          </div>
                        </div>
                      ))}
                    </div>

                    <div className="mb-4">
                      <div className="flex items-center justify-between mb-1.5">
                        <p className="text-xs font-bold text-foreground">공유 인프라</p>
                        <button type="button" onClick={addInfrastructure} className="text-xs text-brand-strong font-bold">+ 추가</button>
                      </div>
                      <p className="mb-1.5 text-[11px] text-muted-soft">서비스가 함께 사용할 DB/캐시 등 공유 인프라를 지정합니다.</p>
                      {infraSelections.map((inf, i) => (
                        <div key={i} className="flex gap-2 mb-2">
                          <select value={inf.type} onChange={(e) => updateInfrastructure(i, { type: e.target.value })} className="h-8 px-2 border border-line-strong rounded text-xs">
                            <option value="postgres">PostgreSQL</option>
                            <option value="mysql">MySQL</option>
                            <option value="redis">Redis</option>
                            <option value="mongodb">MongoDB</option>
                          </select>
                          <input value={inf.version ?? ""} onChange={(e) => updateInfrastructure(i, { version: e.target.value })} placeholder="버전 (선택, 비우면 AI가 선택)" className="flex-1 h-8 px-2 border border-line-strong rounded text-xs" />
                          <button type="button" onClick={() => removeInfrastructure(i)} className="text-muted-soft hover:text-danger">✕</button>
                        </div>
                      ))}
                    </div>

                    <div className="mb-4">
                      <p className="mb-1.5 text-xs font-bold text-foreground">Docker 네트워크</p>
                      <p className="mb-1.5 text-[11px] text-muted-soft">서비스들을 새 네트워크에 배치할지, VM에 이미 있는 네트워크를 재사용할지 선택하세요.</p>
                      <div className="flex flex-col gap-1.5">
                        <label className="flex items-center gap-2 text-xs text-foreground">
                          <input
                            type="radio"
                            name="deploy-network-mode"
                            checked={networkMode === "create"}
                            onChange={() => handleNetworkModeChange("create")}
                            className="accent-brand"
                          />
                          새 네트워크 생성
                        </label>
                        <label className="flex items-center gap-2 text-xs text-foreground">
                          <input
                            type="radio"
                            name="deploy-network-mode"
                            checked={networkMode === "reuse"}
                            onChange={() => handleNetworkModeChange("reuse")}
                            disabled={dockerNetworks.length === 0}
                            className="accent-brand"
                          />
                          기존 네트워크 재사용
                        </label>
                        {networkMode === "reuse" && (
                          <Select
                            value={existingNetworkName}
                            onChange={(e) => handleExistingNetworkChange(e.target.value)}
                            className="mt-1 w-64"
                          >
                            <option value="">네트워크 선택</option>
                            {dockerNetworks.map((n) => (
                              <option key={n.ID} value={n.Name}>{n.Name}</option>
                            ))}
                          </Select>
                        )}
                        {dockerNetworks.length === 0 && (
                          <p className="text-[11px] text-muted-soft">이 VM에는 아직 조회 가능한 Docker 네트워크가 없어 재사용할 수 없습니다.</p>
                        )}
                      </div>
                    </div>

                  </Section>
                  </div>}

                  {/* 결정론적 저장소 분석 결과 — AI 호출 여부와 무관하게 항상 먼저 보여줌 */}
                  {createStep === 4 && <div className="wizard-step-enter space-y-4">
                  <Section title="배포 설정 요약" description="이전 단계에서 입력한 값입니다. 수정하려면 단계 표시나 이전 버튼으로 돌아가세요. 입력 내용은 유지됩니다.">
                    <dl className="grid grid-cols-2 gap-3 text-xs">
                      <div className="rounded-md border border-line bg-white/[0.025] p-3">
                        <dt className="mb-1 text-muted-soft">배포 대상</dt>
                        <dd className="font-bold text-foreground">{targetName || "기존 배포 재시도"}</dd>
                      </div>
                      <div className="rounded-md border border-line bg-white/[0.025] p-3">
                        <dt className="mb-1 text-muted-soft">자동 재배포</dt>
                        <dd className="text-foreground">
                          {activeGithubRepository && autoDeploy ? "Git push 시 자동 실행" : "사용 안 함"}
                        </dd>
                      </div>
                      <div className="rounded-md border border-line bg-white/[0.025] p-3">
                        <dt className="mb-1 text-muted-soft">
                          저장소 · {repositorySource === "github" ? "GitHub 연결" : "Git URL 단발성"}
                        </dt>
                        <dd className="break-all font-mono text-foreground">{repoUrl} · {branch}</dd>
                      </div>
                      <div className="rounded-md border border-line bg-white/[0.025] p-3">
                        <dt className="mb-1 text-muted-soft">저장소 배포 디렉토리</dt>
                        <dd className="font-mono text-foreground">{context.trim() || "저장소 루트"}</dd>
                      </div>
                      <div className="rounded-md border border-line bg-white/[0.025] p-3">
                        <dt className="mb-1 text-muted-soft">서비스</dt>
                        <dd className="text-foreground">{serviceCards.filter((service) => service.name).map((service) => service.name).join(", ")}</dd>
                      </div>
                      <div className="rounded-md border border-line bg-white/[0.025] p-3">
                        <dt className="mb-1 text-muted-soft">VM 배포 경로</dt>
                        <dd className="font-mono text-foreground">{installPath.trim() || "자동 관리 경로"}</dd>
                      </div>
                    </dl>
                  </Section>

                  {evidenceRefs.length > 0 && (
                    <Section title="저장소 분석 결과" description="AI 호출 전에 저장소를 결정론적 규칙으로 먼저 분석한 근거입니다.">
                      <div className="space-y-1 text-xs text-[#7ab3f5]">
                        {evidenceRefs.map((ref, i) => {
                          const [ctx, detectedType, confidence] = ref.split(":");
                          return (
                            <p key={i}>
                              · <span className="font-mono">{ctx || "."}</span> → {detectedType}
                              {confidence && <span className="text-[#7ab3f5]/70"> (신뢰도: {confidence})</span>}
                            </p>
                          );
                        })}
                      </div>
                    </Section>
                  )}

                  {generationWarnings.length > 0 && (
                    <div className="rounded-md border border-[#e8b657]/25 bg-[#e8b657]/[0.06] p-3 text-xs text-[#e8b657] space-y-1">
                      {generationWarnings.map((w, i) => <p key={i}>⚠ {w}</p>)}
                    </div>
                  )}

                  {serviceCards.filter((service) => service.expose).length >= 2 && (
                    <div className="rounded-md border border-[#75a9ff]/25 bg-[#75a9ff]/[0.055] p-3 text-xs text-[#a9c8ff]">
                      <p className="font-bold text-foreground">다중 HTTP 서비스 라우팅 자동 보강</p>
                      <p className="mt-1">
                        배포 시 Caddy 단일 진입점과 서비스별 경로 라우팅을 Compose에 자동 렌더링합니다.
                        AI 검수도 최종 Caddy 구성이 포함된 Compose를 기준으로 실행됩니다.
                      </p>
                    </div>
                  )}

                  {/* 근거 부족/충돌 등으로 확정하지 못한 경우 — 억지로 스펙을 만들어내지 않고 사유를 그대로 보여줌 */}
                  {generationStatus && generationStatus !== "READY" && (
                    <div className="rounded-md border border-danger-soft bg-danger/10 p-3 text-xs text-danger space-y-1.5">
                      <p className="font-bold">
                        {generationStatus === "NEEDS_INPUT" && "추가 정보가 필요합니다"}
                        {generationStatus === "UNSUPPORTED" && "이 구성은 자동 배포를 지원하지 않습니다"}
                        {generationStatus === "CONFLICT" && "입력값이 저장소 분석 결과와 충돌합니다"}
                        {generationStatus === "INVALID_RESPONSE" && "스펙 생성에 실패했습니다"}
                      </p>
                      {unresolvedFields.map((f, i) => (
                        <p key={i}>· [{f.field}] {f.reason}</p>
                      ))}
                    </div>
                  )}

                  {generatedSpec && (
                    <Section title="생성된 배포 구성" description="결정론적 규칙으로 확정된 부분과 AI가 확정한 부분이 합쳐진 결과입니다. 검토 후 필요하면 직접 수정할 수 있습니다.">
                      <div className="space-y-3">
                        <Textarea
                          id="deploy-generated-spec"
                          name="deploy-generated-spec"
                          value={generatedSpec}
                          onChange={(e) => {
                            setGeneratedSpec(e.target.value);
                            setRenderedSpec(null);
                            setReviewFindings(null);
                          }}
                          rows={12}
                          spellCheck={false}
                          className="font-mono resize-none"
                        />
                        <div className="flex flex-wrap gap-2">
                          <Button type="button" onClick={handleRenderSpec} disabled={renderingSpec}>
                            {renderingSpec ? "최종 Compose 생성 중..." : "최종 Compose 다시 생성"}
                          </Button>
                          <Button type="button" onClick={handleReviewSpec} disabled={reviewing}>
                            {reviewing ? "AI 검수 중..." : "AI 검수 요청 (선택 — 결과가 배포를 막지 않습니다)"}
                          </Button>
                        </div>
                        {renderedSpec && (
                          <div className="rounded-md border border-[#75a9ff]/25 bg-[#75a9ff]/[0.045] p-3">
                            <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                              <p className="text-xs font-bold text-foreground">실제 배포될 최종 docker-compose.yaml</p>
                              <span className="text-[11px] text-[#a9c8ff]">
                                공개 라우트 {renderedSpec.exposedRoutes.length}개
                                {renderedSpec.composeContent.includes("gamjabox-router") && " · Caddy 자동 보강됨"}
                              </span>
                            </div>
                            <pre className="max-h-80 overflow-auto rounded-[10px] border border-line bg-[#0c0e12] p-3 font-mono text-[11px] leading-[1.6] text-foreground">
                              {renderedSpec.composeContent}
                            </pre>
                          </div>
                        )}
                        {aiRouterPlan
                          && (aiThroughCaddy || aiRouterPlan.status === "NEEDS_INPUT")
                          && aiRouterPlan.routes.some((route) => !route.root) && (
                          <div className="rounded-md border border-[#75a9ff]/25 bg-white/[0.02] p-3">
                            <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                              <p className="text-xs font-bold text-foreground">서비스별 노출 방식</p>
                              <Button type="button" onClick={handleRegenerateAiRouting} disabled={renderingSpec}>
                                {renderingSpec ? "다시 생성 중..." : "라우팅 다시 생성"}
                              </Button>
                            </div>
                            <p className="mb-2 text-[11px] text-muted-soft">
                              각 서비스를 공용 도메인 아래 경로(Prefix)로 둘지, 전용 서브도메인(도메인)으로 노출할지 고른 뒤 라우팅을 다시 생성하세요.
                            </p>
                            {aiRouterPlan.unresolvedServices.length > 0 && (
                              <div className="mb-2 space-y-1 rounded border border-[#e8b657]/25 bg-[#e8b657]/[0.06] p-2 text-[11px] text-[#e8b657]">
                                {aiRouterPlan.unresolvedServices.map((service) => (
                                  <p key={service.serviceName}>· [{service.serviceName}] {service.reason}</p>
                                ))}
                              </div>
                            )}
                            <RouteModeRows
                              routes={aiRouterPlan.routes}
                              overrides={routerRouteOverrides}
                              planType={planType}
                              subdomainCheck={domainSubdomainCheck}
                              disabled={renderingSpec}
                              onOverrideChange={(service, override) =>
                                setRouterRouteOverrides((previous) => ({ ...previous, [service]: override }))
                              }
                              onDomainSubdomainChange={handleDomainSubdomainChange}
                            />
                          </div>
                        )}
                        {aiPublicEndpoint && (
                          <PublicEndpointCnameCard
                            endpoint={aiPublicEndpoint.endpoint}
                            throughCaddy={aiPublicEndpoint.throughCaddy}
                            planType={planType}
                            customSubdomain={deploymentCustomSubdomain}
                            check={deploymentSubdomainCheck}
                            onChange={handleDeploymentCustomSubdomainChange}
                          />
                        )}
                        {reviewFindings && (
                          <div className="rounded-md border border-line bg-white/[0.03] p-3 text-xs text-foreground space-y-2">
                            <p className="text-[11px] text-muted-soft">AI 검수는 참고용이며 배포를 막지 않습니다.</p>
                            {reviewFindings.length === 0 ? (
                              <p className="text-muted-soft">특이사항이 없습니다.</p>
                            ) : (
                              reviewFindings.map((finding, i) => (
                                <div key={i} className="border-l-2 pl-2" style={{
                                  borderColor: finding.severity === "CRITICAL" ? "#ff6b6b" : finding.severity === "WARNING" ? "#e8b657" : "#9aa39a",
                                }}>
                                  <p className="font-bold text-foreground">
                                    [{finding.severity}] {finding.service && <span className="font-mono">{finding.service}</span>} {finding.message}
                                  </p>
                                  {finding.remediation && <p className="text-muted mt-0.5">→ {finding.remediation}</p>}
                                </div>
                              ))
                            )}
                          </div>
                        )}
                      </div>
                    </Section>
                  )}
                  </div>}
                </>
              )}
            </div>
          </div>

          {/* 푸터 */}
          <div className="flex shrink-0 items-center gap-2 border-t border-line bg-panel px-6 py-4">
            <span className="flex-1 text-xs text-muted-soft">
              {createStep}/4 단계 · 이전 단계로 돌아가도 이 창을 닫기 전까지 입력 내용이 유지됩니다.
            </span>
            <Button
              onClick={closeCreate}
              disabled={submitting || generating || reviewing || detectingCompose || reviewingCompose || planningRouter}
            >
              취소
            </Button>
            {createStep > 1 && (
              <Button type="button" onClick={handlePreviousCreateStep} disabled={submitting || generating || reviewing || detectingCompose || reviewingCompose || planningRouter}>
                이전
              </Button>
            )}
            {createStep < 4 && (
              <Button
                type="button"
                variant="primary"
                onClick={handleNextCreateStep}
                disabled={
                  generating ||
                  detectingCompose ||
                  reviewingCompose ||
                  planningRouter ||
                  (createStep === 2 && !repositoryStepReady) ||
                  (createStep === 3 && createTab === "compose" && !composeStepReady) ||
                  (createStep === 3 && createTab === "ai" && !aiHintsStepReady)
                }
              >
                {createStep === 1 && "저장소 설정"}
                {createStep === 2 && (
                  detectingCompose
                    ? "Compose 파일 탐지 중..."
                    : (createTab === "ai" ? "서비스 힌트 입력" : "Compose 작성")
                )}
                {createStep === 3 && createTab === "compose" && "검토로 이동"}
                {createStep === 3 && createTab === "ai" && (
                  generating ? "저장소 분석 + AI 생성 중..." : generationStatus ? "분석 결과 보기" : "AI 스펙 생성"
                )}
              </Button>
            )}
            {createStep === 4 && createTab === "compose" && (
              <Button
                type="button"
                variant="primary"
                onClick={handleCreateFromCompose}
                disabled={
                  submitting || reviewingCompose || planningRouter || !repositoryStepReady || !composeStepReady ||
                  !routingSubdomainsReady
                }
              >
                {submitting ? "배포 시작 중..." : "배포 시작"}
              </Button>
            )}
            {createStep === 4 && createTab === "ai" && generatedSpec && (
              <>
                <Button type="button" onClick={handleGenerateSpec} disabled={generating || submitting || reviewing}>
                  {generating ? "다시 생성 중..." : "다시 생성"}
                </Button>
                <Button
                  type="button"
                  variant="primary"
                  onClick={handleCreateFromSpec}
                  disabled={
                    submitting || !repositoryStepReady || !routingSubdomainsReady
                  }
                >
                  {submitting ? "배포 시작 중..." : "이 스펙으로 배포 시작"}
                </Button>
              </>
            )}
          </div>
        </div>
      </Modal>
    </div>
  );
}
