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
const emptyServiceCard = (): ServiceCard => ({ name: "", runtime: "docker", context: ".", containerPort: 3000, expose: true, customSubdomain: "" });
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
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [togglingTargetId, setTogglingTargetId] = useState<string | null>(null);

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
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [envFiles, setEnvFiles] = useState<EnvironmentFile[]>([]);
  const [routes, setRoutes] = useState<ExposedRoute[]>([]);
  const [healthChecks, setHealthChecks] = useState<HealthCheck[]>([]);
  // PRO 전용 커스텀 CNAME — 라우트별로 가용성 체크 상태를 따로 들고 있어야 함(여러 서비스 노출 가능)
  const [planType, setPlanType] = useState<string | null>(null);
  const [routeSubdomainCheck, setRouteSubdomainCheck] = useState<Record<number, SubdomainCheckStatus>>({});
  const routeSubdomainTimers = useRef<Record<number, ReturnType<typeof setTimeout>>>({});

  // AI 자동생성
  const [serviceCards, setServiceCards] = useState<ServiceCard[]>([emptyServiceCard()]);
  const [serviceSubdomainCheck, setServiceSubdomainCheck] = useState<Record<number, SubdomainCheckStatus>>({});
  const serviceSubdomainTimers = useRef<Record<number, ReturnType<typeof setTimeout>>>({});
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

  const furthestCreateStep = furthestStepByTab[createTab];

  function visitCreateStep(step: CreateStep) {
    if (generating || submitting || reviewing) return;
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

  function handleRepoUrlChange(value: string) {
    setManualRepoUrl(value);
    invalidateAiGeneration();
  }

  function handleBranchChange(value: string) {
    if (repositorySource === "github") {
      setGithubBranch(value);
    } else {
      setManualBranch(value);
    }
    invalidateAiGeneration();
  }

  function handleRepositorySourceChange(source: RepositorySource) {
    if (source === repositorySource) return;
    setRepositorySource(source);
    invalidateAiGeneration();
  }

  function handlePatTokenChange(value: string) {
    setPatToken(value);
    invalidateAiGeneration();
  }

  function handleRepositoryContextChange(value: string) {
    setContext(value);
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
    setServiceSubdomainCheck((prev) => {
      const next: Record<number, SubdomainCheckStatus> = {};
      Object.entries(prev).forEach(([key, value]) => {
        const currentIndex = Number(key);
        if (currentIndex < index) next[currentIndex] = value;
        if (currentIndex > index) next[currentIndex - 1] = value;
      });
      return next;
    });
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

  function handleRouteCustomSubdomainChange(index: number, value: string) {
    const lower = value.toLowerCase().replace(/[^a-z0-9-]/g, "").slice(0, 30);
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

  function handleServiceCustomSubdomainChange(index: number, value: string) {
    const lower = value.toLowerCase().replace(/[^a-z0-9-]/g, "").slice(0, 30);
    setServiceCards((prev) => prev.map((x, xi) => (xi === index ? { ...x, customSubdomain: lower } : x)));
    setServiceSubdomainCheck((prev) => ({ ...prev, [index]: "idle" }));
    invalidateAiGeneration();
    if (serviceSubdomainTimers.current[index]) clearTimeout(serviceSubdomainTimers.current[index]);
    if (!lower || !accessToken) return;
    setServiceSubdomainCheck((prev) => ({ ...prev, [index]: "checking" }));
    serviceSubdomainTimers.current[index] = setTimeout(async () => {
      try {
        const result = await api.vm.checkSubdomain(accessToken, vmId, lower);
        setServiceSubdomainCheck((prev) => ({
          ...prev,
          [index]: result.available ? "available" : ((result.reason as Exclude<SubdomainCheckStatus, "idle" | "checking" | "available">) ?? "taken"),
        }));
      } catch {
        setServiceSubdomainCheck((prev) => ({ ...prev, [index]: "taken" }));
      }
    }, 500);
  }

  function handleServiceExposureChange(index: number, expose: boolean) {
    setServiceCards((prev) => prev.map((x, xi) => (
      xi === index ? { ...x, expose, customSubdomain: expose ? x.customSubdomain : "" } : x
    )));
    setServiceSubdomainCheck((prev) => ({ ...prev, [index]: "idle" }));
    if (serviceSubdomainTimers.current[index]) clearTimeout(serviceSubdomainTimers.current[index]);
    invalidateAiGeneration();
  }

  useEffect(() => () => {
    Object.values(routeSubdomainTimers.current).forEach(clearTimeout);
    Object.values(serviceSubdomainTimers.current).forEach(clearTimeout);
  }, []);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setError(null);
    try {
      const [deploymentResult, targetResult] = await Promise.all([
        api.ops.deployments.list(accessToken, vmId),
        api.ops.deployments.listTargets(accessToken, vmId),
      ]);
      setDeployments(deploymentResult);
      setDeploymentTargets(targetResult);
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
    setInstallPath("");
    setNetworkMode("create");
    setExistingNetworkName("");
    setShowAdvanced(false);
    setEnvFiles([]);
    setRoutes([]);
    setRouteSubdomainCheck({});
    setHealthChecks([]);
    setServiceCards([emptyServiceCard()]);
    setServiceSubdomainCheck({});
    setInfraSelections([]);
    setGeneratedSpec("");
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

  async function handleCreateFromCompose() {
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
    if (serviceCards.some((s, i) => s.expose && s.customSubdomain && serviceSubdomainCheck[i] !== "available")) return false;
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
            context: joinRepositoryContext(context, service.context),
          })),
        infrastructure: infraSelections.filter((i) => i.type),
        existingNetworkName: networkMode === "reuse" ? existingNetworkName || undefined : undefined,
      });
      setGenerationStatus(result.status);
      setEvidenceRefs(result.evidenceRefs);
      setGenerationWarnings(result.warnings);
      setUnresolvedFields(result.unresolved);
      setGeneratedSpec(result.status === "READY" && result.spec ? JSON.stringify(result.spec, null, 2) : "");
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

  async function handleCreateFromSpec() {
    if (!accessToken || !repoUrl || !branch || !generatedSpec) return;
    if (serviceCards.some((s, i) => s.expose && s.customSubdomain && serviceSubdomainCheck[i] !== "available")) return;
    setSubmitting(true);
    setError(null);
    try {
      const spec: DeploymentSpec = JSON.parse(generatedSpec);
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
  const aiHintsStepReady = serviceCards.some((service) => (
    service.name.trim() && service.runtime.trim() && service.context.trim()
  )) && (networkMode === "create" || Boolean(existingNetworkName));
  const aiSubdomainsReady = !serviceCards.some((service, index) => (
    service.expose && service.customSubdomain && serviceSubdomainCheck[index] !== "available"
  ));

  async function handleNextCreateStep() {
    if (createStep === 1) {
      advanceCreateStep(2);
      return;
    }
    if (createStep === 2 && repositoryStepReady) {
      advanceCreateStep(3);
      return;
    }
    if (createStep === 3 && createTab === "compose" && composeStepReady) {
      advanceCreateStep(4);
      return;
    }
    if (createStep === 3 && createTab === "ai" && aiHintsStepReady && aiSubdomainsReady) {
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
                  {deploymentTargets.map((target) => (
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
                        <button
                          type="button"
                          onClick={() => handleAutoDeployToggle(target)}
                          disabled={togglingTargetId === target.id || !target.repositoryFullName}
                          className={cn(
                            "shrink-0 rounded-full border px-2.5 py-1 text-[11px] font-bold transition-colors disabled:cursor-not-allowed disabled:opacity-45",
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
                    </article>
                  ))}
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
                      <div className="flex items-end gap-2">
                        <Field label="GitHub 저장소" htmlFor="deploy-github-repository" className="mb-0 min-w-0 flex-1">
                          <Select
                            id="deploy-github-repository"
                            value={selectedGithubRepositoryKey}
                            onChange={(e) => handleGithubRepositoryChange(e.target.value)}
                            disabled={githubLoading}
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
                        </Field>
                        <Button
                          type="button"
                          onClick={handleConnectGithub}
                          disabled={githubConnecting}
                          className="min-h-[42px] shrink-0"
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
                <div className="wizard-step-enter">
                  <Section title="Compose 작성" description="저장소(또는 위에서 지정한 디렉토리)에서 실행할 서비스를 docker-compose.yaml 형식으로 작성하세요.">
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
                                <div className={cn(
                                  "mt-1.5 rounded border p-2",
                                  isPro ? "border-line-strong bg-white/[0.02]" : "border-[#e8b657]/25 bg-[#e8b657]/[0.045]"
                                )}>
                                  <div className="mb-1 flex items-center gap-1.5">
                                    <span className="text-[10px] font-bold text-muted">커스텀 서브도메인</span>
                                    {!isPro ? (
                                      <span className="shrink-0 rounded border border-[#e8b657]/30 bg-[#e8b657]/10 px-1.5 py-0.5 text-[10px] font-bold text-[#e8b657]">PRO에서 잠금 해제</span>
                                    ) : (
                                      <span className="text-[10px] text-accent">PRO</span>
                                    )}
                                  </div>
                                  <div className="flex items-center gap-1.5">
                                    <input
                                      value={r.customSubdomain ?? ""}
                                      onChange={(e) => handleRouteCustomSubdomainChange(i, e.target.value)}
                                      placeholder="예: myservice (선착순 점유, 미입력 시 자동 생성)"
                                      disabled={!isPro}
                                      title={!isPro ? "PRO 플랜에서 자동 식별자 없는 커스텀 CNAME을 사용할 수 있습니다." : undefined}
                                      className="h-7 flex-1 rounded border border-line-strong px-2 text-xs disabled:cursor-not-allowed disabled:bg-black/10 disabled:text-muted-soft"
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
                                      {!isPro
                                        ? "PRO 플랜에서는 자동 식별자 없이 원하는 주소를 선점할 수 있습니다."
                                        : `미입력 시 자동 생성됩니다${r.nickname ? ` (${r.nickname} 닉네임 기준)` : ""}.`}
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
                      </div>
                    </Section>
                  </div>
                ) : null
              ) : (
                <>
                  {createStep === 3 && <div className="wizard-step-enter">
                  <Section title="서비스 힌트" description="분석할 서비스의 위치와 실행 환경을 알려주세요. 저장소 배포 디렉토리를 입력했다면 아래 경로는 그 디렉토리를 기준으로 계산됩니다.">
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
                            </select>
                            <input value={s.context} onChange={(e) => updateServiceCard(i, { context: e.target.value })} placeholder={context.trim() ? "기준 디렉토리 내부 경로 (예: api)" : "저장소 내부 경로 (예: .)"} className="h-8 px-2 border border-line-strong rounded text-xs" />
                          </div>
                          {context.trim() && (
                            <p className="text-[10px] text-muted-soft">
                              실제 분석·빌드 경로: <span className="font-mono text-muted">{joinRepositoryContext(context, s.context)}</span>
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
                          {s.expose && (() => {
                            const isPro = planType === "PRO";
                            const check = serviceSubdomainCheck[i] ?? "idle";
                            return (
                              <div className={cn(
                                "rounded-md border p-2.5",
                                isPro ? "border-line-strong bg-white/[0.02]" : "border-[#e8b657]/25 bg-[#e8b657]/[0.045]"
                              )}>
                                <div className="mb-1.5 flex items-center gap-1.5">
                                  <span className="text-[11px] font-bold text-foreground">커스텀 CNAME</span>
                                  {!isPro ? (
                                    <span className="rounded border border-[#e8b657]/30 bg-[#e8b657]/10 px-1.5 py-0.5 text-[10px] font-bold text-[#e8b657]">PRO에서 잠금 해제</span>
                                  ) : (
                                    <span className="text-[10px] text-accent">PRO</span>
                                  )}
                                </div>
                                <div className="flex items-center gap-1.5">
                                  <div className={cn(
                                    "flex h-8 flex-1 items-center overflow-hidden rounded border border-line-strong",
                                    isPro ? "bg-background" : "bg-black/10"
                                  )}>
                                    <input
                                      value={s.customSubdomain ?? ""}
                                      onChange={(e) => handleServiceCustomSubdomainChange(i, e.target.value)}
                                      placeholder="예: portfolio"
                                      maxLength={30}
                                      pattern="^[a-z0-9]+(-[a-z0-9]+)*$"
                                      disabled={!isPro}
                                      title={!isPro ? "PRO 플랜에서 자동 식별자 없는 커스텀 CNAME을 사용할 수 있습니다." : undefined}
                                      className="h-full min-w-0 flex-1 bg-transparent px-2 text-xs outline-none disabled:cursor-not-allowed disabled:text-muted-soft"
                                    />
                                    <span className="shrink-0 border-l border-line-strong px-2 text-[11px] text-muted-soft">.gamjabox.cloud</span>
                                  </div>
                                  {isPro && s.customSubdomain && (
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
                                <p className={cn("mt-1.5 text-[10px]", isPro ? "text-muted-soft" : "font-medium text-[#e8b657]")}>
                                  {isPro
                                    ? "비워두면 VM 식별자가 포함된 주소가 자동 생성됩니다. 입력하면 접미사 없는 주소를 사용합니다."
                                    : "PRO 플랜에서는 portfolio.gamjabox.cloud처럼 자동 식별자 없는 주소를 선점할 수 있습니다."}
                                </p>
                              </div>
                            );
                          })()}
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
                            setReviewFindings(null);
                          }}
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
            <Button onClick={closeCreate}>취소</Button>
            {createStep > 1 && (
              <Button type="button" onClick={handlePreviousCreateStep} disabled={submitting || generating || reviewing}>
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
                  (createStep === 2 && !repositoryStepReady) ||
                  (createStep === 3 && createTab === "compose" && (
                    !composeStepReady ||
                    routes.some((route, index) => route.customSubdomain && routeSubdomainCheck[index] !== "available")
                  )) ||
                  (createStep === 3 && createTab === "ai" && (!aiHintsStepReady || !aiSubdomainsReady))
                }
              >
                {createStep === 1 && "저장소 설정"}
                {createStep === 2 && (createTab === "ai" ? "서비스 힌트 입력" : "Compose 작성")}
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
                  submitting || !repositoryStepReady || !composeStepReady ||
                  routes.some((route, index) => route.customSubdomain && routeSubdomainCheck[index] !== "available")
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
                    submitting || !repositoryStepReady || !aiSubdomainsReady
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
