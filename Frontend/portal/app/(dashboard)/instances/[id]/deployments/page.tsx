"use client";

import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
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
  NetworkInfo,
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

function formatDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

type CreateTab = "compose" | "ai";

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

export default function DeploymentsPage() {
  const params = useParams();
  const router = useRouter();
  const vmId = params.id as string;
  const { accessToken } = useAuth();

  const [deployments, setDeployments] = useState<DeploymentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [showCreate, setShowCreate] = useState(false);
  const [createTab, setCreateTab] = useState<CreateTab>("compose");
  const [submitting, setSubmitting] = useState(false);
  const [retryingId, setRetryingId] = useState<string | null>(null);
  const [rollingBackId, setRollingBackId] = useState<string | null>(null);
  const [retryNotice, setRetryNotice] = useState(false);

  // 공통 (repo)
  const [repoUrl, setRepoUrl] = useState("");
  const [branch, setBranch] = useState("main");
  const [patToken, setPatToken] = useState("");

  // 공통 — VM 내 배포 경로 (심볼릭 링크)
  const [installPath, setInstallPath] = useState("");

  // 공통 — Docker 네트워크 (AI는 실제로 선택 반영, Compose는 참고용 목록만 노출)
  const [dockerNetworks, setDockerNetworks] = useState<NetworkInfo[]>([]);
  const [networkMode, setNetworkMode] = useState<NetworkMode>("create");
  const [existingNetworkName, setExistingNetworkName] = useState("");

  // Raw Compose
  const [composeContent, setComposeContent] = useState("");
  const [context, setContext] = useState("");
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [envFiles, setEnvFiles] = useState<EnvironmentFile[]>([]);
  const [routes, setRoutes] = useState<ExposedRoute[]>([]);
  const [healthChecks, setHealthChecks] = useState<HealthCheck[]>([]);
  // PRO 전용 커스텀 CNAME — 라우트별로 가용성 체크 상태를 따로 들고 있어야 함(여러 서비스 노출 가능)
  const [planType, setPlanType] = useState<string | null>(null);
  const [routeSubdomainCheck, setRouteSubdomainCheck] = useState<
    Record<number, "idle" | "checking" | "available" | "taken" | "reserved" | "pro-only">
  >({});
  const routeSubdomainTimers = useRef<Record<number, ReturnType<typeof setTimeout>>>({});

  // AI 자동생성
  const [serviceCards, setServiceCards] = useState<ServiceCard[]>([emptyServiceCard()]);
  const [infraSelections, setInfraSelections] = useState<InfraSelection[]>([]);
  const [generatedSpec, setGeneratedSpec] = useState<string>("");
  const [generating, setGenerating] = useState(false);
  const [reviewFindings, setReviewFindings] = useState<ComposeReviewFinding[] | null>(null);
  const [reviewing, setReviewing] = useState(false);
  // 결정론적 저장소 분석 + 명시적 불확실성 상태 — status가 READY가 아니면 generatedSpec은 비어있고
  // unresolvedFields에 이유가 담김 (AI가 근거 없이 완전한 스펙을 지어내지 않았다는 뜻)
  const [generationStatus, setGenerationStatus] = useState<GenerationStatus | null>(null);
  const [unresolvedFields, setUnresolvedFields] = useState<UnresolvedField[]>([]);
  const [evidenceRefs, setEvidenceRefs] = useState<string[]>([]);
  const [generationWarnings, setGenerationWarnings] = useState<string[]>([]);

  function handleRouteCustomSubdomainChange(index: number, value: string) {
    const lower = value.toLowerCase();
    setRoutes((prev) => prev.map((x, xi) => (xi === index ? { ...x, customSubdomain: lower } : x)));
    setRouteSubdomainCheck((prev) => ({ ...prev, [index]: "idle" }));
    if (routeSubdomainTimers.current[index]) clearTimeout(routeSubdomainTimers.current[index]);
    if (!lower || !accessToken) return;
    setRouteSubdomainCheck((prev) => ({ ...prev, [index]: "checking" }));
    routeSubdomainTimers.current[index] = setTimeout(async () => {
      try {
        const result = await api.vm.checkSubdomain(accessToken, vmId, lower);
        setRouteSubdomainCheck((prev) => ({
          ...prev,
          [index]: result.available ? "available" : ((result.reason as "taken" | "reserved" | "pro-only") ?? "taken"),
        }));
      } catch {
        setRouteSubdomainCheck((prev) => ({ ...prev, [index]: "taken" }));
      }
    }, 500);
  }

  const load = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setError(null);
    try {
      const res = await api.ops.deployments.list(accessToken, vmId);
      setDeployments(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : "배포 이력 조회에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  }, [accessToken, vmId]);

  useEffect(() => {
    load();
  }, [load]);

  // 배포 생성 모달을 열 때 VM에 이미 존재하는 Docker 네트워크 목록을 가져옴 — Compose 탭에서는 참고용으로
  // 그대로 보여주고, AI 탭에서는 "기존 네트워크 재사용" 선택지의 후보로 사용
  useEffect(() => {
    if (!showCreate || !accessToken) return;
    api.ops.docker.listNetworks(accessToken, vmId).then(setDockerNetworks).catch(() => setDockerNetworks([]));
  }, [showCreate, accessToken, vmId]);

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
    setInstallPath(spec.installPath ?? "");
    setEnvFiles(spec.environmentFiles);
    setRoutes(spec.exposedRoutes);
    setHealthChecks(spec.healthChecks);
    setShowAdvanced(spec.environmentFiles.length > 0 || spec.exposedRoutes.length > 0 || spec.healthChecks.length > 0);
    setCreateTab("compose");
    setRetryNotice(true);
    setShowCreate(true);
  }

  async function handleRetry(deploymentId: string) {
    if (!accessToken) return;
    setRetryingId(deploymentId);
    setError(null);
    try {
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
    setRepoUrl("");
    setBranch("main");
    setPatToken("");
    setComposeContent("");
    setContext("");
    setInstallPath("");
    setNetworkMode("create");
    setExistingNetworkName("");
    setShowAdvanced(false);
    setEnvFiles([]);
    setRoutes([]);
    setHealthChecks([]);
    setServiceCards([emptyServiceCard()]);
    setInfraSelections([]);
    setGeneratedSpec("");
    setReviewFindings(null);
    setGenerationStatus(null);
    setUnresolvedFields([]);
    setEvidenceRefs([]);
    setGenerationWarnings([]);
    setRetryNotice(false);
  }

  function closeCreate() {
    setShowCreate(false);
    resetCreateForm();
  }

  async function handleCreateFromCompose(e: React.FormEvent) {
    e.preventDefault();
    if (!accessToken || !repoUrl || !branch || !composeContent) return;
    const hasUncheckedCustomSubdomain = routes.some(
      (r, i) => r.customSubdomain && routeSubdomainCheck[i] !== "available"
    );
    if (hasUncheckedCustomSubdomain) return;
    setSubmitting(true);
    setError(null);
    try {
      const deployment = await api.ops.deployments.create(accessToken, vmId, {
        repoUrl,
        branch,
        patToken: patToken || undefined,
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
    if (!accessToken || !repoUrl || !branch) return;
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
        patToken: patToken || undefined,
        services: serviceCards.filter((s) => s.name && s.runtime && s.context),
        infrastructure: infraSelections.filter((i) => i.type),
        existingNetworkName: networkMode === "reuse" ? existingNetworkName || undefined : undefined,
      });
      setGenerationStatus(result.status);
      setEvidenceRefs(result.evidenceRefs);
      setGenerationWarnings(result.warnings);
      setUnresolvedFields(result.unresolved);
      setGeneratedSpec(result.status === "READY" && result.spec ? JSON.stringify(result.spec, null, 2) : "");
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 스펙 생성에 실패했습니다.");
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

  async function handleCreateFromSpec(e: React.FormEvent) {
    e.preventDefault();
    if (!accessToken || !repoUrl || !branch || !generatedSpec) return;
    setSubmitting(true);
    setError(null);
    try {
      const spec: DeploymentSpec = JSON.parse(generatedSpec);
      const deployment = await api.ops.deployments.createFromSpec(accessToken, vmId, {
        repoUrl,
        branch,
        patToken: patToken || undefined,
        spec,
        installPath: installPath.trim() || undefined,
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
          <button onClick={() => setShowCreate(true)} className="flex h-10 shrink-0 items-center gap-1.5 whitespace-nowrap px-3.5 text-sm font-bold text-brand-strong transition-colors hover:bg-white/[0.06]">
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

      {error && (
        <div className="bg-danger/10 border border-danger-soft text-danger px-4 py-3 rounded-md mb-3 text-sm flex items-center justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-danger/60 hover:text-danger">✕</button>
        </div>
      )}

      <div className="flex-1 rounded-panel border border-line overflow-auto">
        {loading ? (
          <PageLoader label="불러오는 중" />
        ) : deployments.length === 0 ? (
          <p className="text-sm text-muted-soft text-center py-16">배포 이력이 없습니다</p>
        ) : (
          <Table>
            <thead className="sticky top-0">
              <tr>
                <Th>생성일시</Th>
                <Th>방식</Th>
                <Th>리비전</Th>
                <Th>상태</Th>
                <Th className="w-48">작업</Th>
              </tr>
            </thead>
            <tbody>
              {deployments.map((d) => (
                <tr key={d.id} className="hover:bg-white/[0.03]">
                  <Td className="text-xs text-muted-soft">{formatDate(d.createdAt)}</Td>
                  <Td>{SOURCE_TYPE_LABEL[d.sourceType] ?? d.sourceType}</Td>
                  <Td className="text-muted-soft font-mono text-xs">{d.sourceRevision?.slice(0, 10) ?? "—"}</Td>
                  <Td>
                    <StatusBadge tone={STATUS_TONE[d.status] ?? "off"}>{d.status}</StatusBadge>
                  </Td>
                  <Td>
                    <div className="flex items-center gap-3">
                      <button
                        onClick={() => router.push(`/instances/${vmId}/deployments/${d.id}`)}
                        className="text-xs text-brand-strong hover:underline font-bold"
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
              ))}
            </tbody>
          </Table>
        )}
      </div>

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

          {/* 본문 (스크롤 영역) */}
          <div className="flex-1 overflow-y-auto p-6">
            <div className="mx-auto max-w-[820px] space-y-4">
              {retryNotice && (
                <div className="rounded-md border border-[#e8b657]/25 bg-[#e8b657]/[0.06] px-3 py-2.5 text-xs text-[#e8b657]">
                  이전 배포의 compose 내용을 불러왔습니다. Git 저장소 URL/브랜치/PAT는 보안상 저장되지 않아 다시 입력해야 합니다. 필요하면 내용을 수정한 뒤 배포를 시작하세요.
                </div>
              )}

              {/* 배포 방식 선택 */}
              <div>
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
              </div>

              {/* 공통: 저장소 연결 */}
              <Section title="1. 저장소 연결" description="배포할 Git 저장소와 브랜치를 입력하세요. PAT는 저장되지 않고 이번 배포에만 사용됩니다.">
                <div className="grid grid-cols-2 gap-3">
                  <Field label="Git 저장소 URL" htmlFor="deploy-repo-url">
                    <Input
                      id="deploy-repo-url"
                      name="deploy-repo-url"
                      value={repoUrl}
                      onChange={(e) => setRepoUrl(e.target.value)}
                      placeholder="https://github.com/user/repo.git"
                    />
                  </Field>
                  <Field label="브랜치" htmlFor="deploy-branch">
                    <Input id="deploy-branch" name="deploy-branch" value={branch} onChange={(e) => setBranch(e.target.value)} />
                  </Field>
                  <Field label="PAT (비공개 저장소인 경우)" htmlFor="deploy-pat" className="col-span-2">
                    <Input
                      id="deploy-pat"
                      name="deploy-pat"
                      type="password"
                      value={patToken}
                      onChange={(e) => setPatToken(e.target.value)}
                      placeholder="공개 저장소면 비워두세요"
                    />
                  </Field>
                  {createTab === "compose" && (
                    <Field label="배포 디렉토리 (선택)" htmlFor="deploy-context" className="col-span-2">
                      <Input
                        id="deploy-context"
                        name="deploy-context"
                        value={context}
                        onChange={(e) => setContext(e.target.value)}
                        placeholder="예: backend (비워두면 저장소 루트에서 배포)"
                      />
                      <p className="mt-1 text-[11px] font-normal normal-case text-muted-soft">
                        모노레포에서 특정 폴더만 배포하고 싶을 때 입력하세요. Compose 파일 업로드와 이미지 빌드가 이 디렉토리를 기준으로 실행됩니다. (저장소 내부 경로)
                      </p>
                    </Field>
                  )}
                  <Field label="VM 배포 경로 (선택)" htmlFor="deploy-install-path" className="col-span-2">
                    <Input
                      id="deploy-install-path"
                      name="deploy-install-path"
                      value={installPath}
                      onChange={(e) => setInstallPath(e.target.value)}
                      placeholder="예: /home/ubuntu/myapp (비워두면 gamjabox가 자동 관리하는 경로만 사용)"
                    />
                    <p className="mt-1 text-[11px] font-normal normal-case text-muted-soft">
                      VM 내부의 이 경로에 현재 배포를 가리키는 심볼릭 링크를 만듭니다. SSH로 직접 접속했을 때 익숙한 위치에서 배포 결과물을 찾을 수 있어요. (VM 파일시스템 절대경로)
                    </p>
                  </Field>
                </div>
              </Section>

              {createTab === "compose" ? (
                <form id="deploy-compose-form" onSubmit={handleCreateFromCompose}>
                  <Section title="2. Compose 작성" description="저장소(또는 위에서 지정한 디렉토리)에서 실행할 서비스를 docker-compose.yaml 형식으로 작성하세요.">
                    <Field label="docker-compose.yaml" htmlFor="deploy-compose">
                      <Textarea
                        id="deploy-compose"
                        name="deploy-compose"
                        value={composeContent}
                        onChange={(e) => setComposeContent(e.target.value)}
                        rows={10}
                        spellCheck={false}
                        placeholder={"services:\n  web:\n    build: .\n    ports:\n      - \"3000:3000\""}
                        className="font-mono resize-none"
                      />
                    </Field>

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
                          <p className="mb-1.5 text-[11px] text-muted-soft">외부에서 접근 가능하게 노출할 서비스 포트를 지정합니다.</p>
                          {routes.map((r, i) => {
                            const check = routeSubdomainCheck[i] ?? "idle";
                            const isPro = planType === "PRO";
                            return (
                              <div key={i} className="mb-2">
                                <div className="grid grid-cols-6 gap-1.5 items-center">
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
                                <div className="mt-1.5 rounded border border-line-strong bg-white/[0.02] p-1.5">
                                  <div className="mb-1 flex items-center gap-1.5">
                                    <span className="text-[10px] font-bold text-muted">커스텀 서브도메인</span>
                                    {!isPro ? (
                                      <span className="shrink-0 text-[10px] font-bold text-[#e8b657] bg-[#e8b657]/10 border border-[#e8b657]/25 px-1.5 py-0.5 rounded">PRO 전용</span>
                                    ) : (
                                      <span className="text-[10px] text-accent">PRO</span>
                                    )}
                                  </div>
                                  <div className={cn("flex items-center gap-1.5", !isPro && "opacity-50")}>
                                    <input
                                      value={r.customSubdomain ?? ""}
                                      onChange={(e) => handleRouteCustomSubdomainChange(i, e.target.value)}
                                      placeholder="예: myservice (선착순 점유, 미입력 시 자동 생성)"
                                      disabled={!isPro}
                                      className="h-7 flex-1 px-2 border border-line-strong rounded text-xs disabled:pointer-events-none disabled:bg-white/[0.03]"
                                    />
                                    {isPro && r.customSubdomain && (
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
                                  {!r.customSubdomain && (
                                    <p className="mt-1 text-[10px] text-muted-soft">
                                      미입력 시 자동 생성됩니다{r.nickname && ` (${r.nickname} 닉네임 기준)`}.
                                    </p>
                                  )}
                                </div>
                              </div>
                            );
                          })}
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
                </form>
              ) : (
                <>
                  <Section title="2. 서비스 힌트 (선택)" description="AI가 저장소를 자동으로 분석하지만, 감지가 어려운 서비스가 있다면 힌트를 입력해 정확도를 높일 수 있습니다.">
                    <div className="mb-4">
                      <div className="flex items-center justify-between mb-1.5">
                        <p className="text-xs font-bold text-foreground">서비스</p>
                        <button type="button" onClick={() => setServiceCards((prev) => [...prev, emptyServiceCard()])} className="text-xs text-brand-strong font-bold">+ 서비스 추가</button>
                      </div>
                      {serviceCards.map((s, i) => (
                        <div key={i} className="rounded-md border border-line p-3 mb-2 space-y-2">
                          <div className="grid grid-cols-3 gap-2">
                            <input value={s.name} onChange={(e) => setServiceCards((prev) => prev.map((x, xi) => (xi === i ? { ...x, name: e.target.value } : x)))} placeholder="서비스명" className="h-8 px-2 border border-line-strong rounded text-xs" />
                            <select value={s.runtime} onChange={(e) => setServiceCards((prev) => prev.map((x, xi) => (xi === i ? { ...x, runtime: e.target.value } : x)))} className="h-8 px-2 border border-line-strong rounded text-xs">
                              <option value="docker">Docker (직접 빌드)</option>
                              <option value="java">Java</option>
                              <option value="node">Node.js</option>
                              <option value="python">Python</option>
                            </select>
                            <input value={s.context} onChange={(e) => setServiceCards((prev) => prev.map((x, xi) => (xi === i ? { ...x, context: e.target.value } : x)))} placeholder="경로 (예: .)" className="h-8 px-2 border border-line-strong rounded text-xs" />
                          </div>
                          <div className="grid grid-cols-3 gap-2">
                            <input type="number" value={s.containerPort} onChange={(e) => setServiceCards((prev) => prev.map((x, xi) => (xi === i ? { ...x, containerPort: Number(e.target.value) } : x)))} placeholder="컨테이너 포트" className="h-8 px-2 border border-line-strong rounded text-xs" />
                            <input value={s.buildCommand ?? ""} onChange={(e) => setServiceCards((prev) => prev.map((x, xi) => (xi === i ? { ...x, buildCommand: e.target.value } : x)))} placeholder="빌드 명령 (선택)" className="h-8 px-2 border border-line-strong rounded text-xs" />
                            <input value={s.startCommand ?? ""} onChange={(e) => setServiceCards((prev) => prev.map((x, xi) => (xi === i ? { ...x, startCommand: e.target.value } : x)))} placeholder="시작 명령 (선택)" className="h-8 px-2 border border-line-strong rounded text-xs" />
                          </div>
                          <div className="flex items-center justify-between">
                            <label className="flex items-center gap-1.5 text-xs text-muted">
                              <input type="checkbox" checked={s.expose} onChange={(e) => setServiceCards((prev) => prev.map((x, xi) => (xi === i ? { ...x, expose: e.target.checked } : x)))} className="accent-brand" />
                              외부 노출
                            </label>
                            <button type="button" onClick={() => setServiceCards((prev) => prev.filter((_, xi) => xi !== i))} className="text-xs text-muted-soft hover:text-danger">삭제</button>
                          </div>
                        </div>
                      ))}
                    </div>

                    <div className="mb-4">
                      <div className="flex items-center justify-between mb-1.5">
                        <p className="text-xs font-bold text-foreground">공유 인프라</p>
                        <button type="button" onClick={() => setInfraSelections((prev) => [...prev, emptyInfra()])} className="text-xs text-brand-strong font-bold">+ 추가</button>
                      </div>
                      <p className="mb-1.5 text-[11px] text-muted-soft">서비스가 함께 사용할 DB/캐시 등 공유 인프라를 지정합니다.</p>
                      {infraSelections.map((inf, i) => (
                        <div key={i} className="flex gap-2 mb-2">
                          <select value={inf.type} onChange={(e) => setInfraSelections((prev) => prev.map((x, xi) => (xi === i ? { ...x, type: e.target.value } : x)))} className="h-8 px-2 border border-line-strong rounded text-xs">
                            <option value="postgres">PostgreSQL</option>
                            <option value="mysql">MySQL</option>
                            <option value="redis">Redis</option>
                            <option value="mongodb">MongoDB</option>
                          </select>
                          <input value={inf.version ?? ""} onChange={(e) => setInfraSelections((prev) => prev.map((x, xi) => (xi === i ? { ...x, version: e.target.value } : x)))} placeholder="버전 (선택, 비우면 AI가 선택)" className="flex-1 h-8 px-2 border border-line-strong rounded text-xs" />
                          <button type="button" onClick={() => setInfraSelections((prev) => prev.filter((_, xi) => xi !== i))} className="text-muted-soft hover:text-danger">✕</button>
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
                            onChange={() => setNetworkMode("create")}
                            className="accent-brand"
                          />
                          새 네트워크 생성
                        </label>
                        <label className="flex items-center gap-2 text-xs text-foreground">
                          <input
                            type="radio"
                            name="deploy-network-mode"
                            checked={networkMode === "reuse"}
                            onChange={() => setNetworkMode("reuse")}
                            disabled={dockerNetworks.length === 0}
                            className="accent-brand"
                          />
                          기존 네트워크 재사용
                        </label>
                        {networkMode === "reuse" && (
                          <Select
                            value={existingNetworkName}
                            onChange={(e) => setExistingNetworkName(e.target.value)}
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

                    <Button
                      type="button"
                      variant="primary"
                      onClick={handleGenerateSpec}
                      disabled={generating || !repoUrl || !branch || serviceCards.every((s) => !s.name) || (networkMode === "reuse" && !existingNetworkName)}
                      title={!repoUrl || !branch ? "위쪽 저장소 연결에 Git 저장소 URL/브랜치를 먼저 입력하세요" : undefined}
                    >
                      {generating ? "저장소 분석 + AI 생성 중..." : "AI 스펙 생성"}
                    </Button>
                  </Section>

                  {/* 결정론적 저장소 분석 결과 — AI 호출 여부와 무관하게 항상 먼저 보여줌 */}
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
                    <Section title="3. 생성된 배포 구성" description="결정론적 규칙으로 확정된 부분과 AI가 확정한 부분이 합쳐진 결과입니다. 검토 후 필요하면 직접 수정할 수 있습니다.">
                      <form id="deploy-spec-form" onSubmit={handleCreateFromSpec} className="space-y-3">
                        <Textarea
                          id="deploy-generated-spec"
                          name="deploy-generated-spec"
                          value={generatedSpec}
                          onChange={(e) => setGeneratedSpec(e.target.value)}
                          rows={12}
                          spellCheck={false}
                          className="font-mono resize-none"
                        />
                        <Button type="button" onClick={handleReviewSpec} disabled={reviewing}>
                          {reviewing ? "AI 검수 중..." : "AI 검수 요청 (선택 — 결과가 배포를 막지 않습니다)"}
                        </Button>
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
                      </form>
                    </Section>
                  )}
                </>
              )}
            </div>
          </div>

          {/* 푸터 */}
          <div className="flex shrink-0 items-center gap-2 border-t border-line bg-panel px-6 py-4">
            <span className="flex-1 text-xs text-muted-soft">
              {createTab === "compose"
                ? "배포를 시작하면 소스 체크아웃부터 헬스체크까지 자동으로 진행되고, 실시간 로그로 확인할 수 있어요."
                : "AI가 생성한 스펙으로 배포하면 위와 동일한 파이프라인으로 진행됩니다."}
            </span>
            <Button onClick={closeCreate}>취소</Button>
            {createTab === "compose" ? (
              <Button
                form="deploy-compose-form"
                type="submit"
                variant="primary"
                disabled={
                  submitting || !repoUrl || !branch || !composeContent ||
                  routes.some((r, i) => r.customSubdomain && routeSubdomainCheck[i] !== "available")
                }
              >
                {submitting ? "배포 시작 중..." : "배포 시작"}
              </Button>
            ) : (
              generatedSpec && (
                <Button form="deploy-spec-form" type="submit" variant="primary" disabled={submitting || !repoUrl || !branch}>
                  {submitting ? "배포 시작 중..." : "이 스펙으로 배포 시작"}
                </Button>
              )
            )}
          </div>
        </div>
      </Modal>
    </div>
  );
}
