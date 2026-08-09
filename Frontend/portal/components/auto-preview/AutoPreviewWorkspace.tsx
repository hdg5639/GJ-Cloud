"use client";

import { useRef, useState } from "react";
import type { ChangeEvent } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api } from "@/lib/api-client";
import type {
  PreviewAnalysisResult,
  PageReviewFinding,
  PreviewCapability,
  PreviewPageDraft,
  PagePlanOperation,
  PagePlanOperationView,
  PreviewFlowStep,
  PreviewApiBinding,
  PreviewPagePlan,
  PreviewMode,
  VmResponse,
} from "@/lib/types";
import { InstanceSectionNav } from "@/components/ui/instance-section-nav";
import { PageLoader, Spinner } from "@/components/ui/loader";
import { Field, Input, Select, Textarea } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { PreviewPageRenderer } from "@/components/preview-runtime/PreviewPageRenderer";
import { ProductShell } from "@/components/preview-runtime/ProductShell";
import { BlueprintPartPicker } from "@/components/preview-runtime/BlueprintPartPicker";
import { ApiCallLog } from "@/components/preview-runtime/ApiCallLog";
import { ProductExperienceRuntime, ScenarioWorkbench } from "@/components/preview-runtime/scenario";
import { rowId } from "@/components/preview-runtime/api";
import type { ApiCallLogEntry } from "@/components/preview-runtime/types";
import type { Block } from "@/components/preview-runtime/blueprint";
import {
  PreviewDeploymentTargetSection,
  type PreviewDeploymentTargetType,
} from "@/components/auto-preview/PreviewDeploymentTargetSection";

type Purpose = "API_TEST" | "PRODUCT_LIKE" | "ADMIN";
type ApiDocumentSource = "URL" | "FILE";

