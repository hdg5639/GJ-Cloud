"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type {
  PreviewAnalysisResult,
  PageReviewFinding,
  PreviewCapability,
  PreviewPageDraft,
  PagePlanOperation,
  PagePlanOperationView,
} from "@/lib/types";
import { InstanceSectionNav } from "@/components/ui/instance-section-nav";
import { PageLoader } from "@/components/ui/loader";
import { Field, Input, Select, Textarea } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { PreviewPageRenderer } from "@/components/preview-runtime/PreviewPageRenderer";
import { ApiCallLog } from "@/components/preview-runtime/ApiCallLog";
import type { ApiCallLogEntry } from "@/components/preview-runtime/types";
import type { Block } from "@/components/preview-runtime/blueprint";

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
  // Plan Review UI(Increment 5 2부) — proposing 중엔 AI 호출, proposedOperations는 아직 적용 안 된
  // 제안 목록(체크박스 검토용), selectedOperationIds는 사용자가 체크한 것, applyingPlan/planApplyErrors는
  // 선택한 서브셋을 실제로 적용하는 단계.
  const [proposing, setProposing] = useState(false);
  const [proposedOperations, setProposedOperations] = useState<PagePlanOperationView[] | null>(null);
  const [selectedOperationIds, setSelectedOperationIds] = useState<Set<string>>(new Set());
  const [applyingPlan, setApplyingPlan] = useState(false);
  const [planApplyErrors, setPlanApplyErrors] = useState<string[] | null>(null);
  const [previewPageId, setPreviewPageId] = useState<string | null>(null);
  // Direction Recovery Change Request §13.1 — 라이브 프리뷰가 조립 규칙을 직접 계산하지 않도록,
  // capability/페이지가 바뀔 때마다(analyze/plan 응답 직후 + accessTokenPath 지정·수동 로그인 등록
  // 직후) POST /ops/preview/blocks로 다시 계산받은 결과를 여기 저장해 PreviewPageRenderer에 그대로 넘긴다.
  const [pageBlocks, setPageBlocks] = useState<Record<string, Block[]>>({});
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

  // Direction Recovery Change Request §13.1 — capability/페이지가 바뀔 때마다 이 함수로 Block을
  // 다시 계산받는다. 실패해도 조용히 무시한다(마지막으로 받은 pageBlocks가 그대로 남을 뿐, 분석·검수·
  // 배포 자체를 막지 않음 — AI 검수/재구성이 실패를 안전하게 무시하는 것과 같은 원칙).
  async function refreshBlocks(capabilities: PreviewCapability[], pages: PreviewPageDraft[], forPurpose: Purpose) {
    if (!accessToken) return;
    try {
      const data = await api.ops.preview.blocks(accessToken, { capabilities, pages, purpose: forPurpose });
      setPageBlocks(data.pageBlocks);
    } catch {
      // 무시 — 위 주석 참고.
    }
  }

  async function handleAnalyze() {
    if (!accessToken || !apiDocsUrl.trim()) return;
    setAnalyzing(true);
    setAnalysisError(null);
    setResult(null);
    setReviewFindings(null);
    setProposedOperations(null);
    setSelectedOperationIds(new Set());
    setPlanApplyErrors(null);
    try {
      const data = await api.ops.preview.analyze(accessToken, {
        apiDocsUrl: apiDocsUrl.trim(),
        serviceDescription: serviceDescription.trim() || undefined,
        purpose,
      });
      setResult(data);
      refreshBlocks(data.capabilities, data.pages, purpose);
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

  // Plan Review UI(Increment 5 2부) — handleReview(코멘트만)와 달리 AI가 페이지를 실제로 바꿀 수
  // 있는 오퍼레이션을 제안하지만, 여기서는 아무것도 적용하지 않는다. 유효한(valid) 오퍼레이션은
  // 기본으로 체크해두고, 사용자가 체크박스를 조정한 뒤 handleApplyPlan으로 넘어간다.
  async function handleProposePlan() {
    if (!accessToken || !result) return;
    setProposing(true);
    setPlanApplyErrors(null);
    try {
      const proposal = await api.ops.preview.planPropose(accessToken, {
        serviceDescription: serviceDescription.trim() || undefined,
        purpose,
        capabilities: result.capabilities,
        pages: result.pages,
      });
      setProposedOperations(proposal.operations);
      setSelectedOperationIds(new Set(proposal.operations.filter((op) => op.valid).map((op) => op.id)));
    } catch {
      setProposedOperations([]);
      setSelectedOperationIds(new Set());
    } finally {
      setProposing(false);
    }
  }

  function toggleOperationSelected(id: string) {
    setSelectedOperationIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  // 사용자가 체크한 서브셋만 실제로 적용한다. 실패(all-or-nothing)하면 체크박스는 그대로 두고 에러를
  // 보여줘 사용자가 선택을 조정해 재시도할 수 있게 한다 — 성공하면 review 목록을 닫는다.
  async function handleApplyPlan() {
    if (!accessToken || !result || !proposedOperations) return;
    const selectedOps: PagePlanOperation[] = proposedOperations
      .filter((op) => selectedOperationIds.has(op.id))
      .map((op) => ({
        type: op.type,
        pageId: op.pageId,
        otherPageId: op.otherPageId,
        newTitle: op.newTitle,
        capabilityId: op.capabilityId,
        destinationPageId: op.destinationPageId,
        reason: op.reason,
      }));
    if (selectedOps.length === 0) return;
    setApplyingPlan(true);
    setPlanApplyErrors(null);
    try {
      const applied = await api.ops.preview.planApply(accessToken, {
        capabilities: result.capabilities,
        pages: result.pages,
        operations: selectedOps,
      });
      if (applied.errors.length > 0) {
        setPlanApplyErrors(applied.errors);
        return;
      }
      setResult({ ...result, pages: applied.pages, pagePlans: applied.pagePlans, generationMode: applied.generationMode });
      refreshBlocks(result.capabilities, applied.pages, purpose);
      setPreviewPageId(applied.pages[0]?.id ?? null);
      setProposedOperations(null);
      setSelectedOperationIds(new Set());
    } catch (err) {
      setPlanApplyErrors([err instanceof Error ? err.message : "적용에 실패했습니다."]);
    } finally {
      setApplyingPlan(false);
    }
  }

  // 오퍼레이션의 pageId/capabilityId를 사람이 읽는 제목/이름으로 치환해 요약 문장을 만든다.
  function describeOperation(op: PagePlanOperationView): string {
    const pageTitle = (id: string | null) => result?.pages.find((p) => p.id === id)?.title ?? id ?? "";
    switch (op.type) {
      case "RENAME_PAGE":
        return `"${pageTitle(op.pageId)}" → "${op.newTitle}"로 이름 변경`;
      case "MERGE_PAGES":
        return `"${pageTitle(op.otherPageId)}" 페이지를 "${pageTitle(op.pageId)}" 페이지로 병합`;
      case "MOVE_CAPABILITY": {
        const capability = op.capabilityId ? findCapability(op.capabilityId) : undefined;
        const label = capability
          ? capability.type
            ? (CAPABILITY_TYPE_LABEL[capability.type] ?? capability.type)
            : (capability.action ?? capability.kind)
          : op.capabilityId;
        const resourceName = capability?.resourceName ?? "";
        return `${resourceName} ${label ?? ""} 기능을 "${pageTitle(op.destinationPageId)}" 페이지로 이동`;
      }
      default:
        return op.reason ?? "";
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
    if (!path || !result) return;
    const unresolved = result.unresolved.filter((f) => f.field !== ACCESS_TOKEN_PATH_FIELD);
    const capabilities = result.capabilities.map((c) => (c.type === "LOGIN" ? { ...c, accessTokenPath: path } : c));
    setResult({
      ...result,
      capabilities,
      unresolved,
      status: unresolved.length === 0 && result.status === "NEEDS_INPUT" ? "READY" : result.status,
    });
    refreshBlocks(capabilities, result.pages, purpose);
  }

  // 자동 탐지가 실패했을 때(AUTH_LOGIN_NOT_FOUND) 사용자가 로그인 API를 직접 등록한다. 서버가 모르는
  // capability라 confidence는 항상 LOW로 표시하고, AUTH_PAGE 스켈레톤이 없으면 하나 만들어 붙인다.
  // access token 위치까지 같이 입력하면 뒤이은 accessTokenPath 보완 단계를 또 거치지 않아도 된다.
  function handleSetManualLogin() {
    const path = manualLoginPath.trim();
    if (!path || !result) return;
    const usernameField = manualLoginUsernameField.trim() || "email";
    const passwordField = manualLoginPasswordField.trim() || "password";
    const accessTokenPath = manualLoginAccessTokenPath.trim() || null;

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
    const capabilities = [...result.capabilities.filter((c) => c.type !== "LOGIN"), loginCapability];
    const hasAuthPage = result.pages.some((p) => p.skeleton === "AUTH_PAGE");
    const pages = hasAuthPage
      ? result.pages
      : [{ id: "auth-login-manual", title: "로그인", skeleton: "AUTH_PAGE" as const, capabilityIds: ["auth.login"] }, ...result.pages];

    let unresolved = result.unresolved.filter((f) => f.field !== AUTH_LOGIN_FIELD);
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
    setResult({
      ...result,
      capabilities,
      pages,
      unresolved,
      status: unresolved.length === 0 && result.status === "NEEDS_INPUT" ? "READY" : result.status,
    });
    setPreviewPageId((prevId) => prevId ?? "auth-login-manual");
    refreshBlocks(capabilities, pages, purpose);
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
              <Button type="button" onClick={handleProposePlan} disabled={proposing || !serviceDescription.trim()}>
                {proposing ? "AI가 제안 생성 중..." : "AI로 서비스에 맞게 페이지 재구성 제안받기"}
              </Button>
              {!serviceDescription.trim() && (
                <p className="mt-1 text-[11px] text-muted-soft">서비스 설명을 입력해야 사용할 수 있습니다.</p>
              )}
              {proposedOperations && (
                <div className="mt-3 space-y-2 rounded-md border border-line bg-white/[0.03] p-3 text-xs">
                  {proposedOperations.length === 0 ? (
                    <p className="text-muted-soft">AI가 개선할 점을 찾지 못했습니다.</p>
                  ) : (
                    <>
                      {proposedOperations.map((op) => (
                        <label
                          key={op.id}
                          className={`flex items-start gap-2 rounded-md border p-2 ${
                            op.valid ? "border-line-strong" : "border-danger-soft opacity-60"
                          }`}
                        >
                          <input
                            type="checkbox"
                            className="mt-0.5"
                            checked={selectedOperationIds.has(op.id)}
                            disabled={!op.valid}
                            onChange={() => toggleOperationSelected(op.id)}
                          />
                          <span>
                            <span className="block font-bold text-foreground">{describeOperation(op)}</span>
                            {op.reason && <span className="block text-muted">{op.reason}</span>}
                            {!op.valid && <span className="block text-danger">적용 불가: {op.validationError}</span>}
                          </span>
                        </label>
                      ))}
                      {planApplyErrors && planApplyErrors.length > 0 && (
                        <div className="rounded-md border border-danger-soft bg-danger/10 p-2 text-danger">
                          {planApplyErrors.map((e, i) => (
                            <p key={i}>· {e}</p>
                          ))}
                        </div>
                      )}
                      <Button
                        type="button"
                        variant="primary"
                        onClick={handleApplyPlan}
                        disabled={applyingPlan || selectedOperationIds.size === 0}
                      >
                        {applyingPlan ? "적용 중..." : `선택한 ${selectedOperationIds.size}개 적용`}
                      </Button>
                    </>
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
                      blocks={pageBlocks[previewPageId] ?? []}
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
