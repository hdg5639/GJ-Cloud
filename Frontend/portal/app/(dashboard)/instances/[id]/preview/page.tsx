"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type { PreviewAnalysisResult, PageReviewFinding, PreviewCapability } from "@/lib/types";
import { InstanceSectionNav } from "@/components/ui/instance-section-nav";
import { PageLoader } from "@/components/ui/loader";
import { Field, Input, Select, Textarea } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { PreviewPageRenderer } from "@/components/preview-runtime/PreviewPageRenderer";
import { ApiCallLog } from "@/components/preview-runtime/ApiCallLog";
import type { ApiCallLogEntry } from "@/components/preview-runtime/types";

type Purpose = "API_TEST" | "PRODUCT_LIKE" | "ADMIN";

const PURPOSE_LABEL: Record<Purpose, string> = {
  API_TEST: "API 테스트 페이지",
  PRODUCT_LIKE: "실제 서비스 형태의 테스트 페이지",
  ADMIN: "관리자용 페이지",
};

const CAPABILITY_TYPE_LABEL: Record<string, string> = {
  LIST: "목록",
  DETAIL: "상세",
  CREATE: "생성",
  UPDATE: "수정",
  DELETE: "삭제",
  LOGIN: "로그인",
};

const STATUS_LABEL: Record<string, string> = {
  READY: "생성 준비 완료",
  NEEDS_INPUT: "일부 항목의 신뢰도가 낮습니다",
  UNSUPPORTED: "이 문서로는 생성할 수 없습니다",
};

// Direction Recovery Change Request §17 — FALLBACK_CRUD를 목적 반영 계획인 것처럼 보여주면 안 된다.
const GENERATION_MODE_LABEL: Record<string, string> = {
  SERVICE_AWARE: "AI가 서비스 설명을 반영해 페이지를 구성했습니다",
  RULE_BASED: "생성 목적에 맞춰 페이지 구성 규칙을 적용했습니다",
  FALLBACK_CRUD: "생성 목적을 반영하지 못해 기본 API 테스트 구성으로 대체되었습니다",
};

// GamjaBox_2.0_Key_Features.md 10절 — 신뢰도를 숫자 대신 상태(✓/△/✕)로 보여준다.
const CONFIDENCE_MARK: Record<string, { symbol: string; className: string }> = {
  HIGH: { symbol: "✓", className: "text-brand-strong" },
  MEDIUM: { symbol: "△", className: "text-[#e8b657]" },
  LOW: { symbol: "✕", className: "text-danger" },
};

// auto-preview-design/05-capability-taxonomy.md §5 — SAFE는 표시하지 않고 주의가 필요한 것만 배지로 강조.
const RISK_LABEL: Record<string, { label: string; className: string }> = {
  STATE_CHANGING: { label: "상태변경", className: "bg-white/[0.06] text-muted" },
  DESTRUCTIVE: { label: "파괴적", className: "bg-[#e8b657]/15 text-[#e8b657]" },
  IRREVERSIBLE: { label: "복구불가", className: "bg-danger/15 text-danger" },
  EXTERNAL_SIDE_EFFECT: { label: "외부영향", className: "bg-danger/15 text-danger" },
};

const ACCESS_TOKEN_PATH_FIELD = "auth.login.accessTokenPath";
const AUTH_LOGIN_FIELD = "auth.login";

