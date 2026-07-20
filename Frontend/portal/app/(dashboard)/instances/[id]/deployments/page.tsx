"use client";

import { useCallback, useEffect, useState } from "react";
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
} from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";
import { Modal } from "@/components/ui/modal";
import { Field, Input, Textarea } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { Table, Th, Td } from "@/components/ui/table";
import { StatusBadge } from "@/components/ui/badge";

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

const emptyExposedRoute = (): ExposedRoute => ({ serviceName: "", port: 80, protocol: "HTTP", visibility: "PUBLIC", nickname: "" });
const emptyHealthCheck = (): HealthCheck => ({ serviceName: "", path: "/", hostPort: undefined, containerPort: undefined });
const emptyEnvFile = (): EnvironmentFile => ({ vmPath: ".env", content: "" });
const emptyServiceCard = (): ServiceCard => ({ name: "", runtime: "docker", context: ".", containerPort: 3000, expose: true });
const emptyInfra = (): InfraSelection => ({ type: "postgres", version: "" });

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

  // Raw Compose
  const [composeContent, setComposeContent] = useState("");
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [envFiles, setEnvFiles] = useState<EnvironmentFile[]>([]);
  const [routes, setRoutes] = useState<ExposedRoute[]>([]);
  const [healthChecks, setHealthChecks] = useState<HealthCheck[]>([]);

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

  // 배포 상세 페이지의 "재시도" 버튼에서 넘어온 프리필 스펙을 페이지 진입 시 적용 (모달 상태는
  // 이 목록 페이지에만 있어서 페이지 간 전달에 sessionStorage를 사용)
  useEffect(() => {
    const raw = sessionStorage.getItem(retryStorageKey(vmId));
    if (!raw) return;
    sessionStorage.removeItem(retryStorageKey(vmId));
    try {
      const spec: ComposeSpecResponse = JSON.parse(raw);
      applyComposeSpec(spec);
    } catch {
      // 손상된 데이터는 무시
    }
  }, [vmId]);

  function applyComposeSpec(spec: ComposeSpecResponse) {
    setComposeContent(spec.composeContent);
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

  async function handleCreateFromCompose(e: React.FormEvent) {
    e.preventDefault();
    if (!accessToken || !repoUrl || !branch || !composeContent) return;
    setSubmitting(true);
    setError(null);
    try {
      const deployment = await api.ops.deployments.create(accessToken, vmId, {
        repoUrl,
        branch,
        patToken: patToken || undefined,
        composeContent,
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
    <div className="flex flex-col h-[calc(100vh-120px)]">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-3 min-w-0">
          <button onClick={() => router.back()} className="p-2 hover:bg-[#f2f6f3] rounded-lg transition-colors shrink-0" aria-label="뒤로가기">
            <svg className="w-5 h-5 text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="text-lg font-bold shrink-0">배포</h1>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <button onClick={load} title="새로고침" className="h-8 w-8 flex items-center justify-center border border-line-strong rounded-md hover:bg-[#f2f6f3]">
            <svg className="w-4 h-4 text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
          <Button variant="primary" size="small" onClick={() => setShowCreate(true)}>
            + 새 배포
          </Button>
        </div>
      </div>

      {error && (
        <div className="bg-[#fdf4f4] border border-danger-soft text-danger px-4 py-3 rounded-md mb-3 text-sm flex items-center justify-between">
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
                <tr key={d.id} className="hover:bg-[#fbfdfc]">
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

      {/* 배포 생성 모달 */}
      <Modal open={showCreate} onClose={() => { setShowCreate(false); resetCreateForm(); }}>
        <div className="mx-auto flex max-h-[85vh] w-full max-w-2xl flex-col rounded-panel bg-panel p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-base font-bold">{retryNotice ? "재시도 / 수정 후 재배포" : "새 배포"}</h2>
            <button onClick={() => { setShowCreate(false); resetCreateForm(); }} className="text-muted-soft hover:text-[#3f4c43]">✕</button>
          </div>

          {retryNotice && (
            <div className="bg-[#fffaf0] border border-[#f3dfa8] rounded-md px-3 py-2.5 text-xs text-[#9c6b1f] mb-3 shrink-0">
              이전 배포의 compose 내용을 불러왔습니다. Git 저장소 URL/브랜치/PAT는 보안상 저장되지 않아 다시 입력해야 합니다. 필요하면 내용을 수정한 뒤 배포를 시작하세요.
            </div>
          )}

          <div className="flex gap-1 mb-4 shrink-0">
            <button
              onClick={() => setCreateTab("compose")}
              className={`text-xs px-3 h-7 rounded-md transition-colors ${createTab === "compose" ? "bg-[#445248] text-white" : "bg-panel border border-line-strong text-muted"}`}
            >
              사용자 지정 (Compose)
            </button>
            <button
              onClick={() => setCreateTab("ai")}
              className={`text-xs px-3 h-7 rounded-md transition-colors ${createTab === "ai" ? "bg-[#445248] text-white" : "bg-panel border border-line-strong text-muted"}`}
            >
              AI 자동생성
            </button>
          </div>

          <div className="flex-1 overflow-y-auto pr-1">
            {/* 공통 레포 설정 */}
            <div className="grid grid-cols-2 gap-3 mb-4">
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
            </div>

            {createTab === "compose" ? (
              <form onSubmit={handleCreateFromCompose} className="flex flex-col gap-3">
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
                  className="text-xs text-muted hover:text-[#3f4c43] text-left"
                >
                  고급 설정 (환경변수 파일 · 라우트 노출 · 헬스체크) {showAdvanced ? "▴" : "▾"}
                </button>

                {showAdvanced && (
                  <div className="space-y-4 rounded-md border border-line p-3">
                    {/* 환경변수 파일 */}
                    <div>
                      <div className="flex items-center justify-between mb-1.5">
                        <p className="text-xs font-bold text-[#3f4c43]">환경변수 파일</p>
                        <button type="button" onClick={() => setEnvFiles((prev) => [...prev, emptyEnvFile()])} className="text-xs text-brand-strong font-bold">+ 추가</button>
                      </div>
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
                      <div className="flex items-center justify-between mb-1.5">
                        <p className="text-xs font-bold text-[#3f4c43]">라우트 노출</p>
                        <button type="button" onClick={() => setRoutes((prev) => [...prev, emptyExposedRoute()])} className="text-xs text-brand-strong font-bold">+ 추가</button>
                      </div>
                      {routes.map((r, i) => (
                        <div key={i} className="grid grid-cols-6 gap-1.5 mb-2 items-center">
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
                      <div className="flex items-center justify-between mb-1.5">
                        <p className="text-xs font-bold text-[#3f4c43]">헬스체크</p>
                        <button type="button" onClick={() => setHealthChecks((prev) => [...prev, emptyHealthCheck()])} className="text-xs text-brand-strong font-bold">+ 추가</button>
                      </div>
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

                <Button type="submit" variant="primary" disabled={submitting || !repoUrl || !branch || !composeContent} className="mt-2">
                  {submitting ? "배포 시작 중..." : "배포 시작"}
                </Button>
              </form>
            ) : (
              <div className="flex flex-col gap-3">
                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <p className="text-xs font-bold text-[#3f4c43]">서비스</p>
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

                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <p className="text-xs font-bold text-[#3f4c43]">공유 인프라</p>
                    <button type="button" onClick={() => setInfraSelections((prev) => [...prev, emptyInfra()])} className="text-xs text-brand-strong font-bold">+ 추가</button>
                  </div>
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

                <Button
                  type="button"
                  onClick={handleGenerateSpec}
                  disabled={generating || !repoUrl || !branch || serviceCards.every((s) => !s.name)}
                  title={!repoUrl || !branch ? "위쪽 공통 레포 설정에 Git 저장소 URL/브랜치를 먼저 입력하세요" : undefined}
                >
                  {generating ? "저장소 분석 + AI 생성 중..." : "AI 스펙 생성"}
                </Button>

                {/* 결정론적 저장소 분석 결과 — AI 호출 여부와 무관하게 항상 먼저 보여줌 */}
                {evidenceRefs.length > 0 && (
                  <div className="rounded-md border border-[#bcd6f5] bg-[#eef5fd] p-3 text-xs text-[#2c5a8a] space-y-1">
                    <p className="font-bold">저장소 분석 결과</p>
                    {evidenceRefs.map((ref, i) => {
                      const [context, detectedType, confidence] = ref.split(":");
                      return (
                        <p key={i}>
                          · <span className="font-mono">{context || "."}</span> → {detectedType}
                          {confidence && <span className="text-[#5c8ab8]"> (신뢰도: {confidence})</span>}
                        </p>
                      );
                    })}
                  </div>
                )}

                {generationWarnings.length > 0 && (
                  <div className="rounded-md border border-[#f3dfa8] bg-[#fffaf0] p-3 text-xs text-[#9c6b1f] space-y-1">
                    {generationWarnings.map((w, i) => <p key={i}>⚠ {w}</p>)}
                  </div>
                )}

                {/* 근거 부족/충돌 등으로 확정하지 못한 경우 — 억지로 스펙을 만들어내지 않고 사유를 그대로 보여줌 */}
                {generationStatus && generationStatus !== "READY" && (
                  <div className="rounded-md border border-danger-soft bg-[#fdf4f4] p-3 text-xs text-danger space-y-1.5">
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
                  <>
                    <Field
                      label="생성된 스펙 (검토 후 필요 시 수정 가능 — 결정론적 규칙으로 확정된 부분과 AI 확정 부분이 합쳐져 있습니다)"
                      htmlFor="deploy-generated-spec"
                    >
                      <Textarea
                        id="deploy-generated-spec"
                        name="deploy-generated-spec"
                        value={generatedSpec}
                        onChange={(e) => setGeneratedSpec(e.target.value)}
                        rows={12}
                        spellCheck={false}
                        className="font-mono resize-none"
                      />
                    </Field>
                    <Button type="button" onClick={handleReviewSpec} disabled={reviewing}>
                      {reviewing ? "AI 검수 중..." : "AI 검수 요청 (선택)"}
                    </Button>
                    {reviewFindings && (
                      <div className="rounded-md border border-line bg-[#fbfcfb] p-3 text-xs text-[#3d4941] space-y-2">
                        <p className="text-[11px] text-muted-soft">AI 검수는 참고용이며 배포를 막지 않습니다.</p>
                        {reviewFindings.length === 0 ? (
                          <p className="text-muted-soft">특이사항이 없습니다.</p>
                        ) : (
                          reviewFindings.map((finding, i) => (
                            <div key={i} className="border-l-2 pl-2" style={{
                              borderColor: finding.severity === "CRITICAL" ? "#e34949" : finding.severity === "WARNING" ? "#e0a940" : "#9aa59e",
                            }}>
                              <p className="font-bold text-[#3d4941]">
                                [{finding.severity}] {finding.service && <span className="font-mono">{finding.service}</span>} {finding.message}
                              </p>
                              {finding.remediation && <p className="text-muted mt-0.5">→ {finding.remediation}</p>}
                            </div>
                          ))
                        )}
                      </div>
                    )}
                    <form onSubmit={handleCreateFromSpec}>
                      <Button type="submit" variant="primary" disabled={submitting || !repoUrl || !branch} className="w-full">
                        {submitting ? "배포 시작 중..." : "이 스펙으로 배포 시작"}
                      </Button>
                    </form>
                  </>
                )}
              </div>
            )}
          </div>
        </div>
      </Modal>
    </div>
  );
}
