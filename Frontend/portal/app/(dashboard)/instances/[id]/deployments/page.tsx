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
} from "@/lib/types";
import { PageLoader } from "@/components/ui/loader";

const STATUS_STYLE: Record<string, string> = {
  QUEUED: "bg-gray-100 text-gray-600",
  CLONING: "bg-amber-100 text-amber-700",
  UPLOADING: "bg-amber-100 text-amber-700",
  VALIDATING: "bg-amber-100 text-amber-700",
  BUILDING: "bg-amber-100 text-amber-700",
  SWAPPING: "bg-amber-100 text-amber-700",
  HEALTH_CHECKING: "bg-amber-100 text-amber-700",
  ROUTING: "bg-amber-100 text-amber-700",
  SUCCEEDED: "bg-[#03C75A]/10 text-[#03C75A]",
  FAILED: "bg-red-100 text-red-700",
  ROLLING_BACK: "bg-red-100 text-red-700",
  ROLLED_BACK: "bg-gray-100 text-gray-600",
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
  const [reviewComments, setReviewComments] = useState<string[] | null>(null);
  const [reviewing, setReviewing] = useState(false);

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
    setReviewComments(null);
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
    if (!accessToken) return;
    setGenerating(true);
    setError(null);
    setReviewComments(null);
    try {
      const spec = await api.ops.deployments.generateSpec(accessToken, vmId, {
        services: serviceCards.filter((s) => s.name && s.runtime && s.context),
        infrastructure: infraSelections.filter((i) => i.type),
      });
      setGeneratedSpec(JSON.stringify(spec, null, 2));
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
      const comments = await api.ops.deployments.reviewSpec(accessToken, vmId, spec);
      setReviewComments(comments);
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
          <button onClick={() => router.back()} className="p-2 hover:bg-gray-100 rounded-lg transition-colors shrink-0" aria-label="뒤로가기">
            <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="text-lg font-medium text-gray-900 shrink-0">배포</h1>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <button onClick={load} title="새로고침" className="h-8 w-8 flex items-center justify-center border border-gray-300 rounded-md hover:bg-gray-50">
            <svg className="w-4 h-4 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
          <button onClick={() => setShowCreate(true)} className="text-sm px-3.5 h-8 bg-[#03C75A] text-white rounded-md hover:opacity-90">
            + 새 배포
          </button>
        </div>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md mb-3 text-sm flex items-center justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-red-400 hover:text-red-600">✕</button>
        </div>
      )}

      <div className="flex-1 border border-gray-200 rounded-lg overflow-auto">
        {loading ? (
          <PageLoader label="불러오는 중" />
        ) : deployments.length === 0 ? (
          <p className="text-sm text-gray-400 text-center py-16">배포 이력이 없습니다</p>
        ) : (
          <table className="w-full text-sm">
            <thead className="sticky top-0 bg-gray-50 border-b border-gray-200 text-left text-xs text-gray-500">
              <tr>
                <th className="px-4 py-2 font-medium">생성일시</th>
                <th className="px-4 py-2 font-medium">방식</th>
                <th className="px-4 py-2 font-medium">리비전</th>
                <th className="px-4 py-2 font-medium">상태</th>
                <th className="px-4 py-2 font-medium w-48">작업</th>
              </tr>
            </thead>
            <tbody>
              {deployments.map((d) => (
                <tr key={d.id} className="border-b border-gray-100 hover:bg-gray-50">
                  <td className="px-4 py-2 text-gray-500 text-xs">{formatDate(d.createdAt)}</td>
                  <td className="px-4 py-2 text-gray-700">{SOURCE_TYPE_LABEL[d.sourceType] ?? d.sourceType}</td>
                  <td className="px-4 py-2 text-gray-500 font-mono text-xs">{d.sourceRevision?.slice(0, 10) ?? "—"}</td>
                  <td className="px-4 py-2">
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-md ${STATUS_STYLE[d.status] ?? "bg-gray-100 text-gray-600"}`}>
                      {d.status}
                    </span>
                  </td>
                  <td className="px-4 py-2">
                    <div className="flex items-center gap-3">
                      <button
                        onClick={() => router.push(`/instances/${vmId}/deployments/${d.id}`)}
                        className="text-xs text-[#03C75A] hover:underline"
                      >
                        보기
                      </button>
                      {d.status === "FAILED" && (
                        <button
                          onClick={() => handleRetry(d.id)}
                          disabled={retryingId === d.id}
                          className="text-xs text-gray-500 hover:underline disabled:opacity-50"
                        >
                          {retryingId === d.id ? "불러오는 중..." : "재시도"}
                        </button>
                      )}
                      {d.status === "SUCCEEDED" && (
                        <button
                          onClick={() => handleRollback(d.id)}
                          disabled={rollingBackId === d.id}
                          className="text-xs text-gray-500 hover:underline disabled:opacity-50"
                        >
                          {rollingBackId === d.id ? "롤백 요청 중..." : "롤백"}
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* 배포 생성 모달 */}
      {showCreate && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-6">
          <div className="bg-white rounded-xl p-6 w-full max-w-2xl max-h-[85vh] flex flex-col">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-base font-medium text-gray-900">{retryNotice ? "재시도 / 수정 후 재배포" : "새 배포"}</h2>
              <button onClick={() => { setShowCreate(false); resetCreateForm(); }} className="text-gray-400 hover:text-gray-700">✕</button>
            </div>

            {retryNotice && (
              <div className="bg-amber-50 border border-amber-200 rounded-md px-3 py-2.5 text-xs text-amber-700 mb-3 shrink-0">
                이전 배포의 compose 내용을 불러왔습니다. Git 저장소 URL/브랜치/PAT는 보안상 저장되지 않아 다시 입력해야 합니다. 필요하면 내용을 수정한 뒤 배포를 시작하세요.
              </div>
            )}

            <div className="flex gap-1 mb-4 shrink-0">
              <button
                onClick={() => setCreateTab("compose")}
                className={`text-xs px-3 h-7 rounded-md transition-colors ${createTab === "compose" ? "bg-gray-900 text-white" : "bg-white border border-gray-200 text-gray-500"}`}
              >
                사용자 지정 (Compose)
              </button>
              <button
                onClick={() => setCreateTab("ai")}
                className={`text-xs px-3 h-7 rounded-md transition-colors ${createTab === "ai" ? "bg-gray-900 text-white" : "bg-white border border-gray-200 text-gray-500"}`}
              >
                AI 자동생성
              </button>
            </div>

            <div className="flex-1 overflow-y-auto pr-1">
              {/* 공통 레포 설정 */}
              <div className="grid grid-cols-2 gap-3 mb-4">
                <div>
                  <label htmlFor="deploy-repo-url" className="text-xs text-gray-500 block mb-1">Git 저장소 URL</label>
                  <input
                    id="deploy-repo-url"
                    name="deploy-repo-url"
                    value={repoUrl}
                    onChange={(e) => setRepoUrl(e.target.value)}
                    placeholder="https://github.com/user/repo.git"
                    className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:border-[#03C75A]"
                  />
                </div>
                <div>
                  <label htmlFor="deploy-branch" className="text-xs text-gray-500 block mb-1">브랜치</label>
                  <input
                    id="deploy-branch"
                    name="deploy-branch"
                    value={branch}
                    onChange={(e) => setBranch(e.target.value)}
                    className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:border-[#03C75A]"
                  />
                </div>
                <div className="col-span-2">
                  <label htmlFor="deploy-pat" className="text-xs text-gray-500 block mb-1">PAT (비공개 저장소인 경우)</label>
                  <input
                    id="deploy-pat"
                    name="deploy-pat"
                    type="password"
                    value={patToken}
                    onChange={(e) => setPatToken(e.target.value)}
                    placeholder="공개 저장소면 비워두세요"
                    className="w-full h-9 px-3 border border-gray-300 rounded-md text-sm focus:outline-none focus:border-[#03C75A]"
                  />
                </div>
              </div>

              {createTab === "compose" ? (
                <form onSubmit={handleCreateFromCompose} className="flex flex-col gap-3">
                  <div>
                    <label htmlFor="deploy-compose" className="text-xs text-gray-500 block mb-1">docker-compose.yaml</label>
                    <textarea
                      id="deploy-compose"
                      name="deploy-compose"
                      value={composeContent}
                      onChange={(e) => setComposeContent(e.target.value)}
                      rows={10}
                      spellCheck={false}
                      placeholder={"services:\n  web:\n    build: .\n    ports:\n      - \"3000:3000\""}
                      className="w-full font-mono text-xs border border-gray-300 rounded-md p-3 resize-none focus:outline-none focus:border-[#03C75A]"
                    />
                  </div>

                  <button
                    type="button"
                    onClick={() => setShowAdvanced((v) => !v)}
                    className="text-xs text-gray-500 hover:text-gray-700 text-left"
                  >
                    고급 설정 (환경변수 파일 · 라우트 노출 · 헬스체크) {showAdvanced ? "▴" : "▾"}
                  </button>

                  {showAdvanced && (
                    <div className="space-y-4 border border-gray-200 rounded-md p-3">
                      {/* 환경변수 파일 */}
                      <div>
                        <div className="flex items-center justify-between mb-1.5">
                          <p className="text-xs font-medium text-gray-700">환경변수 파일</p>
                          <button type="button" onClick={() => setEnvFiles((prev) => [...prev, emptyEnvFile()])} className="text-xs text-[#03C75A]">+ 추가</button>
                        </div>
                        {envFiles.map((f, i) => (
                          <div key={i} className="flex gap-2 mb-2">
                            <input
                              value={f.vmPath}
                              onChange={(e) => setEnvFiles((prev) => prev.map((x, xi) => (xi === i ? { ...x, vmPath: e.target.value } : x)))}
                              placeholder="경로 (예: .env)"
                              className="w-32 h-8 px-2 border border-gray-300 rounded text-xs shrink-0"
                            />
                            <textarea
                              value={f.content}
                              onChange={(e) => setEnvFiles((prev) => prev.map((x, xi) => (xi === i ? { ...x, content: e.target.value } : x)))}
                              placeholder="KEY=value"
                              rows={2}
                              className="flex-1 px-2 py-1.5 border border-gray-300 rounded text-xs font-mono resize-none"
                            />
                            <button type="button" onClick={() => setEnvFiles((prev) => prev.filter((_, xi) => xi !== i))} className="text-gray-400 hover:text-red-500 self-start mt-1.5">✕</button>
                          </div>
                        ))}
                      </div>

                      {/* 라우트 노출 */}
                      <div>
                        <div className="flex items-center justify-between mb-1.5">
                          <p className="text-xs font-medium text-gray-700">라우트 노출</p>
                          <button type="button" onClick={() => setRoutes((prev) => [...prev, emptyExposedRoute()])} className="text-xs text-[#03C75A]">+ 추가</button>
                        </div>
                        {routes.map((r, i) => (
                          <div key={i} className="grid grid-cols-6 gap-1.5 mb-2 items-center">
                            <input value={r.serviceName} onChange={(e) => setRoutes((prev) => prev.map((x, xi) => (xi === i ? { ...x, serviceName: e.target.value } : x)))} placeholder="서비스명" className="h-8 px-2 border border-gray-300 rounded text-xs col-span-1" />
                            <input type="number" value={r.port} onChange={(e) => setRoutes((prev) => prev.map((x, xi) => (xi === i ? { ...x, port: Number(e.target.value) } : x)))} placeholder="포트" className="h-8 px-2 border border-gray-300 rounded text-xs col-span-1" />
                            <select value={r.protocol} onChange={(e) => setRoutes((prev) => prev.map((x, xi) => (xi === i ? { ...x, protocol: e.target.value } : x)))} className="h-8 px-1 border border-gray-300 rounded text-xs col-span-1">
                              <option value="HTTP">HTTP</option>
                              <option value="TCP">TCP</option>
                            </select>
                            <select value={r.visibility} onChange={(e) => setRoutes((prev) => prev.map((x, xi) => (xi === i ? { ...x, visibility: e.target.value } : x)))} className="h-8 px-1 border border-gray-300 rounded text-xs col-span-1">
                              <option value="PUBLIC">공개</option>
                              <option value="PRIVATE">비공개</option>
                            </select>
                            <input value={r.nickname} onChange={(e) => setRoutes((prev) => prev.map((x, xi) => (xi === i ? { ...x, nickname: e.target.value } : x)))} placeholder="닉네임" className="h-8 px-2 border border-gray-300 rounded text-xs col-span-1" />
                            <button type="button" onClick={() => setRoutes((prev) => prev.filter((_, xi) => xi !== i))} className="text-gray-400 hover:text-red-500 col-span-1">✕</button>
                          </div>
                        ))}
                      </div>

                      {/* 헬스체크 */}
                      <div>
                        <div className="flex items-center justify-between mb-1.5">
                          <p className="text-xs font-medium text-gray-700">헬스체크</p>
                          <button type="button" onClick={() => setHealthChecks((prev) => [...prev, emptyHealthCheck()])} className="text-xs text-[#03C75A]">+ 추가</button>
                        </div>
                        {healthChecks.map((h, i) => (
                          <div key={i} className="grid grid-cols-5 gap-1.5 mb-2 items-center">
                            <input value={h.serviceName} onChange={(e) => setHealthChecks((prev) => prev.map((x, xi) => (xi === i ? { ...x, serviceName: e.target.value } : x)))} placeholder="서비스명" className="h-8 px-2 border border-gray-300 rounded text-xs col-span-1" />
                            <input value={h.path} onChange={(e) => setHealthChecks((prev) => prev.map((x, xi) => (xi === i ? { ...x, path: e.target.value } : x)))} placeholder="경로 (예: /health)" className="h-8 px-2 border border-gray-300 rounded text-xs col-span-1" />
                            <input type="number" value={h.hostPort ?? ""} onChange={(e) => setHealthChecks((prev) => prev.map((x, xi) => (xi === i ? { ...x, hostPort: e.target.value ? Number(e.target.value) : undefined } : x)))} placeholder="호스트포트" className="h-8 px-2 border border-gray-300 rounded text-xs col-span-1" />
                            <input type="number" value={h.containerPort ?? ""} onChange={(e) => setHealthChecks((prev) => prev.map((x, xi) => (xi === i ? { ...x, containerPort: e.target.value ? Number(e.target.value) : undefined } : x)))} placeholder="컨테이너포트" className="h-8 px-2 border border-gray-300 rounded text-xs col-span-1" />
                            <button type="button" onClick={() => setHealthChecks((prev) => prev.filter((_, xi) => xi !== i))} className="text-gray-400 hover:text-red-500 col-span-1">✕</button>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  <button
                    type="submit"
                    disabled={submitting || !repoUrl || !branch || !composeContent}
                    className="h-9 bg-[#03C75A] text-white rounded-md text-sm disabled:opacity-60 mt-2"
                  >
                    {submitting ? "배포 시작 중..." : "배포 시작"}
                  </button>
                </form>
              ) : (
                <div className="flex flex-col gap-3">
                  <div>
                    <div className="flex items-center justify-between mb-1.5">
                      <p className="text-xs font-medium text-gray-700">서비스</p>
                      <button type="button" onClick={() => setServiceCards((prev) => [...prev, emptyServiceCard()])} className="text-xs text-[#03C75A]">+ 서비스 추가</button>
                    </div>
                    {serviceCards.map((s, i) => (
                      <div key={i} className="border border-gray-200 rounded-md p-3 mb-2 space-y-2">
                        <div className="grid grid-cols-3 gap-2">
                          <input value={s.name} onChange={(e) => setServiceCards((prev) => prev.map((x, xi) => (xi === i ? { ...x, name: e.target.value } : x)))} placeholder="서비스명" className="h-8 px-2 border border-gray-300 rounded text-xs" />
                          <select value={s.runtime} onChange={(e) => setServiceCards((prev) => prev.map((x, xi) => (xi === i ? { ...x, runtime: e.target.value } : x)))} className="h-8 px-2 border border-gray-300 rounded text-xs">
                            <option value="docker">Docker (직접 빌드)</option>
                            <option value="java">Java</option>
                            <option value="node">Node.js</option>
                            <option value="python">Python</option>
                          </select>
                          <input value={s.context} onChange={(e) => setServiceCards((prev) => prev.map((x, xi) => (xi === i ? { ...x, context: e.target.value } : x)))} placeholder="경로 (예: .)" className="h-8 px-2 border border-gray-300 rounded text-xs" />
                        </div>
                        <div className="grid grid-cols-3 gap-2">
                          <input type="number" value={s.containerPort} onChange={(e) => setServiceCards((prev) => prev.map((x, xi) => (xi === i ? { ...x, containerPort: Number(e.target.value) } : x)))} placeholder="컨테이너 포트" className="h-8 px-2 border border-gray-300 rounded text-xs" />
                          <input value={s.buildCommand ?? ""} onChange={(e) => setServiceCards((prev) => prev.map((x, xi) => (xi === i ? { ...x, buildCommand: e.target.value } : x)))} placeholder="빌드 명령 (선택)" className="h-8 px-2 border border-gray-300 rounded text-xs" />
                          <input value={s.startCommand ?? ""} onChange={(e) => setServiceCards((prev) => prev.map((x, xi) => (xi === i ? { ...x, startCommand: e.target.value } : x)))} placeholder="시작 명령 (선택)" className="h-8 px-2 border border-gray-300 rounded text-xs" />
                        </div>
                        <div className="flex items-center justify-between">
                          <label className="flex items-center gap-1.5 text-xs text-gray-600">
                            <input type="checkbox" checked={s.expose} onChange={(e) => setServiceCards((prev) => prev.map((x, xi) => (xi === i ? { ...x, expose: e.target.checked } : x)))} />
                            외부 노출
                          </label>
                          <button type="button" onClick={() => setServiceCards((prev) => prev.filter((_, xi) => xi !== i))} className="text-xs text-gray-400 hover:text-red-500">삭제</button>
                        </div>
                      </div>
                    ))}
                  </div>

                  <div>
                    <div className="flex items-center justify-between mb-1.5">
                      <p className="text-xs font-medium text-gray-700">공유 인프라</p>
                      <button type="button" onClick={() => setInfraSelections((prev) => [...prev, emptyInfra()])} className="text-xs text-[#03C75A]">+ 추가</button>
                    </div>
                    {infraSelections.map((inf, i) => (
                      <div key={i} className="flex gap-2 mb-2">
                        <select value={inf.type} onChange={(e) => setInfraSelections((prev) => prev.map((x, xi) => (xi === i ? { ...x, type: e.target.value } : x)))} className="h-8 px-2 border border-gray-300 rounded text-xs">
                          <option value="postgres">PostgreSQL</option>
                          <option value="mysql">MySQL</option>
                          <option value="redis">Redis</option>
                          <option value="mongodb">MongoDB</option>
                        </select>
                        <input value={inf.version ?? ""} onChange={(e) => setInfraSelections((prev) => prev.map((x, xi) => (xi === i ? { ...x, version: e.target.value } : x)))} placeholder="버전 (선택, 비우면 AI가 선택)" className="flex-1 h-8 px-2 border border-gray-300 rounded text-xs" />
                        <button type="button" onClick={() => setInfraSelections((prev) => prev.filter((_, xi) => xi !== i))} className="text-gray-400 hover:text-red-500">✕</button>
                      </div>
                    ))}
                  </div>

                  <button
                    type="button"
                    onClick={handleGenerateSpec}
                    disabled={generating || serviceCards.every((s) => !s.name)}
                    className="h-9 border border-gray-300 rounded-md text-sm disabled:opacity-60"
                  >
                    {generating ? "AI 스펙 생성 중..." : "AI 스펙 생성"}
                  </button>

                  {generatedSpec && (
                    <>
                      <div>
                        <label htmlFor="deploy-generated-spec" className="text-xs text-gray-500 block mb-1">
                          생성된 스펙 (검토 후 필요 시 수정 가능)
                        </label>
                        <textarea
                          id="deploy-generated-spec"
                          name="deploy-generated-spec"
                          value={generatedSpec}
                          onChange={(e) => setGeneratedSpec(e.target.value)}
                          rows={12}
                          spellCheck={false}
                          className="w-full font-mono text-xs border border-gray-300 rounded-md p-3 resize-none focus:outline-none focus:border-[#03C75A]"
                        />
                      </div>
                      <button
                        type="button"
                        onClick={handleReviewSpec}
                        disabled={reviewing}
                        className="h-9 border border-gray-300 rounded-md text-sm disabled:opacity-60"
                      >
                        {reviewing ? "AI 검수 중..." : "AI 검수 요청 (선택)"}
                      </button>
                      {reviewComments && (
                        <div className="bg-gray-50 border border-gray-200 rounded-md p-3 text-xs text-gray-700 space-y-1">
                          {reviewComments.length === 0 ? (
                            <p className="text-gray-400">특이사항이 없습니다.</p>
                          ) : (
                            reviewComments.map((c, i) => <p key={i}>· {c}</p>)
                          )}
                        </div>
                      )}
                      <form onSubmit={handleCreateFromSpec}>
                        <button
                          type="submit"
                          disabled={submitting || !repoUrl || !branch}
                          className="w-full h-9 bg-[#03C75A] text-white rounded-md text-sm disabled:opacity-60"
                        >
                          {submitting ? "배포 시작 중..." : "이 스펙으로 배포 시작"}
                        </button>
                      </form>
                    </>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