export default function PreviewWizardPage() {
  const params = useParams();
  const router = useRouter();
  const vmId = params.id as string;
  const { accessToken } = useAuth();

  const [step, setStep] = useState<1 | 2 | 3>(1);

  // 1단계 — 입력
  const [apiDocsUrl, setApiDocsUrl] = useState("");
  const [serviceDescription, setServiceDescription] = useState("");
  const [purpose, setPurpose] = useState<Purpose>("API_TEST");
  const [analyzing, setAnalyzing] = useState(false);
  const [analysisError, setAnalysisError] = useState<string | null>(null);

  // 2단계 — 분석/검수/미리보기
  const [result, setResult] = useState<PreviewAnalysisResult | null>(null);
  const [apiBaseUrl, setApiBaseUrl] = useState("");
  const [reviewing, setReviewing] = useState(false);
  const [reviewFindings, setReviewFindings] = useState<PageReviewFinding[] | null>(null);
  const [planning, setPlanning] = useState(false);
  const [planDecisions, setPlanDecisions] = useState<string[] | null>(null);
  const [previewPageId, setPreviewPageId] = useState<string | null>(null);
  const [previewAuthToken, setPreviewAuthToken] = useState<string | null>(null);
  const [apiCallLog, setApiCallLog] = useState<ApiCallLogEntry[]>([]);
  const [accessTokenPathInput, setAccessTokenPathInput] = useState("");
  const [manualLoginPath, setManualLoginPath] = useState("");
  const [manualLoginUsernameField, setManualLoginUsernameField] = useState("email");
  const [manualLoginPasswordField, setManualLoginPasswordField] = useState("password");
  const [manualLoginAccessTokenPath, setManualLoginAccessTokenPath] = useState("");

  // 3단계 — 배포
  const [targetName, setTargetName] = useState("");
  const [deploying, setDeploying] = useState(false);
  const [deployError, setDeployError] = useState<string | null>(null);

  async function handleAnalyze() {
    if (!accessToken || !apiDocsUrl.trim()) return;
    setAnalyzing(true);
    setAnalysisError(null);
    setResult(null);
    setReviewFindings(null);
    setPlanDecisions(null);
    try {
      const data = await api.ops.preview.analyze(accessToken, {
        apiDocsUrl: apiDocsUrl.trim(),
        serviceDescription: serviceDescription.trim() || undefined,
        purpose,
      });
      setResult(data);
      setApiBaseUrl(data.apiServerUrls[0] ?? "");
      setPreviewPageId(data.pages[0]?.id ?? null);
      setPreviewAuthToken(null);
      setApiCallLog([]);
      setAccessTokenPathInput("");
      setManualLoginPath("");
      setManualLoginUsernameField("email");
      setManualLoginPasswordField("password");
      setManualLoginAccessTokenPath("");
      setStep(2);
    } catch (err) {
      setAnalysisError(err instanceof Error ? err.message : "분석에 실패했습니다.");
    } finally {
      setAnalyzing(false);
    }
  }

  async function handleReview() {
    if (!accessToken || !result) return;
    setReviewing(true);
    try {
      const findings = await api.ops.preview.review(accessToken, {
        serviceDescription: serviceDescription.trim() || undefined,
        capabilities: result.capabilities,
        pages: result.pages,
      });
      setReviewFindings(findings);
    } catch {
      setReviewFindings([]);
    } finally {
      setReviewing(false);
    }
  }

  // Direction Recovery Change Request Increment 3 — handleReview와 달리 이 응답은 실제로 pages를
  // 대체한다(코멘트만 반환하는 게 아님). 실패해도 서버가 항상 유효한 pages를 돌려주므로 여기서
  // catch할 예외는 네트워크 오류 정도뿐 — 그 경우는 기존 result를 그대로 둔다.
  async function handlePlan() {
    if (!accessToken || !result) return;
    setPlanning(true);
    try {
      const planned = await api.ops.preview.plan(accessToken, {
        serviceDescription: serviceDescription.trim() || undefined,
        purpose,
        capabilities: result.capabilities,
        pages: result.pages,
      });
      setResult((prev) => (prev ? { ...prev, pages: planned.pages, generationMode: planned.generationMode } : prev));
      setPreviewPageId(planned.pages[0]?.id ?? null);
      setPlanDecisions(planned.decisions);
    } catch {
      setPlanDecisions([]);
    } finally {
      setPlanning(false);
    }
  }

  async function handleDeploy() {
    if (!accessToken || !result || !targetName.trim() || !apiBaseUrl.trim()) return;
    setDeploying(true);
    setDeployError(null);
    try {
      const deployment = await api.ops.preview.deploy(accessToken, vmId, {
        targetName: targetName.trim(),
        apiBaseUrl: apiBaseUrl.trim(),
        capabilities: result.capabilities,
        pages: result.pages,
        authStrategy: result.authStrategy,
        purpose,
      });
      router.push(`/instances/${vmId}/deployments/${deployment.id}`);
    } catch (err) {
      setDeployError(err instanceof Error ? err.message : "배포에 실패했습니다.");
      setDeploying(false);
    }
  }

  const findCapability = (id: string): PreviewCapability | undefined =>
    result?.capabilities.find((c) => c.id === id);

  // 신뢰도 낮은/확인 못 한 항목을 사용자가 직접 지정하는 보완 UI(§10) — 지금은 access token 위치
  // 하나만 다룬다. 로컬 상태만 바꾸면 review/deploy가 그대로 result.capabilities를 다시 보내므로
  // 서버 호출 없이 이후 단계(라이브 프리뷰 로그인, 배포)에 곧바로 반영된다.
  function handleSetAccessTokenPath() {
    const path = accessTokenPathInput.trim();
    if (!path) return;
    setResult((prev) => {
      if (!prev) return prev;
      const unresolved = prev.unresolved.filter((f) => f.field !== ACCESS_TOKEN_PATH_FIELD);
      return {
        ...prev,
        capabilities: prev.capabilities.map((c) => (c.type === "LOGIN" ? { ...c, accessTokenPath: path } : c)),
        unresolved,
        status: unresolved.length === 0 && prev.status === "NEEDS_INPUT" ? "READY" : prev.status,
      };
    });
  }

  // 자동 탐지가 실패했을 때(AUTH_LOGIN_NOT_FOUND) 사용자가 로그인 API를 직접 등록한다. 서버가 모르는
  // capability라 confidence는 항상 LOW로 표시하고, AUTH_PAGE 스켈레톤이 없으면 하나 만들어 붙인다.
  // access token 위치까지 같이 입력하면 뒤이은 accessTokenPath 보완 단계를 또 거치지 않아도 된다.
  function handleSetManualLogin() {
    const path = manualLoginPath.trim();
    if (!path) return;
    const usernameField = manualLoginUsernameField.trim() || "email";
    const passwordField = manualLoginPasswordField.trim() || "password";
    const accessTokenPath = manualLoginAccessTokenPath.trim() || null;

    setResult((prev) => {
      if (!prev) return prev;
      const loginCapability: PreviewCapability = {
        id: "auth.login",
        resourceName: "auth",
        type: "LOGIN",
        operationId: null,
        path,
        method: "POST",
        hasSearch: false,
        hasSort: false,
        hasPagination: false,
        confidence: "LOW",
        evidence: ["사용자가 직접 지정함"],
        fields: [usernameField, passwordField],
        accessTokenPath,
        searchParam: null,
        risk: "SAFE",
        automationPolicy: "AUTO_SAFE",
        collectionPath: null,
        totalCountPath: null,
        kind: "AUTH",
        action: null,
        dependencies: [],
      };
      const capabilities = [...prev.capabilities.filter((c) => c.type !== "LOGIN"), loginCapability];
      const hasAuthPage = prev.pages.some((p) => p.skeleton === "AUTH_PAGE");
      const pages = hasAuthPage
        ? prev.pages
        : [{ id: "auth-login-manual", title: "로그인", skeleton: "AUTH_PAGE" as const, capabilityIds: ["auth.login"] }, ...prev.pages];

      let unresolved = prev.unresolved.filter((f) => f.field !== AUTH_LOGIN_FIELD);
      if (!accessTokenPath) {
        unresolved = [
          ...unresolved,
          {
            field: ACCESS_TOKEN_PATH_FIELD,
            code: "ACCESS_TOKEN_PATH_UNKNOWN",
            reason: "로그인 응답에서 access token 위치를 확인하지 못했습니다. 아래에서 직접 지정해주세요.",
          },
        ];
      }
      return {
        ...prev,
        capabilities,
        pages,
        unresolved,
        status: unresolved.length === 0 && prev.status === "NEEDS_INPUT" ? "READY" : prev.status,
      };
    });
    setPreviewPageId((prevId) => prevId ?? "auth-login-manual");
  }

  if (!accessToken) return <PageLoader />;

  return (
    <div className="mx-auto max-w-[900px]">
      <InstanceSectionNav vmId={vmId} />

      <header className="mb-5">
        <span className="text-[11px] font-extrabold tracking-[.11em] text-muted-soft">AUTO PREVIEW</span>
        <h1 className="my-[5px] text-[22px] font-extrabold tracking-tight">API 문서로 테스트 페이지 자동 생성</h1>
        <p className="m-0 text-sm text-muted">
          OpenAPI 문서를 분석해 로그인·목록·상세·생성·삭제 화면을 자동으로 만들고 이 VM에 배포합니다.
        </p>
      </header>

      {/* 1단계 */}
      {step === 1 && (
        <section className="rounded-panel border border-line bg-panel p-5">
          <Field label="API Docs URL" htmlFor="preview-docs-url">
            <Input
              id="preview-docs-url"
              value={apiDocsUrl}
              onChange={(e) => setApiDocsUrl(e.target.value)}
              placeholder="https://api.example.com/v3/api-docs"
              required
            />
          </Field>
          <Field label="서비스 설명 (선택)" htmlFor="preview-description" className="mt-4">
            <Textarea
              id="preview-description"
              value={serviceDescription}
              onChange={(e) => setServiceDescription(e.target.value)}
              rows={3}
              placeholder="사용자가 가상머신을 생성하고, 포트를 외부에 노출하며 조직원과 공유하는 서비스입니다."
            />
          </Field>
          <Field label="생성 목적" htmlFor="preview-purpose" className="mt-4">
            <Select id="preview-purpose" value={purpose} onChange={(e) => setPurpose(e.target.value as Purpose)}>
              {Object.entries(PURPOSE_LABEL).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </Field>

          {analysisError && <p className="mt-3 text-xs text-danger">{analysisError}</p>}

          <Button variant="primary" onClick={handleAnalyze} disabled={analyzing || !apiDocsUrl.trim()} className="mt-5">
            {analyzing ? "분석 중..." : "분석"}
          </Button>
        </section>
      )}

      {/* 2단계 */}
      {step === 2 && result && (
        <div className="space-y-4">
          <section className="rounded-panel border border-line bg-panel p-5">
            <div
              className={`mb-4 rounded-md border p-3 text-xs ${
                result.status === "READY"
                  ? "border-brand/25 bg-brand/[0.06] text-brand-strong"
                  : result.status === "UNSUPPORTED"
                    ? "border-danger-soft bg-danger/10 text-danger"
                    : "border-[#e8b657]/25 bg-[#e8b657]/[0.06] text-[#e8b657]"
              }`}
            >
              <p className="font-bold">{STATUS_LABEL[result.status] ?? result.status}</p>
              <p className="mt-1 text-muted">
                {GENERATION_MODE_LABEL[result.generationMode] ?? result.generationMode}
              </p>
              {result.unresolved.length > 0 && (
                <div className="mt-2 space-y-2">
                  {result.unresolved.map((field, i) => (
                    <div key={i}>
                      <p>
                        · [{field.field}] {field.reason}
                      </p>
                      {field.field === ACCESS_TOKEN_PATH_FIELD && (
                        <div className="mt-1.5 flex gap-2">
                          <Input
                            value={accessTokenPathInput}
                            onChange={(e) => setAccessTokenPathInput(e.target.value)}
                            placeholder="예: data.accessToken"
                            className="max-w-[240px]"
                          />
                          <Button type="button" onClick={handleSetAccessTokenPath} disabled={!accessTokenPathInput.trim()}>
                            직접 설정
                          </Button>
                        </div>
                      )}
                      {field.field === AUTH_LOGIN_FIELD && (
                        <div className="mt-1.5 grid grid-cols-2 gap-2 rounded-md border border-line-strong bg-white/[0.02] p-3">
                          <Field label="로그인 API 경로" htmlFor="manual-login-path">
                            <Input
                              id="manual-login-path"
                              value={manualLoginPath}
                              onChange={(e) => setManualLoginPath(e.target.value)}
                              placeholder="/auth/signin"
                            />
                          </Field>
                          <Field label="Access Token 위치 (선택)" htmlFor="manual-login-token-path">
                            <Input
                              id="manual-login-token-path"
                              value={manualLoginAccessTokenPath}
                              onChange={(e) => setManualLoginAccessTokenPath(e.target.value)}
                              placeholder="data.accessToken"
                            />
                          </Field>
                          <Field label="아이디 필드명" htmlFor="manual-login-username">
                            <Input
                              id="manual-login-username"
                              value={manualLoginUsernameField}
                              onChange={(e) => setManualLoginUsernameField(e.target.value)}
                              placeholder="email"
                            />
                          </Field>
                          <Field label="비밀번호 필드명" htmlFor="manual-login-password">
                            <Input
                              id="manual-login-password"
                              value={manualLoginPasswordField}
                              onChange={(e) => setManualLoginPasswordField(e.target.value)}
                              placeholder="password"
                            />
                          </Field>
                          <Button
                            type="button"
                            className="col-span-2"
                            onClick={handleSetManualLogin}
                            disabled={!manualLoginPath.trim()}
                          >
                            로그인 API 직접 지정
                          </Button>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>

            {result.warnings.length > 0 && (
              <div className="mb-4 space-y-1 rounded-md border border-[#e8b657]/25 bg-[#e8b657]/[0.06] p-3 text-xs text-[#e8b657]">
                {result.warnings.map((warning, i) => (
                  <p key={i}>⚠ {warning}</p>
                ))}
              </div>
            )}

            <p className="mb-2 text-xs font-bold text-muted-soft">{result.pages.length}개 페이지 추천됨</p>
            <div className="space-y-2">
              {result.pages.map((page) => (
                <div key={page.id} className="rounded-md border border-line-strong bg-white/[0.02] p-3">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-bold">{page.title}</span>
                    <span className="rounded bg-white/[0.06] px-1.5 py-0.5 text-[10px] font-bold text-muted">{page.skeleton}</span>
                  </div>
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    {page.capabilityIds.map((id) => {
                      const capability = findCapability(id);
                      if (!capability) return null;
                      const mark = CONFIDENCE_MARK[capability.confidence];
                      const risk = RISK_LABEL[capability.risk];
                      return (
                        <span
                          key={id}
                          title={`${capability.method} ${capability.path} (신뢰도: ${capability.confidence}, 위험도: ${capability.risk})`}
                          className="inline-flex items-center gap-1 rounded bg-white/[0.04] px-1.5 py-0.5 text-[10px] text-muted"
                        >
                          {mark && <span className={`font-bold ${mark.className}`}>{mark.symbol}</span>}
                          {capability.type
                            ? CAPABILITY_TYPE_LABEL[capability.type] ?? capability.type
                            : capability.action ?? capability.kind}
                          {risk && <span className={`rounded px-1 py-0.5 font-bold ${risk.className}`}>{risk.label}</span>}
                        </span>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>

            <div className="mt-4">
              <Button type="button" onClick={handleReview} disabled={reviewing}>
                {reviewing ? "AI 검수 중..." : "AI 검수 요청 (선택 — 결과가 생성을 막지 않습니다)"}
              </Button>
              {reviewFindings && (
                <div className="mt-3 space-y-2 rounded-md border border-line bg-white/[0.03] p-3 text-xs">
                  {reviewFindings.length === 0 ? (
                    <p className="text-muted-soft">특이사항이 없습니다.</p>
                  ) : (
                    reviewFindings.map((finding, i) => (
                      <div
                        key={i}
                        className="border-l-2 pl-2"
                        style={{
                          borderColor:
                            finding.severity === "CRITICAL" ? "#ff6b6b" : finding.severity === "WARNING" ? "#e8b657" : "#9aa39a",
                        }}
                      >
                        <p className="font-bold text-foreground">
                          [{finding.severity}] {finding.message}
                        </p>
                        {finding.remediation && <p className="mt-0.5 text-muted">→ {finding.remediation}</p>}
                      </div>
                    ))
                  )}
                </div>
              )}
            </div>

            <div className="mt-3">
              <Button type="button" onClick={handlePlan} disabled={planning || !serviceDescription.trim()}>
                {planning ? "AI로 재구성 중..." : "AI로 서비스에 맞게 페이지 재구성"}
              </Button>
              {!serviceDescription.trim() && (
                <p className="mt-1 text-[11px] text-muted-soft">서비스 설명을 입력해야 사용할 수 있습니다.</p>
              )}
              {planDecisions && (
                <div className="mt-3 space-y-1 rounded-md border border-line bg-white/[0.03] p-3 text-xs">
                  {planDecisions.length === 0 ? (
                    <p className="text-muted-soft">
                      제안할 재구성이 없거나 검증에 실패해 기존 페이지 구성을 그대로 유지했습니다.
                    </p>
                  ) : (
                    planDecisions.map((decision, i) => <p key={i}>· {decision}</p>)
                  )}
                </div>
              )}
            </div>
          </section>

          <section className="rounded-panel border border-line bg-panel p-5">
            <h2 className="mb-1 text-sm font-extrabold">실제 API로 미리 확인</h2>
            <p className="mb-3 text-xs text-muted">
              배포 전에 분석된 API에 직접 연결해 화면이 의도대로 동작하는지 확인할 수 있습니다. 대상 API가 이 브라우저
              origin의 CORS를 허용하지 않으면 요청이 막힐 수 있습니다.
            </p>
            <Field label="API 서버 주소" htmlFor="preview-api-base-url" className="mb-3">
              <Input
                id="preview-api-base-url"
                value={apiBaseUrl}
                onChange={(e) => setApiBaseUrl(e.target.value)}
                placeholder="https://api.example.com"
              />
            </Field>
            {result.pages.length > 0 && (
              <>
                <div className="mb-3 flex flex-wrap gap-2">
                  {result.pages.map((page) => (
                    <button
                      key={page.id}
                      type="button"
                      onClick={() => setPreviewPageId(page.id)}
                      className={`rounded-md border px-3 py-1.5 text-xs font-bold ${
                        previewPageId === page.id ? "border-brand bg-soft text-brand-strong" : "border-line-strong text-muted"
                      }`}
                    >
                      {page.title}
                    </button>
                  ))}
                </div>
                {previewPageId && apiBaseUrl.trim() && (
                  <div className="rounded-md border border-line-strong bg-white/[0.02] p-4">
                    <PreviewPageRenderer
                      page={result.pages.find((p) => p.id === previewPageId)!}
                      capabilities={result.capabilities}
                      config={{
                        apiBaseUrl: apiBaseUrl.trim(),
                        authToken: previewAuthToken,
                        onAuthTokenChange: setPreviewAuthToken,
                        onApiCall: (entry) => setApiCallLog((prev) => [entry, ...prev].slice(0, 30)),
                        authStrategy: result.authStrategy,
                        purpose,
                      }}
                    />
                  </div>
                )}
              </>
            )}
          </section>

          <section className="rounded-panel border border-line bg-panel p-5">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-sm font-extrabold">요청·응답 확인</h2>
              {apiCallLog.length > 0 && (
                <Button type="button" onClick={() => setApiCallLog([])}>
                  기록 지우기
                </Button>
              )}
            </div>
            <p className="mb-3 text-xs text-muted">
              위 미리보기에서 화면을 조작하면 실제로 보낸 요청과 받은 응답이 여기 쌓입니다. 각 항목을 눌러 펼쳐보세요.
            </p>
            <ApiCallLog entries={apiCallLog} />
          </section>

          <div className="flex justify-between">
            <Button onClick={() => setStep(1)}>이전</Button>
            <Button variant="primary" onClick={() => setStep(3)} disabled={result.status === "UNSUPPORTED"}>
              다음
            </Button>
          </div>
        </div>
      )}

      {/* 3단계 */}
      {step === 3 && result && (
        <section className="rounded-panel border border-line bg-panel p-5">
          <Field label="배포 대상 이름" htmlFor="preview-target-name">
            <Input
              id="preview-target-name"
              value={targetName}
              onChange={(e) => setTargetName(e.target.value.slice(0, 60))}
              placeholder="예: my-api-preview"
              required
            />
          </Field>
          <Field label="API 서버 주소" htmlFor="preview-target-api-base-url" className="mt-4">
            <Input
              id="preview-target-api-base-url"
              value={apiBaseUrl}
              onChange={(e) => setApiBaseUrl(e.target.value)}
              placeholder="https://api.example.com"
              required
            />
          </Field>

          <p className="mt-4 text-xs text-muted-soft">
            {result.pages.length}개 페이지, {result.capabilities.length}개 capability로 Vite+React 프로젝트를 생성해 이
            VM에 새 배포 대상으로 추가합니다. 생성 후 자동으로 배포 서브도메인이 발급됩니다.
          </p>

          {deployError && <p className="mt-3 text-xs text-danger">{deployError}</p>}

          <div className="mt-5 flex justify-between">
            <Button onClick={() => setStep(2)} disabled={deploying}>
              이전
            </Button>
            <Button
              variant="primary"
              onClick={handleDeploy}
              disabled={deploying || !targetName.trim() || !apiBaseUrl.trim()}
            >
              {deploying ? "배포 요청 중..." : "배포"}
            </Button>
          </div>
        </section>
      )}
    </div>
  );
}