function isAbsoluteHttpUrl(value: string): boolean {
  try {
    const parsed = new URL(value.trim());
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}

// 이전 Ops 버전이 상대 servers.url을 반환해도 URL 입력 분석이라면 문서 origin과 즉시 결합한다.
// 파일 업로드는 기준 origin이 없으므로 상대 값을 보존하고 사용자가 절대 주소를 입력하게 한다.
function resolveAnalyzedApiBaseUrl(serverUrl: string | undefined, documentUrl: string): string {
  const value = serverUrl?.trim() ?? "";
  if (!value || isAbsoluteHttpUrl(value) || !documentUrl.trim()) return value;
  try {
    return new URL(value, documentUrl.trim()).toString().replace(/\/$/, "");
  } catch {
    return value;
  }
}

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

// Workflow Composition Phase 2 Change Request §19 WP-7 "Plan and flow review UI" — RuleBasedFlowGenerator가
// 만든 workflow(§22 7번)를 사용자가 배포 전에 확인할 수 있게 단계 하나하나를 사람이 읽는 문장으로
// 요약한다. 아직 AI가 flow를 만들지 않아(WP-4 없음) 편집·승인 UI는 아니고 읽기 전용 요약이다.
function describeFlowStep(step: PreviewFlowStep, bindings: PreviewApiBinding[]): string {
  switch (step.type) {
    case "API_CALL":
    case "REFRESH_BINDING": {
      const binding = bindings.find((b) => b.id === step.bindingRef);
      const label = step.type === "API_CALL" ? "API 호출" : "새로고침";
      if (!binding) return `${label}: ${step.bindingRef}`;
      const mappings = binding.outputMappings.map((m) => `${m.from} → ${m.to}`).join(", ");
      return mappings ? `${label}: ${binding.capabilityId} (응답 매핑: ${mappings})` : `${label}: ${binding.capabilityId}`;
    }
    case "SET_CONTEXT":
      return `값 저장: ${Object.entries(step.values ?? {}).map(([k, v]) => `${k}=${v}`).join(", ")}`;
    case "NAVIGATE":
      return `이동: ${Object.entries(step.parameters ?? {}).map(([k, v]) => `${k}=${v}`).join(", ") || step.pageId}`;
    case "POLL":
      return `상태 추적: ${step.intervalMs}ms 간격, 최대 ${step.timeoutSeconds}초`;
    case "WAIT":
      return `대기: ${step.timeoutSeconds}초`;
    case "CONDITION":
      return `조건: ${step.condition}`;
    case "SHOW_SUCCESS":
      return `성공 메시지: ${step.message}`;
    case "SHOW_ERROR":
      return `실패 메시지: ${step.message}`;
    default:
      return step.type;
  }
}

const ACCESS_TOKEN_PATH_FIELD = "auth.login.accessTokenPath";
const AUTH_LOGIN_FIELD = "auth.login";

export function AutoPreviewWorkspace({ fixedVmId }: { fixedVmId?: string }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const standalone = !fixedVmId;
  const { accessToken } = useAuth();

  const [step, setStep] = useState<1 | 2 | 3>(1);

  // 1단계 — 입력
  const [apiDocumentSource, setApiDocumentSource] = useState<ApiDocumentSource>("URL");
  const [apiDocsUrl, setApiDocsUrl] = useState("");
  const [apiDocsContent, setApiDocsContent] = useState("");
  const [apiDocsFileName, setApiDocsFileName] = useState("");
  const apiDocsFileRef = useRef<HTMLInputElement>(null);
  const [documentationPageUrl, setDocumentationPageUrl] = useState("");
  const [serviceDescription, setServiceDescription] = useState("");
  const [purpose, setPurpose] = useState<Purpose>("API_TEST");
  const [previewMode, setPreviewMode] = useState<PreviewMode>("SCENARIO_PREVIEW");
  const [analyzing, setAnalyzing] = useState(false);
  const [analysisError, setAnalysisError] = useState<string | null>(null);

  // 2단계 — 분석/검수/미리보기
  const [result, setResult] = useState<PreviewAnalysisResult | null>(null);
  const [apiBaseUrl, setApiBaseUrl] = useState("");
  const [reviewing, setReviewing] = useState(false);
  const [reviewFindings, setReviewFindings] = useState<PageReviewFinding[] | null>(null);
  const [analysisPanelOpen, setAnalysisPanelOpen] = useState(false);
  const [scenarioIntent, setScenarioIntent] = useState("");
  const [selectedResourceNames, setSelectedResourceNames] = useState<Set<string>>(new Set());
  const [regenerating, setRegenerating] = useState(false);
  const [regenerationError, setRegenerationError] = useState<string | null>(null);
  // Plan Review UI(Increment 5 2부) — proposing 중엔 AI 호출, proposedOperations는 아직 적용 안 된
  // 제안 목록(체크박스 검토용), selectedOperationIds는 사용자가 체크한 것, applyingPlan/planApplyErrors는
  // 선택한 서브셋을 실제로 적용하는 단계.
  const [proposing, setProposing] = useState(false);
  const [proposedOperations, setProposedOperations] = useState<PagePlanOperationView[] | null>(null);
  const [selectedOperationIds, setSelectedOperationIds] = useState<Set<string>>(new Set());
  const [applyingPlan, setApplyingPlan] = useState(false);
  const [planApplyErrors, setPlanApplyErrors] = useState<string[] | null>(null);
  const [planAppliedNotice, setPlanAppliedNotice] = useState<string | null>(null);
  const proposalRef = useRef<HTMLDivElement>(null);
  // Workflow Composition Phase 2 Change Request §7 "Navigation Requirements" — "selected-row state
  // must not be the only detail-navigation mechanism". previewPageId/선택된 행을 URL 쿼리파라미터
  // (?page=&selected=)에 반영해 새로고침 생존·브라우저 뒤로/앞으로가기·직접 URL 진입을 실제로
  // 만족시킨다. 클릭으로 얻은 전체 row 객체는 "수정" 폼 prefill에 필요해 로컬 state로도 들고 있되,
  // "선택됐는지 여부"의 소스 오브 트루스는 항상 URL이다 — 뒤로가기로 URL에서 selected가 사라지면
  // 로컬에 옛 row 객체가 남아있어도 즉시 선택 해제로 취급해야 하기 때문(아래 effectiveSelectedRow).
  const previewPageId = searchParams.get("page");
  const selectedIdFromUrl = searchParams.get("selected");
  const [selectedRow, setSelectedRowState] = useState<Record<string, unknown> | null>(null);
  const effectiveSelectedRow = selectedIdFromUrl
    ? selectedRow && rowId(selectedRow) === selectedIdFromUrl
      ? selectedRow
      : { id: selectedIdFromUrl }
    : null;

  const activePagePlan = result?.pagePlans.find((plan) => plan.id === previewPageId);
  // Planner가 선언한 route parameter뿐 아니라 NavigationRule이 넘긴 안전한 query state도 Runtime에
  // 전달한다. 최종 백엔드 검증이 target page/parameter를 검사하므로 여기서 다시 이름을 추측해
  // 누락시키지 않는다. page 자체는 라우팅 메타데이터이므로 제외한다.
  const activeRouteParameters = Object.fromEntries(
    Array.from(searchParams.entries()).filter(([name]) => name !== "page" && name !== "targetVmId")
  );

  function allRouteParameterNames(): Set<string> {
    return new Set([
      "selected",
      "id",
      ...(result?.pagePlans.flatMap((plan) => plan.routeParameters.map((parameter) => parameter.name)) ?? []),
    ]);
  }

  function writePreviewQuery(
    pageId: string | null,
    parameters: Record<string, string> = {},
    mode: "push" | "replace" = "push"
  ) {
    const nextParams = new URLSearchParams(searchParams.toString());
    for (const name of allRouteParameterNames()) nextParams.delete(name);
    if (pageId) nextParams.set("page", pageId);
    else nextParams.delete("page");
    for (const [name, value] of Object.entries(parameters)) {
      if (value) nextParams.set(name, value);
    }
    const query = nextParams.toString();
    const href = query ? `${pathname}?${query}` : pathname;
    if (mode === "replace") router.replace(href);
    else router.push(href);
  }

  function setPreviewPageId(id: string | null) {
    setSelectedRowState(null);
    writePreviewQuery(id);
  }

  function selectRow(row: Record<string, unknown> | null) {
    setSelectedRowState(row);
    const next = new URLSearchParams(searchParams.toString());
    if (row) next.set("selected", rowId(row));
    else next.delete("selected");
    const query = next.toString();
    router.push(query ? `${pathname}?${query}` : pathname);
  }

  function navigatePreview(
    targetPageId: string | null,
    parameters: Record<string, string>,
    type: "OPEN_PAGE" | "OPEN_OVERLAY" | "GO_BACK" | "REPLACE_ROUTE"
  ) {
    if (type === "GO_BACK") {
      router.back();
      return;
    }
    if (type === "OPEN_OVERLAY") {
      // 현재 MVP Runtime에는 페이지 간 overlay host가 없으므로 route state로 안전하게 폴백한다.
      writePreviewQuery(targetPageId ?? previewPageId, parameters);
      return;
    }
    setSelectedRowState(null);
    writePreviewQuery(targetPageId, parameters, type === "REPLACE_ROUTE" ? "replace" : "push");
  }

  // Direction Recovery Change Request §13.1 — 라이브 프리뷰가 조립 규칙을 직접 계산하지 않도록,
  // capability/페이지가 바뀔 때마다(analyze/plan 응답 직후 + accessTokenPath 지정·수동 로그인 등록
  // 직후) POST /ops/preview/blocks로 다시 계산받은 결과를 여기 저장해 PreviewPageRenderer에 그대로 넘긴다.
  const [pageBlocks, setPageBlocks] = useState<Record<string, Block[]>>({});
  // Phase C — 사용자가 블록별로 고른 Blueprint 파츠("pageId/instanceId"→componentId). blocks/deploy에 전달.
  const [partOverrides, setPartOverrides] = useState<Record<string, string>>({});
  // AI 제안 — /parts/suggest가 채운 componentId를 partOverrides에 병합하고, 이유는 툴팁으로 보여준다.
  const [suggestingParts, setSuggestingParts] = useState(false);
  const [partReasons, setPartReasons] = useState<Record<string, string>>({});
  const [previewAuthToken, setPreviewAuthToken] = useState<string | null>(null);
  const [apiCallLog, setApiCallLog] = useState<ApiCallLogEntry[]>([]);
  const [previewSurface, setPreviewSurface] = useState<"PRODUCT" | "SCENARIO" | "OPERATION">("PRODUCT");
  const [accessTokenPathInput, setAccessTokenPathInput] = useState("");
  const [manualLoginPath, setManualLoginPath] = useState("");
  const [manualLoginUsernameField, setManualLoginUsernameField] = useState("email");
  const [manualLoginPasswordField, setManualLoginPasswordField] = useState("password");
  const [manualLoginAccessTokenPath, setManualLoginAccessTokenPath] = useState("");

  // 3단계 — 배포
  const [targetName, setTargetName] = useState("");
  const [deploying, setDeploying] = useState(false);
  const [deployError, setDeployError] = useState<string | null>(null);
  const [deploymentTargetType, setDeploymentTargetType] = useState<PreviewDeploymentTargetType>(
    standalone && searchParams.get("targetVmId") ? "USER_VM" : standalone ? "MANAGED" : "USER_VM"
  );
  const [deploymentVms, setDeploymentVms] = useState<VmResponse[]>([]);
  const [selectedDeploymentVmId, setSelectedDeploymentVmId] = useState(standalone ? "" : (fixedVmId ?? ""));
  const [deploymentVmsLoading, setDeploymentVmsLoading] = useState(false);
  const [deploymentVmsLoaded, setDeploymentVmsLoaded] = useState(false);
  const [deploymentVmsError, setDeploymentVmsError] = useState<string | null>(null);

  async function loadDeploymentVms() {
    if (!accessToken || !standalone || deploymentVmsLoading) return;
    setDeploymentVmsLoading(true);
    setDeploymentVmsError(null);
    try {
      const vms = await api.vm.list(accessToken);
      setDeploymentVms(vms);
      setDeploymentVmsLoaded(true);

      const requestedVmId = searchParams.get("targetVmId");
      const requestedVm = requestedVmId
        ? vms.find((vm) => vm.id === requestedVmId && vm.status === "RUNNING")
        : undefined;
      if (requestedVm) {
        setDeploymentTargetType("USER_VM");
        setSelectedDeploymentVmId(requestedVm.id);
      } else {
        setSelectedDeploymentVmId((current) =>
          vms.some((vm) => vm.id === current && vm.status === "RUNNING") ? current : ""
        );
      }
    } catch (error) {
      setDeploymentVmsError(error instanceof Error ? error.message : "인스턴스 목록을 불러오지 못했습니다.");
    } finally {
      setDeploymentVmsLoading(false);
    }
  }

  function openDeploymentStep() {
    setStep(3);
    if (standalone && !deploymentVmsLoaded) void loadDeploymentVms();
  }

  function writeDeploymentTargetQuery(vmId: string | null) {
    const nextSearchParams = new URLSearchParams(searchParams.toString());
    if (vmId) nextSearchParams.set("targetVmId", vmId);
    else nextSearchParams.delete("targetVmId");
    const query = nextSearchParams.toString();
    router.replace(query ? `${pathname}?${query}` : pathname);
  }

  function changeDeploymentTargetType(targetType: PreviewDeploymentTargetType) {
    setDeploymentTargetType(targetType);
    setDeployError(null);
    if (targetType === "MANAGED") writeDeploymentTargetQuery(null);
    if (targetType === "USER_VM" && standalone && !deploymentVmsLoaded) void loadDeploymentVms();
  }

  function selectDeploymentVm(vmId: string) {
    setSelectedDeploymentVmId(vmId);
    setDeployError(null);
    writeDeploymentTargetQuery(vmId);
  }

  // Direction Recovery Change Request §13.1 — capability/페이지가 바뀔 때마다 이 함수로 Block을
  // 다시 계산받는다. 실패해도 조용히 무시한다(마지막으로 받은 pageBlocks가 그대로 남을 뿐, 분석·검수·
  // 배포 자체를 막지 않음 — AI 검수/재구성이 실패를 안전하게 무시하는 것과 같은 원칙).
  async function refreshBlocks(
    capabilities: PreviewCapability[],
    pages: PreviewPageDraft[],
    pagePlans: PreviewPagePlan[],
    forPurpose: Purpose,
    overrides: Record<string, string> = partOverrides
  ) {
    if (!accessToken) return;
    try {
      const data = await api.ops.preview.blocks(accessToken, {
        capabilities, pages, pagePlans, purpose: forPurpose, partOverrides: overrides,
      });
      setPageBlocks(data.pageBlocks);
    } catch {
      // 무시 — 위 주석 참고.
    }
  }

  // AI 파츠 제안 — 스왑 가능한 Block별 추천 componentId를 받아 partOverrides에 병합한 뒤 blocks를 다시
  // 받는다. 검증은 백엔드(AiPartAdvisor)가 이미 마쳐 호환되는 componentId만 오므로 그대로 얹으면 된다.
  // 실패해도 조용히 무시한다(AI 검수/재구성과 같은 원칙).
  async function handleSuggestParts() {
    if (!accessToken || !result) return;
    setSuggestingParts(true);
    try {
      const data = await api.ops.preview.suggestParts(accessToken, {
        serviceDescription: effectiveServiceDescription() || undefined,
        purpose,
        capabilities: result.capabilities,
        pages: result.pages,
        pagePlans: result.pagePlans,
      });
      const nextOverrides = { ...partOverrides };
      const nextReasons = { ...partReasons };
      for (const suggestion of data.suggestions) {
        const key = `${suggestion.pageId}/${suggestion.instanceId}`;
        nextOverrides[key] = suggestion.componentId;
        if (suggestion.reason) nextReasons[key] = suggestion.reason;
      }
      setPartOverrides(nextOverrides);
      setPartReasons(nextReasons);
      refreshBlocks(result.capabilities, result.pages, result.pagePlans, purpose, nextOverrides);
    } catch {
      // 무시 — 위 주석 참고.
    } finally {
      setSuggestingParts(false);
    }
  }

  async function handleAnalyze() {
    if (!accessToken || !hasApiDocumentSource()) return;
    setAnalyzing(true);
    setAnalysisError(null);
    setResult(null);
    setReviewFindings(null);
    setSelectedRowState(null);
    setProposedOperations(null);
    setSelectedOperationIds(new Set());
    setPlanApplyErrors(null);
    setPlanAppliedNotice(null);
    try {
      const data = await api.ops.preview.analyze(accessToken, {
        apiDocsUrl: apiDocumentSource === "URL" ? apiDocsUrl.trim() : undefined,
        apiDocsContent: apiDocumentSource === "FILE" ? apiDocsContent : undefined,
        documentationPageUrl: documentationPageUrl.trim() || undefined,
        serviceDescription: serviceDescription.trim() || undefined,
        purpose,
        previewMode,
      });
      setResult(data);
      setSelectedResourceNames(activeResourceNames(data));
      refreshBlocks(data.capabilities, data.pages, data.pagePlans, purpose);
      setApiBaseUrl(resolveAnalyzedApiBaseUrl(
        data.apiServerUrls[0],
        apiDocumentSource === "URL" ? apiDocsUrl : ""
      ));
      setPreviewPageId(data.pages[0]?.id ?? null);
      setPreviewAuthToken(null);
      setApiCallLog([]);
      setAccessTokenPathInput("");
      setManualLoginPath("");
      setManualLoginUsernameField("email");
      setManualLoginPasswordField("password");
      setManualLoginAccessTokenPath("");
      setPreviewSurface(
        data.previewMode !== "OPERATION_PREVIEW"
          && data.scenarios.some((scenario) => scenario.status !== "UNSUPPORTED")
          ? "PRODUCT"
          : "OPERATION"
      );
      setStep(2);
    } catch (err) {
      setAnalysisError(err instanceof Error ? err.message : "분석에 실패했습니다.");
    } finally {
      setAnalyzing(false);
    }
  }

  function hasApiDocumentSource(): boolean {
    return apiDocumentSource === "URL" ? Boolean(apiDocsUrl.trim()) : Boolean(apiDocsContent);
  }

  async function handleApiDocumentFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      setAnalysisError("OpenAPI 파일은 최대 5MB까지 업로드할 수 있습니다.");
      event.target.value = "";
      return;
    }
    try {
      const content = await file.text();
      setApiDocsContent(content);
      setApiDocsFileName(file.name);
      setAnalysisError(null);
    } catch {
      setAnalysisError("파일을 읽지 못했습니다. JSON 또는 YAML 파일인지 확인해주세요.");
    }
  }

  function activeResourceNames(data: PreviewAnalysisResult): Set<string> {
    const activeIds = new Set(data.activeCapabilityIds);
    return new Set(
      data.availableCapabilities
        .filter((capability) => activeIds.has(capability.id))
        .map((capability) => capability.resourceName)
    );
  }

  function toggleResource(resourceName: string) {
    setSelectedResourceNames((previous) => {
      const next = new Set(previous);
      if (next.has(resourceName)) next.delete(resourceName);
      else next.add(resourceName);
      return next;
    });
  }

  async function handleRegenerateFromIntent() {
    if (!accessToken || !result || !scenarioIntent.trim() || selectedResourceNames.size === 0) return;
    const selectedCapabilityIds = result.availableCapabilities
      .filter((capability) => selectedResourceNames.has(capability.resourceName))
      .map((capability) => capability.id);
    setRegenerating(true);
    setRegenerationError(null);
    setPlanAppliedNotice(null);
    try {
      const data = await api.ops.preview.analyze(accessToken, {
        apiDocsUrl: apiDocumentSource === "URL" ? apiDocsUrl.trim() : undefined,
        apiDocsContent: apiDocumentSource === "FILE" ? apiDocsContent : undefined,
        documentationPageUrl: documentationPageUrl.trim() || undefined,
        serviceDescription: serviceDescription.trim() || undefined,
        scenarioIntent: scenarioIntent.trim(),
        selectedCapabilityIds,
        purpose,
        previewMode,
      });
      setResult(data);
      setSelectedResourceNames(activeResourceNames(data));
      setReviewFindings(null);
      setProposedOperations(null);
      setSelectedOperationIds(new Set());
      setPlanApplyErrors(null);
      setPartOverrides({});
      setPartReasons({});
      refreshBlocks(data.capabilities, data.pages, data.pagePlans, purpose, {});
      setPreviewPageId(data.pages[0]?.id ?? null);
      setPreviewSurface(
        data.previewMode !== "OPERATION_PREVIEW"
          && data.scenarios.some((scenario) => scenario.status !== "UNSUPPORTED")
          ? "PRODUCT"
          : "OPERATION"
      );
      setPlanAppliedNotice("선택한 API와 자연어 플로우를 반영해 서비스 화면과 시나리오를 다시 만들었습니다.");
      setAnalysisPanelOpen(false);
    } catch (error) {
      setRegenerationError(error instanceof Error ? error.message : "시나리오 재생성에 실패했습니다.");
    } finally {
      setRegenerating(false);
    }
  }

  function effectiveServiceDescription(): string {
    return result?.resolvedServiceDescription?.trim() || serviceDescription.trim();
  }

  async function handleReview() {
    if (!accessToken || !result) return;
    setReviewing(true);
    try {
      const findings = await api.ops.preview.review(accessToken, {
        serviceDescription: effectiveServiceDescription() || undefined,
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
  async function handleProposePlan(reviewContext?: string) {
    if (!accessToken || !result) return;
    setProposing(true);
    setPlanApplyErrors(null);
    try {
      const reviewInstruction = reviewContext?.trim()
        ? [
            effectiveServiceDescription(),
            "아래 AI 검수 피드백을 우선 반영하는 구체적인 수정안을 제안해주세요.",
            reviewContext.trim(),
          ].filter(Boolean).join("\n\n")
        : effectiveServiceDescription();
      const proposal = await api.ops.preview.planPropose(accessToken, {
        serviceDescription: reviewInstruction || undefined,
        purpose,
        capabilities: result.capabilities,
        pages: result.pages,
        pagePlans: result.pagePlans,
        flows: result.flows,
        bindings: result.bindings,
      });
      setProposedOperations(proposal.operations);
      setSelectedOperationIds(new Set(proposal.operations.filter((op) => op.valid).map((op) => op.id)));
      window.requestAnimationFrame(() => proposalRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" }));
    } catch (err) {
      setProposedOperations([]);
      setSelectedOperationIds(new Set());
      setPlanApplyErrors([err instanceof Error ? err.message : "AI 수정안을 만들지 못했습니다."]);
    } finally {
      setProposing(false);
    }
  }

  function reviewContextOf(findings: PageReviewFinding[]): string {
    return findings
      .map((finding, index) => `${index + 1}. ${finding.message}${finding.remediation ? `\n조치: ${finding.remediation}` : ""}`)
      .join("\n");
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
        capabilityIds: op.capabilityIds,
        pageType: op.pageType,
        layoutRef: op.layoutRef,
        featureKey: op.featureKey,
        featureEnabled: op.featureEnabled,
        navigationRule: op.navigationRule,
        flow: op.flow,
        flowId: op.flowId,
        actionId: op.actionId,
        reason: op.reason,
      }));
    if (selectedOps.length === 0) return;
    setApplyingPlan(true);
    setPlanApplyErrors(null);
    try {
      const applied = await api.ops.preview.planApply(accessToken, {
        capabilities: result.capabilities,
        pages: result.pages,
        pagePlans: result.pagePlans,
        flows: result.flows,
        bindings: result.bindings,
        operations: selectedOps,
      });
      if (applied.errors.length > 0) {
        setPlanApplyErrors(applied.errors);
        return;
      }
      setResult({
        ...result,
        pages: applied.pages,
        pagePlans: applied.pagePlans,
        flows: applied.flows,
        bindings: applied.bindings,
        generationMode: applied.generationMode,
      });
      refreshBlocks(result.capabilities, applied.pages, applied.pagePlans, purpose);
      setPreviewPageId(applied.pages[0]?.id ?? null);
      setProposedOperations(null);
      setSelectedOperationIds(new Set());
      setReviewFindings(null);
      setPlanAppliedNotice(`선택한 ${selectedOps.length}개 수정안을 적용했습니다. 서비스 화면에서 결과를 확인해 주세요.`);
      setAnalysisPanelOpen(false);
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
      case "ADD_PAGE":
        return `"${op.newTitle}" 페이지 신설`;
      case "REMOVE_PAGE":
        return `"${pageTitle(op.pageId)}" 페이지 삭제`;
      case "SPLIT_PAGE":
        return `"${pageTitle(op.pageId)}"에서 "${op.newTitle}" 페이지 분리`;
      case "SET_PAGE_TYPE":
        return `"${pageTitle(op.pageId)}" 페이지 유형을 ${op.pageType}로 변경`;
      case "SET_LAYOUT":
        return `"${pageTitle(op.pageId)}" 레이아웃을 ${op.layoutRef}로 변경`;
      case "SET_FEATURE":
        return `"${pageTitle(op.pageId)}"의 ${op.featureKey} 기능을 ${op.featureEnabled ? "활성화" : "비활성화"}`;
      case "ADD_NAVIGATION":
        return `${op.navigationRule?.sourcePageId ?? "페이지"}에 ${op.navigationRule?.trigger ?? "이동"} 네비게이션 추가`;
      case "ADD_FLOW":
        return `${op.flow?.id ?? "신규"} 워크플로우 추가`;
      case "ASSIGN_FLOW":
        return `${op.flowId} 워크플로우를 "${pageTitle(op.pageId)}"의 ${op.actionId} 액션에 연결`;
      default:
        return op.reason ?? "";
    }
  }

  async function handleDeploy() {
    if (!accessToken || !result || !targetName.trim() || !isAbsoluteHttpUrl(apiBaseUrl)) return;
    const targetVmId = fixedVmId ?? selectedDeploymentVmId;
    if (deploymentTargetType === "USER_VM") {
      const selectedVmIsAvailable = fixedVmId
        ? Boolean(targetVmId)
        : deploymentVms.some((vm) => vm.id === targetVmId && vm.status === "RUNNING");
      if (!selectedVmIsAvailable) {
        setDeployError("배포할 실행 중인 인스턴스를 선택해주세요.");
        return;
      }
    }
    setDeploying(true);
    setDeployError(null);
    try {
      const deployBody = {
        targetName: targetName.trim(),
        apiBaseUrl: apiBaseUrl.trim(),
        capabilities: result.capabilities,
        pages: result.pages,
        pagePlans: result.pagePlans,
        flows: result.flows,
        bindings: result.bindings,
        authStrategy: result.authStrategy,
        purpose,
        generationMode: result.generationMode,
        scenarios: result.scenarios,
        previewMode: result.previewMode,
        partOverrides,
      };
      if (deploymentTargetType === "MANAGED") {
        const preview = await api.ops.preview.deployManaged(accessToken, deployBody);
        router.push(`/auto-preview/deployments/${preview.id}`);
      } else {
        const deployment = await api.ops.preview.deploy(accessToken, targetVmId, deployBody);
        router.push(`/instances/${targetVmId}/deployments/${deployment.id}`);
      }
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
    refreshBlocks(capabilities, result.pages, result.pagePlans, purpose);
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
    const pagePlans = hasAuthPage
      ? result.pagePlans
      : [{
          id: "auth-login-manual",
          title: "로그인",
          route: "/login",
          pageType: "AUTH" as const,
          layoutRef: "auth-layout",
          capabilityIds: ["auth.login"],
          routeParameters: [],
          queryParameters: [],
          navigationRules: [],
          features: {},
          confidence: "LOW",
          reason: "사용자가 로그인 API를 직접 지정함",
          unsupportedCapabilityWarnings: [],
        }, ...result.pagePlans];

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
      pagePlans,
      unresolved,
      status: unresolved.length === 0 && result.status === "NEEDS_INPUT" ? "READY" : result.status,
    });
    if (!previewPageId) {
      setPreviewPageId("auth-login-manual");
    }
    refreshBlocks(capabilities, pages, pagePlans, purpose);
  }

  if (!accessToken) return <PageLoader />;

  return (
    <div className="mx-auto max-w-[1380px]">
      {fixedVmId && <InstanceSectionNav vmId={fixedVmId} />}

      <header className="mb-5">
        <span className="text-[11px] font-extrabold tracking-[.11em] text-muted-soft">AUTO PREVIEW</span>
        <h1 className="my-[5px] text-[22px] font-extrabold tracking-tight">API 문서로 테스트 페이지 자동 생성</h1>
        <p className="m-0 text-sm text-muted">
          OpenAPI URL이나 파일과 서비스 문맥을 분석해 실제 사용자 시나리오를 만들고 {standalone ? "원하는 실행 환경에" : "이 VM에 바로"} 배포합니다.
        </p>
      </header>

      {/* 1단계 */}
      {step === 1 && (
        <section className="max-w-[900px] rounded-panel border border-line bg-panel p-5">
          <div className="mb-4">
            <p className="mb-2 text-xs font-bold text-muted">OpenAPI 입력 방식</p>
            <div className="grid gap-2 sm:grid-cols-2">
              {([
                ["URL", "문서 URL", "서버에서 api-docs JSON/YAML을 가져옵니다."],
                ["FILE", "파일 업로드", "로컬의 원본 JSON/YAML 파일을 직접 분석합니다."],
              ] as const).map(([value, title, description]) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => setApiDocumentSource(value)}
                  className={`rounded-[12px] border p-3 text-left transition ${
                    apiDocumentSource === value
                      ? "border-brand bg-brand/[0.07]"
                      : "border-line-strong bg-white/[0.02] hover:bg-white/[0.04]"
                  }`}
                >
                  <span className="block text-sm font-extrabold text-foreground">{title}</span>
                  <span className="mt-1 block text-[11px] leading-4 text-muted">{description}</span>
                </button>
              ))}
            </div>
          </div>
          {apiDocumentSource === "URL" ? (
            <Field label="API Docs URL" htmlFor="preview-docs-url">
              <Input
                id="preview-docs-url"
                value={apiDocsUrl}
                onChange={(e) => setApiDocsUrl(e.target.value)}
                placeholder="https://api.example.com/v3/api-docs"
                required
              />
            </Field>
          ) : (
            <div className="mb-3.5">
              <p className="mb-[7px] text-xs font-bold text-muted">OpenAPI JSON/YAML 파일</p>
              <input
                ref={apiDocsFileRef}
                type="file"
                accept=".json,.yaml,.yml,application/json,application/yaml,text/yaml"
                className="hidden"
                onChange={handleApiDocumentFile}
              />
              <button
                type="button"
                onClick={() => {
                  if (apiDocsFileRef.current) {
                    apiDocsFileRef.current.value = "";
                    apiDocsFileRef.current.click();
                  }
                }}
                className="flex min-h-[74px] w-full items-center justify-between gap-3 rounded-[12px] border border-dashed border-line-strong bg-white/[0.02] px-4 text-left hover:border-brand/50 hover:bg-brand/[0.03]"
              >
                <span>
                  <span className="block text-sm font-extrabold text-foreground">
                    {apiDocsFileName || "파일을 선택해 주세요"}
                  </span>
                  <span className="mt-1 block text-[11px] text-muted">
                    JSON 또는 YAML · 최대 5MB · 원문은 저장하지 않습니다.
                  </span>
                </span>
                <span className="shrink-0 rounded-[8px] border border-line-strong px-3 py-2 text-xs font-bold text-muted">
                  찾아보기
                </span>
              </button>
            </div>
          )}
          <Field label="서비스 문서·소개 페이지 URL (선택)" htmlFor="preview-documentation-page" className="mt-4">
            <Input
              id="preview-documentation-page"
              value={documentationPageUrl}
              onChange={(e) => setDocumentationPageUrl(e.target.value)}
              placeholder="https://docs.example.com 또는 Swagger UI / Redoc 페이지"
            />
            <span className="text-[11px] font-normal leading-4 text-muted-soft">
              페이지의 제목과 설명만 안전하게 추출해 서비스 목적과 용어를 이해하는 데 사용합니다.
            </span>
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
          <Field label="프리뷰 방식" htmlFor="preview-mode" className="mt-4">
            <Select
              id="preview-mode"
              value={previewMode}
              onChange={(e) => setPreviewMode(e.target.value as PreviewMode)}
            >
              <option value="SCENARIO_PREVIEW">사용자 시나리오 중심 (권장)</option>
              <option value="INFERRED_SCENARIO_PREVIEW">추론 가능한 시나리오까지 표시</option>
              <option value="OPERATION_PREVIEW">개별 엔드포인트 중심</option>
            </Select>
          </Field>

          {analysisError && <p className="mt-3 text-xs text-danger">{analysisError}</p>}

          <Button
            variant="primary"
            onClick={handleAnalyze}
            disabled={analyzing || !hasApiDocumentSource()}
            className="mt-5 min-w-28"
          >
            {analyzing && <Spinner className="h-4 w-4" />}
            {analyzing ? "분석 중" : "분석하기"}
          </Button>
          {analyzing && (
            <div
              className="mt-5 flex items-center gap-4 rounded-[14px] border border-brand/25 bg-brand/[0.06] p-4"
              role="status"
              aria-live="polite"
            >
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src="/gamjabox-loader.svg" alt="" width={52} height={52} className="shrink-0" />
              <div className="min-w-0">
                <p className="text-sm font-extrabold text-foreground">OpenAPI 문서를 분석하고 있어요</p>
                <p className="mt-1 text-xs leading-5 text-muted">
                  API 구조를 읽고 사용자 시나리오, 페이지 구성, 서비스 분위기를 순서대로 만들고 있습니다.
                </p>
                <div className="mt-2 flex flex-wrap gap-1.5 text-[10px] font-bold text-brand-strong">
                  <span className="rounded-full bg-brand/10 px-2 py-1">API 인식</span>
                  <span className="rounded-full bg-brand/10 px-2 py-1">시나리오 구성</span>
                  <span className="rounded-full bg-brand/10 px-2 py-1">서비스 화면 조립</span>
                </div>
              </div>
            </div>
          )}
        </section>
      )}

      {/* 2단계 */}
      {step === 2 && result && (
        <div
          className={`grid min-h-0 gap-4 ${
            analysisPanelOpen
              ? "xl:grid-cols-[minmax(0,1fr)_minmax(360px,440px)]"
              : "grid-cols-1"
          }`}
        >
          {analysisPanelOpen && (
            <button
              type="button"
              className="fixed inset-0 z-[220] bg-black/45 backdrop-blur-[2px] xl:hidden"
              onClick={() => setAnalysisPanelOpen(false)}
              onWheel={(event) => event.preventDefault()}
              onTouchMove={(event) => event.preventDefault()}
              aria-label="인식된 API 패널 닫기"
            />
          )}
          {/* 작은 화면에서는 viewport clipping layer 안에서 드로어로, 데스크톱에서는 메인 미리보기
              옆의 sticky column으로 배치해 두 영역이 서로의 스크롤을 끌고 가지 않게 한다. */}
          <div
            className={`pointer-events-none fixed inset-0 z-[230] overflow-hidden xl:sticky xl:inset-auto xl:top-4 xl:col-start-2 xl:row-start-1 xl:h-[calc(100dvh-2rem)] xl:self-start xl:overflow-visible ${
              analysisPanelOpen ? "xl:block" : "xl:hidden"
            }`}
          >
            <aside
              className={`pointer-events-auto absolute bottom-2 right-2 top-2 flex w-[min(calc(100vw-16px),460px)] flex-col overflow-hidden rounded-[18px] border border-line-strong bg-panel shadow-[-18px_14px_64px_rgba(0,0,0,.42),0_1px_0_rgba(255,255,255,.035)_inset] ring-1 ring-black/20 transition-[transform,opacity] duration-300 ease-out sm:bottom-3 sm:right-3 sm:top-3 xl:relative xl:inset-auto xl:h-full xl:w-full xl:translate-x-0 xl:opacity-100 ${
                analysisPanelOpen
                  ? "translate-x-0 opacity-100"
                  : "pointer-events-none translate-x-[calc(100%+16px)] opacity-0"
              }`}
              aria-hidden={!analysisPanelOpen}
            >
            <header className="flex shrink-0 items-start justify-between gap-4 border-b border-line px-5 py-4">
              <div>
                <p className="text-[10px] font-extrabold uppercase tracking-[.14em] text-brand-strong">Analysis</p>
                <h2 className="mt-1 text-base font-extrabold">인식된 API와 구성</h2>
                <p className="mt-1 text-xs text-muted">
                  {result.capabilities.length}개 API · {result.pages.length}개 화면 · {result.scenarios.length}개 시나리오
                </p>
              </div>
              <button
                type="button"
                onClick={() => setAnalysisPanelOpen(false)}
                className="grid h-9 w-9 shrink-0 place-items-center rounded-[9px] border border-line text-lg text-muted hover:bg-white/[0.05] hover:text-foreground"
                aria-label="닫기"
              >
                ×
              </button>
            </header>
            <div
              className="min-h-0 flex-1 touch-pan-y overflow-y-auto overscroll-contain p-5 [scrollbar-gutter:stable]"
              data-preview-scroll-region="analysis"
            >
            <section className="mb-4 rounded-[14px] border border-brand/25 bg-brand/[0.045] p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-[10px] font-extrabold tracking-[.12em] text-brand-strong">SCENARIO COMPOSER</p>
                  <h3 className="mt-1 text-sm font-extrabold">원하는 사용자 흐름으로 다시 만들기</h3>
                </div>
                <span className="rounded-full bg-brand/10 px-2 py-1 text-[10px] font-bold text-brand-strong">
                  {selectedResourceNames.size}개 카테고리
                </span>
              </div>
              <p className="mt-2 text-[11px] leading-5 text-muted">
                실제 사용자가 무엇을 하고 어떤 실패를 복구해야 하는지 적고, 사용할 API 영역을 선택하세요.
                서비스 화면·페이지·시나리오가 함께 다시 구성됩니다.
              </p>
              <Textarea
                value={scenarioIntent}
                onChange={(event) => setScenarioIntent(event.target.value.slice(0, 4000))}
                rows={4}
                className="mt-3 min-h-[112px] bg-background/70 text-xs"
                placeholder="예: 신규 사용자가 가입하고 상품을 검색해 장바구니에 담습니다. 결제가 실패하면 결제 수단을 변경해 다시 시도하고 주문 완료 화면을 확인합니다."
              />
              <div className="mt-3">
                <div className="mb-2 flex items-center justify-between gap-2">
                  <p className="text-[11px] font-bold text-muted">인식된 API 카테고리</p>
                  <button
                    type="button"
                    className="text-[10px] font-bold text-brand-strong hover:underline"
                    onClick={() => setSelectedResourceNames(new Set(result.availableCapabilities.map((capability) => capability.resourceName)))}
                  >
                    전체 선택
                  </button>
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {Array.from(new Set(result.availableCapabilities.map((capability) => capability.resourceName)))
                    .sort()
                    .map((resourceName) => {
                      const resourceCapabilities = result.availableCapabilities.filter(
                        (capability) => capability.resourceName === resourceName
                      );
                      const selected = selectedResourceNames.has(resourceName);
                      const hasDangerous = resourceCapabilities.some(
                        (capability) => capability.risk === "DESTRUCTIVE" || capability.risk === "IRREVERSIBLE"
                      );
                      return (
                        <button
                          key={resourceName}
                          type="button"
                          aria-pressed={selected}
                          onClick={() => toggleResource(resourceName)}
                          className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1.5 text-[11px] font-bold transition ${
                            selected
                              ? "border-brand/50 bg-brand/15 text-brand-strong"
                              : "border-line-strong bg-background/60 text-muted hover:bg-white/[0.05]"
                          }`}
                        >
                          {selected ? "✓" : "+"} {resourceName}
                          <span className="text-[9px] opacity-70">{resourceCapabilities.length}</span>
                          {hasDangerous && <span className="text-[9px] text-[#e8b657]">주의</span>}
                        </button>
                      );
                    })}
                </div>
              </div>
              {regenerationError && (
                <p className="mt-3 rounded-[8px] border border-danger-soft bg-danger/10 p-2 text-[11px] text-danger">
                  {regenerationError}
                </p>
              )}
              <Button
                type="button"
                variant="primary"
                className="mt-4 w-full"
                disabled={regenerating || !scenarioIntent.trim() || selectedResourceNames.size === 0}
                onClick={handleRegenerateFromIntent}
              >
                {regenerating && <Spinner className="h-4 w-4" />}
                {regenerating ? "서비스 흐름 재구성 중" : "선택한 API로 서비스 다시 만들기"}
              </Button>
              {selectedResourceNames.size === 0 && (
                <p className="mt-1.5 text-[10px] text-[#e8b657]">최소 한 개의 API 카테고리를 선택해야 합니다.</p>
              )}
            </section>
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
              <p className="mt-1 text-muted">
                {result.previewMode === "SCENARIO_PREVIEW"
                  ? `${result.scenarios.filter((scenario) => scenario.status === "EXECUTABLE").length}개의 실행 가능한 사용자 시나리오를 구성했습니다.`
                  : result.previewMode === "INFERRED_SCENARIO_PREVIEW"
                    ? "일부 시나리오는 제한된 기능으로 구성되었습니다."
                    : "실행 가능한 시나리오가 없어 엔드포인트 프리뷰로 전환했습니다."}
              </p>
              <p className="mt-1 text-muted">
                시나리오 계획: {result.scenarioPlanningSource === "LLM"
                  ? `AI 의미 분석${result.scenarioPromptVersion ? ` · ${result.scenarioPromptVersion}` : ""}`
                  : result.scenarioPlanningSource === "RULE_BASED"
                    ? "결정적 규칙 기반 fallback"
                    : "사용자 지정 Operation 모드"}
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

            <div className="mb-4 rounded-xl border border-line-strong bg-background/60 p-4">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-[10px] font-extrabold tracking-[.12em] text-brand-strong">SERVICE UNDERSTANDING</span>
                <span className="rounded-full border border-line-strong px-2 py-0.5 text-[10px] font-bold text-muted">
                  신뢰도 {Math.round(result.serviceUnderstanding.confidence * 100)}%
                </span>
              </div>
              <div className="mt-2 flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <strong className="text-sm">{result.serviceUnderstanding.domain}</strong>
                <span className="text-xs text-muted">{result.serviceUnderstanding.serviceType}</span>
              </div>
              <div className="mt-3 flex flex-wrap gap-1.5">
                {result.serviceUnderstanding.coreEntities.map((entity) => (
                  <span key={entity} className="rounded-md bg-white/[0.05] px-2 py-1 text-[11px] font-bold text-muted">
                    {entity}
                  </span>
                ))}
              </div>
              {result.serviceContextSources.length > 0 && (
                <div className="mt-3 flex flex-wrap gap-1.5 border-t border-line-strong pt-3">
                  {result.serviceContextSources.map((source) => (
                    <span
                      key={source}
                      className="rounded-full border border-line-strong bg-white/[0.03] px-2 py-1 text-[9px] font-bold text-muted"
                    >
                      {source === "USER_DESCRIPTION"
                        ? "직접 입력"
                        : source === "SCENARIO_INTENT"
                          ? "시나리오 의도"
                          : source === "DOCUMENTATION_PAGE"
                            ? "서비스 문서"
                            : "OpenAPI 설명"}
                    </span>
                  ))}
                </div>
              )}
              {result.resolvedServiceDescription && (
                <details className="mt-3 rounded-[9px] border border-line-strong bg-white/[0.02] text-[11px]">
                  <summary className="cursor-pointer px-3 py-2 font-bold text-muted">
                    분석에 사용된 서비스 문맥 보기
                  </summary>
                  <p className="max-h-52 overflow-y-auto whitespace-pre-wrap border-t border-line-strong px-3 py-2 leading-5 text-muted-soft">
                    {result.resolvedServiceDescription}
                  </p>
                </details>
              )}
              {result.serviceUnderstanding.primaryGoals.length > 0 && (
                <ul className="mt-3 space-y-1 text-xs text-muted">
                  {result.serviceUnderstanding.primaryGoals.slice(0, 5).map((goal) => (
                    <li key={goal}>· {goal}</li>
                  ))}
                </ul>
              )}
            </div>

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
                  {(() => {
                    const pagePlan = result.pagePlans.find((p) => p.id === page.id);
                    const pageFlows = result.flows.filter((f) => f.trigger?.pageId === page.id);
                    if (!pagePlan && pageFlows.length === 0) return null;
                    return (
                      <div className="mt-2 border-t border-line-strong pt-2 text-[11px] text-muted-soft">
                        {pagePlan && (
                          <p>
                            경로: <span className="font-mono">{pagePlan.route}</span>
                            {pagePlan.confidence && ` · 신뢰도: ${pagePlan.confidence}`}
                          </p>
                        )}
                        {pageFlows.map((flow) => (
                          <div key={flow.id} className="mt-1.5">
                            <p className="font-bold text-muted">
                              워크플로우: {flow.trigger?.actionId ?? flow.id}
                            </p>
                            <ol className="ml-3 list-decimal space-y-0.5">
                              {flow.steps.map((step) => (
                                <li key={step.id}>{describeFlowStep(step, result.bindings)}</li>
                              ))}
                            </ol>
                          </div>
                        ))}
                      </div>
                    );
                  })()}
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
                    <>
                      {reviewFindings.map((finding, i) => (
                      <div
                        key={i}
                        className="border-l-2 py-1 pl-2"
                        style={{
                          borderColor:
                            finding.severity === "CRITICAL" ? "#ff6b6b" : finding.severity === "WARNING" ? "#e8b657" : "#9aa39a",
                        }}
                      >
                        <p className="font-bold text-foreground">
                          [{finding.severity}] {finding.message}
                        </p>
                        {finding.remediation && <p className="mt-0.5 text-muted">→ {finding.remediation}</p>}
                        <button
                          type="button"
                          className="mt-2 text-[11px] font-extrabold text-brand-strong hover:underline disabled:opacity-50"
                          disabled={proposing}
                          onClick={() => void handleProposePlan(reviewContextOf([finding]))}
                        >
                          이 피드백으로 수정안 만들기 →
                        </button>
                      </div>
                      ))}
                      <Button
                        type="button"
                        size="small"
                        onClick={() => void handleProposePlan(reviewContextOf(reviewFindings))}
                        disabled={proposing}
                      >
                        {proposing ? "수정안 생성 중..." : "검수 결과 전체로 수정안 만들기"}
                      </Button>
                    </>
                  )}
                </div>
              )}
            </div>

            <div className="mt-3" ref={proposalRef}>
              <Button
                type="button"
                onClick={() => void handleProposePlan()}
                disabled={proposing || !effectiveServiceDescription()}
              >
                {proposing ? "AI가 제안 생성 중..." : "AI로 서비스에 맞게 페이지 재구성 제안받기"}
              </Button>
              {!effectiveServiceDescription() && (
                <p className="mt-1 text-[11px] text-muted-soft">서비스 설명을 입력해야 사용할 수 있습니다.</p>
              )}
              {proposedOperations && (
                <div className="mt-3 space-y-2 rounded-md border border-line bg-white/[0.03] p-3 text-xs">
                  <div className="rounded-md border border-brand/20 bg-brand/[0.06] p-2 text-muted">
                    적용할 항목만 선택하세요. 선택한 변경은 미리보기와 최종 배포 구성에 즉시 반영됩니다.
                  </div>
                  {planApplyErrors && planApplyErrors.length > 0 && (
                    <div className="rounded-md border border-danger-soft bg-danger/10 p-2 text-danger">
                      {planApplyErrors.map((error, index) => (
                        <p key={index}>· {error}</p>
                      ))}
                    </div>
                  )}
                  {proposedOperations.length === 0 ? (
                    !planApplyErrors?.length && <p className="text-muted-soft">AI가 개선할 점을 찾지 못했습니다.</p>
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
            </div>
            </aside>
          </div>

          <div
            className={`min-w-0 space-y-4 xl:col-start-1 xl:row-start-1 ${
              analysisPanelOpen
                ? "touch-pan-y xl:h-[calc(100dvh-2rem)] xl:overflow-y-auto xl:overscroll-contain xl:pr-1 [scrollbar-gutter:stable]"
                : ""
            }`}
            data-preview-scroll-region="workspace"
          >
            <section className="rounded-panel border border-line bg-panel p-5">
            <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="mb-1 text-sm font-extrabold">실제 API로 미리 확인</h2>
                <p className="max-w-3xl text-xs text-muted">
                  배포 전에 분석된 API에 직접 연결해 화면이 의도대로 동작하는지 확인할 수 있습니다. 대상 API가 이 브라우저
                  origin의 CORS를 허용하지 않으면 요청이 막힐 수 있습니다.
                </p>
              </div>
              <Button type="button" size="small" onClick={() => setAnalysisPanelOpen(true)}>
                인식된 API
                <span className="rounded-full bg-white/[0.07] px-1.5 py-0.5 text-[9px]">
                  {result.capabilities.length}
                </span>
              </Button>
            </div>
            {planAppliedNotice && (
              <div className="mb-4 flex items-center justify-between gap-3 rounded-[12px] border border-brand/25 bg-brand/[0.06] px-4 py-3 text-xs text-brand-strong">
                <span>✓ {planAppliedNotice}</span>
                <button
                  type="button"
                  className="shrink-0 text-base text-muted hover:text-foreground"
                  onClick={() => setPlanAppliedNotice(null)}
                  aria-label="알림 닫기"
                >
                  ×
                </button>
              </div>
            )}
            <Field label="API 서버 주소" htmlFor="preview-api-base-url" className="mb-3">
              <Input
                id="preview-api-base-url"
                value={apiBaseUrl}
                onChange={(e) => setApiBaseUrl(e.target.value)}
                placeholder="https://api.example.com"
              />
              {apiBaseUrl.trim() && !isAbsoluteHttpUrl(apiBaseUrl) && (
                <p className="mt-1.5 text-[11px] text-[#e8b657]">
                  상대 서버 주소만 감지되었습니다. 실제 요청을 보내려면
                  <span className="mx-1 font-mono text-foreground">https://host.example.com{apiBaseUrl.startsWith("/") ? apiBaseUrl : `/${apiBaseUrl}`}</span>
                  형태의 절대 주소를 입력해주세요.
                </p>
              )}
            </Field>
            {result.previewMode !== "OPERATION_PREVIEW"
              && result.scenarios.some((scenario) => scenario.status !== "UNSUPPORTED") && (
              <div className="mb-4 inline-flex rounded-lg border border-line bg-background p-1">
                <button
                  type="button"
                  className={`rounded-md px-3 py-2 text-xs font-extrabold ${
                    previewSurface === "PRODUCT" ? "bg-brand text-black" : "text-muted"
                  }`}
                  onClick={() => setPreviewSurface("PRODUCT")}
                >
                  서비스 화면
                </button>
                <button
                  type="button"
                  className={`rounded-md px-3 py-2 text-xs font-extrabold ${
                    previewSurface === "SCENARIO" ? "bg-brand text-black" : "text-muted"
                  }`}
                  onClick={() => setPreviewSurface("SCENARIO")}
                >
                  시나리오 디버거
                </button>
                <button
                  type="button"
                  className={`rounded-md px-3 py-2 text-xs font-extrabold ${
                    previewSurface === "OPERATION" ? "bg-brand text-black" : "text-muted"
                  }`}
                  onClick={() => setPreviewSurface("OPERATION")}
                >
                  엔드포인트
                </button>
              </div>
            )}
            {previewSurface === "OPERATION" && result.pages.length > 0 && previewPageId && (
              <BlueprintPartPicker
                blocks={pageBlocks[previewPageId] ?? []}
                pageId={previewPageId}
                purpose={purpose}
                overrides={partOverrides}
                onChange={(next) => {
                  setPartOverrides(next);
                  refreshBlocks(result.capabilities, result.pages, result.pagePlans, purpose, next);
                }}
                onRequestAi={handleSuggestParts}
                aiLoading={suggestingParts}
                reasons={partReasons}
              />
            )}
            {previewSurface === "PRODUCT"
              && result.previewMode !== "OPERATION_PREVIEW"
              && isAbsoluteHttpUrl(apiBaseUrl) ? (
              <ProductExperienceRuntime
                scenarios={result.scenarios}
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
            ) : previewSurface === "SCENARIO"
              && result.previewMode !== "OPERATION_PREVIEW"
              && isAbsoluteHttpUrl(apiBaseUrl) ? (
              <ScenarioWorkbench
                scenarios={result.scenarios}
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
            ) : result.pages.length > 0 && (
              <ProductShell purpose={purpose} pages={result.pages} activePageId={previewPageId} onSelectPage={setPreviewPageId}>
                {previewPageId && isAbsoluteHttpUrl(apiBaseUrl) && (
                  <div className="rounded-md border border-line-strong bg-white/[0.02] p-4">
                    <PreviewPageRenderer
                      page={result.pages.find((p) => p.id === previewPageId)!}
                      pagePlan={activePagePlan}
                      capabilities={result.capabilities}
                      blocks={pageBlocks[previewPageId] ?? []}
                      selectedRow={effectiveSelectedRow}
                      onSelectRow={selectRow}
                      routeParameters={activeRouteParameters}
                      onNavigate={navigatePreview}
                      flows={result.flows}
                      bindings={result.bindings}
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
              </ProductShell>
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
              <Button variant="primary" onClick={openDeploymentStep} disabled={result.status === "UNSUPPORTED"}>
                다음
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* 3단계 */}
      {step === 3 && result && (
        <section className="max-w-[900px] rounded-panel border border-line bg-panel p-5">
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

          {standalone && (
            <PreviewDeploymentTargetSection
              targetType={deploymentTargetType}
              onTargetTypeChange={changeDeploymentTargetType}
              vms={deploymentVms}
              selectedVmId={selectedDeploymentVmId}
              onSelectedVmIdChange={selectDeploymentVm}
              loading={deploymentVmsLoading}
              loaded={deploymentVmsLoaded}
              error={deploymentVmsError}
              onRetry={() => void loadDeploymentVms()}
            />
          )}

          <p className="mt-4 text-xs text-muted-soft">
            {result.pages.length}개 페이지, {result.capabilities.length}개 capability로 Vite+React 프로젝트를 생성합니다.
            {deploymentTargetType === "MANAGED"
              ? " 별도 VM 없이 격리된 관리형 Preview로 배포되며, FREE는 6시간·PRO는 24시간 후 자동 정리됩니다."
              : fixedVmId
                ? " 이 VM의 새 배포 대상으로 바로 추가되며 서브도메인이 발급됩니다."
                : " 선택한 내 인스턴스의 새 배포 대상으로 추가되며 서브도메인이 발급됩니다."}
          </p>

          {deployError && <p className="mt-3 text-xs text-danger">{deployError}</p>}

          <div className="mt-5 flex justify-between">
            <Button onClick={() => setStep(2)} disabled={deploying}>
              이전
            </Button>
            <Button
              variant="primary"
              onClick={handleDeploy}
              disabled={
                deploying
                || !targetName.trim()
                || !isAbsoluteHttpUrl(apiBaseUrl)
                || (deploymentTargetType === "USER_VM" && !selectedDeploymentVmId)
              }
            >
              {deploying ? "배포 요청 중..." : "배포"}
            </Button>
          </div>
        </section>
      )}
    </div>
  );
}
