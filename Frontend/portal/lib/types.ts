export type VmStatus =
  | "PENDING"
  | "CREATING"
  | "BOOTING"
  | "RUNNING"
  | "STARTING"
  | "STOPPING"
  | "STOPPED"
  | "SUSPENDING"
  | "SUSPENDED"
  | "FAILED"
  | "DELETING"
  | "DELETED";

export interface VmResponse {
  id: string;
  userId: string;
  name: string;
  planType: "FREE" | "PRO";
  status: VmStatus;
  internalIp: string | null;
  errorMessage: string | null;
  diskSizeGb: number;
  subdomain: string;
  needsReboot: boolean;
  createdAt: string;
}

export interface VmStatusEvent {
  vmId: string;
  status: string;
  internalIp: string | null;
  errorMessage: string | null;
  timestamp: string;
}

export interface VmCreateRequest {
  name: string;
  planType: "FREE" | "PRO";
  diskSizeGb: number;
  sshKeyId: string;
}

export interface VmAvailabilityResponse {
  free: { used: number; total: number; isFull: boolean };
  pro: { used: number; total: number; isFull: boolean };
}

export interface SshKeyResponse {
  id: string;
  name: string;
  fingerprint: string;
  publicKeyPreview: string;
  createdAt: string;
}

export interface SshKeyGenerateResponse extends SshKeyResponse {
  privateKey: string;
}

export interface ProfileResponse {
  userId: string;
  email: string;
  nickname: string | null;
  profileImageUrl: string | null;
  planType: string;
}

export interface AdminUserResponse {
  userId: string;
  email: string;
  nickname: string | null;
  planType: string;
  suspended: boolean;
  createdAt: string;
}

export interface AdminVmResponse {
  id: string;
  userId: string;
  name: string;
  planType: string;
  status: string;
  internalIp: string | null;
  subdomain: string | null;
  diskSizeGb: number;
  needsReboot: boolean | null;
  errorMessage: string | null;
  createdAt: string;
}

export interface UsageResponse {
  planType: string;
  vCpuLimit: number;
  ramGbLimit: number;
  myFreeCount: number;
  maxFreeVmCount: number;
  myProCount: number;
  maxProVmCount: number;
  systemFreeCount: number;
  systemProCount: number;
}

export interface VmMetricsCurrentResponse {
  vmId: string;
  status: string;
  cpuUsagePercent: number;
  allocatedCpu: number;
  memoryUsedBytes: number;
  memoryAllocatedBytes: number;
  diskUsedBytes: number;
  diskAllocatedBytes: number;
  networkInBytes: number;
  networkOutBytes: number;
  uptimeSeconds: number;
  timestamp: number;
}

export interface VmMetricsHistoryResponse {
  vmId: string;
  timeframe: string;
  dataPoints: Array<{
    timestamp: number;
    cpuPercent: number;
    memoryUsedBytes: number;
    networkInBytes: number;
    networkOutBytes: number;
    diskReadBytes: number;
    diskWriteBytes: number;
  }>;
}

export interface UpgradeRequestResponse {
  id: string;
  userId: string;
  type: "UPGRADE" | "DOWNGRADE";
  targetPlanType: "FREE" | "PRO";
  status: "PENDING" | "APPROVED" | "REJECTED";
  reason: string | null;
  createdAt: string;
  reviewedAt: string | null;
  reviewedBy: string | null;
}

export interface PagedResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
  empty: boolean;
}

// ── Organization ──────────────────────────────────────────────────────────────

export type MemberRole = "OWNER" | "ADMIN" | "MEMBER";
export type MemberStatus = "PENDING" | "ACCEPTED" | "REJECTED";

export interface MemberResponse {
  id: string;
  email: string;
  userId: string | null;
  nickname: string | null;
  profileImageUrl: string | null;
  role: MemberRole;
  status: MemberStatus;
  invitedAt: string;
  joinedAt: string | null;
}

// 조직 초대용 사용자 검색 결과
export interface MemberSearchResult {
  userId: string;
  nickname: string | null;
  email: string;
  profileImageUrl: string | null;
}

export interface OrgResponse {
  id: string;
  name: string;
  ownerId: string;
  myRole: MemberRole;
  memberCount: number;
  vmCount: number;
  createdAt: string;
  pendingMemberId?: string;
}

export interface OrgDetailResponse {
  id: string;
  name: string;
  ownerId: string;
  myRole: MemberRole;
  members: MemberResponse[];
  vms: VmResponse[];
  createdAt: string;
}

// ── Collaboration ─────────────────────────────────────────────────────────────

export type ScopeType = "ORGANIZATION" | "INSTANCE";
export type CollaborationType = "NOTE" | "NOTICE" | "REQUEST";
export type CollaborationStatus = "UNSOLVED" | "SOLVED";

export interface CollaborationResponse {
  id: string;
  scopeType: ScopeType;
  scopeId: string;
  type: CollaborationType;
  tag: string | null;
  title: string;
  content: string;
  status: CollaborationStatus | null;
  pinned: boolean;
  createdById: string;
  createdByEmail: string;
  resolvedById: string | null;
  resolvedByEmail: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TagResponse {
  id: string;
  scopeType: ScopeType;
  scopeId: string;
  name: string;
  usageCount: number;
}

export interface FileEntry {
  name: string;
  path: string;
  directory: boolean;
  size: number;
  modifiedAt: string;
}

export interface FileListResponse {
  path: string;
  entries: FileEntry[];
}

export interface FileContentResponse {
  path: string;
  content: string;
}

// Docker 관리 — Ops가 `docker ps/images/network/compose ls --format json` 결과를 그대로 파싱해
// 응답하므로, 필드명이 camelCase가 아니라 docker CLI의 원본 JSON 키(PascalCase)와 동일함.
export interface ContainerInfo {
  ID: string;
  Image: string;
  Names: string;
  Command: string;
  Status: string;
  State: string;
  Ports: string;
  CreatedAt: string;
}

export interface ImageInfo {
  ID: string;
  Repository: string;
  Tag: string;
  Size: string;
  CreatedAt: string;
}

export interface NetworkInfo {
  ID: string;
  Name: string;
  Driver: string;
  Scope: string;
}

export interface ComposeStackInfo {
  Name: string;
  Status: string;
  ConfigFiles: string;
}

export interface DockerStatusResponse {
  installed: boolean;
  installing: boolean;
  stage: string | null;
  lastError: string | null;
}

// 배포 파이프라인 (D-2 Raw Compose / D-1·D-3 DeploymentSpec 공용)
export interface DeploymentResponse {
  id: string;
  vmId: string;
  deploymentTargetId: string | null;
  triggerType: "MANUAL" | "GIT_PUSH" | "RETRY" | "ROLLBACK";
  requestedRevision: string | null;
  status: string;
  sourceType: "TEMPLATE_SPEC" | "AI_SPEC" | "RAW_COMPOSE";
  sourceRevision: string | null;
  releaseDir: string | null;
  previousDeploymentId: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  deployedAt: string | null;
}

export interface DeploymentTargetResponse {
  id: string;
  vmId: string;
  name: string;
  repositoryUrl: string;
  repositoryFullName: string | null;
  branch: string;
  sourceType: "TEMPLATE_SPEC" | "AI_SPEC" | "RAW_COMPOSE";
  autoDeployEnabled: boolean;
  latestRequestedRevision: string | null;
  latestDeployedRevision: string | null;
  latestDeploymentId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface GithubInstallationResponse {
  installationId: number;
  accountLogin: string;
  accountType: string;
}

export interface GithubRepositoryResponse {
  id: number;
  installationId: number;
  fullName: string;
  cloneUrl: string;
  defaultBranch: string;
  privateRepository: boolean;
}

export interface GithubInstallationCompleteResponse {
  installations: GithubInstallationResponse[];
  vmId: string;
}

export interface EnvironmentFile {
  vmPath: string;
  content: string;
}

export interface ExposedRoute {
  serviceName: string;
  port: number;
  protocol: string;
  visibility: string;
  nickname: string;
  // PRO 플랜 전용 — 비워두면 기존처럼 {서브도메인}-{닉네임}으로 자동 생성
  customSubdomain?: string;
}

export interface HealthCheck {
  serviceName: string;
  path: string;
  hostPort?: number;
  containerPort?: number;
}

export interface ServiceCard {
  name: string;
  runtime: string;
  context: string;
  containerPort: number;
  javaVersion?: number;
  buildTool?: string;
  nodeVersion?: number;
  buildCommand?: string;
  startCommand?: string;
  pythonVersion?: string;
  pythonFramework?: string;
  expose: boolean;
}

export interface InfraSelection {
  type: string;
  version?: string;
}

// services/infrastructure는 D-1/D-3 렌더러 전용 세부 스키마라 프론트에서는 구조화하지 않고
// 생성된 JSON을 그대로 보여주고 편집 후 그대로 되돌려보내는 용도로만 사용함
export interface DeploymentSpec {
  schemaVersion: string;
  services: DeploymentSpecService[];
  infrastructure?: unknown[];
  network: string;
  externalNetwork?: boolean;
}

export interface DeploymentSpecService {
  name: string;
  deploymentMode: string;
  build: unknown;
  artifact: unknown;
  run: {
    runtime?: string;
    strategy?: string;
    containerPort?: number;
  };
  context: string;
  expose?: {
    enabled: boolean;
    protocol?: string | null;
    healthCheckPath?: string | null;
    customSubdomain?: string | null;
    routePath?: string | null;
    stripPrefix?: boolean | null;
    // 통합 Caddy 라우팅에서 이 서비스의 노출 방식. "PREFIX"(경로) | "DOMAIN"(전용 서브도메인).
    routeMode?: "PREFIX" | "DOMAIN" | null;
  } | null;
}

// AI 자동생성 파이프라인 개선(결정론적 저장소 분석 + 명시적 불확실성 상태) — 생성 결과가 항상
// 완전한 스펙은 아님. status가 READY가 아니면 spec은 null이고 unresolved 사유를 보여줘야 함.
export type GenerationStatus = "READY" | "NEEDS_INPUT" | "UNSUPPORTED" | "CONFLICT" | "INVALID_RESPONSE";

export interface UnresolvedField {
  field: string;
  code: string;
  reason: string;
}

export interface AiGenerationResult {
  status: GenerationStatus;
  spec: DeploymentSpec | null;
  unresolved: UnresolvedField[];
  warnings: string[];
  evidenceRefs: string[];
}

export interface ComposeReviewFinding {
  code: string;
  severity: "INFO" | "WARNING" | "CRITICAL";
  service: string;
  location: string;
  message: string;
  remediation: string;
  confidence: "LOW" | "MEDIUM" | "HIGH";
  evidence: string;
}

export interface DetectedComposeFile {
  path: string;
  directory: string;
  content: string;
  sizeBytes: number;
  primary: boolean;
}

export interface ComposeDetectionResult {
  detected: boolean;
  searchedContext: string;
  files: DetectedComposeFile[];
  discoveredServices?: DiscoveredService[];
  warnings: string[];
}

export interface DiscoveredService {
  name: string;
  context: string;
  runtime: string;
  containerPort: number;
  expose: boolean;
  confidence: string;
  evidence: string[];
}

export type ComposeRouterPlanStatus =
  | "ADDED"
  | "ALREADY_CONFIGURED"
  | "NOT_REQUIRED"
  | "NEEDS_INPUT";

export interface ComposeRouterRoute {
  serviceName: string;
  routePath: string | null;
  upstream: string;
  containerPort: number;
  hostPort: number | null;
  root: boolean;
  stripPrefix: boolean;
  source: "USER" | "HEALTHCHECK" | "SERVICE_NAME" | "ROOT_DEFAULT" | "DIRECT" | "EXISTING_ROUTER";
  confidence: "LOW" | "MEDIUM" | "HIGH";
  // "PREFIX"(경로 기반) | "DOMAIN"(호스트 기반). DOMAIN이면 customSubdomain으로 노출된다.
  mode: "PREFIX" | "DOMAIN";
  customSubdomain: string | null;
}

export interface ComposeRouterRouteOverride {
  mode: "PREFIX" | "DOMAIN";
  routePath?: string | null;
  stripPrefix: boolean;
  customSubdomain?: string | null;
}

export interface ComposeRouterUnresolvedService {
  serviceName: string;
  reason: string;
  portRequired: boolean;
}

export interface ComposeRouterPlanResult {
  status: ComposeRouterPlanStatus;
  enhancedComposeContent: string;
  routerConfig: string;
  routerServiceName: string;
  routerHostPort: number | null;
  routerContainerPort: number | null;
  routes: ComposeRouterRoute[];
  unresolvedServices: ComposeRouterUnresolvedService[];
  warnings: string[];
}

// Auto Preview (GamjaBox_2.0_Key_Features.md 1단계) — Backend/Ops의
// application/preview/analysis 패키지 record와 필드명을 1:1로 맞춤.
export type PreviewCapabilityType = "LIST" | "DETAIL" | "CREATE" | "UPDATE" | "DELETE" | "LOGIN";
export type PreviewPageSkeletonType = "AUTH_PAGE" | "RESOURCE_LIST" | "RESOURCE_DETAIL" | "LIST_DETAIL" | "DASHBOARD";
// GamjaBox_2.0_Key_Features.md 2절 — BlueprintCompiler가 목적별 Component Variant를 고르는 데 쓴다.
export type PreviewGenerationPurpose = "API_TEST" | "PRODUCT_LIKE" | "ADMIN";
// auto-preview-design/05-capability-taxonomy.md §5·6 — CapabilityType별 고정 기본값만 배정한다.
// IRREVERSIBLE/EXTERNAL_SIDE_EFFECT는 OpenAPI만으로 판별 근거가 없어 규칙 기반으로는 배정하지 않는다.
export type PreviewRiskLevel = "SAFE" | "STATE_CHANGING" | "DESTRUCTIVE" | "IRREVERSIBLE" | "EXTERNAL_SIDE_EFFECT";
export type PreviewAutomationPolicy =
  | "AUTO_SAFE"
  | "USER_INITIATED"
  | "EXPLICIT_CONFIRMATION"
  | "TYPED_CONFIRMATION"
  | "DISABLED_IN_AUTO_TEST";
// GamjaBox_Auto_Preview_Direction_Recovery_Change_Request.md §7.1 — capability의 진짜 정체성.
// kind=COMMAND는 blueprint.ts의 resolveBlocks가 quick-action-button-group Block으로 그린다.
export type PreviewCapabilityKind =
  | "QUERY"
  | "MUTATION"
  | "COMMAND"
  | "AUTH"
  | "METRIC"
  | "EVENT_STREAM"
  | "FILE_TRANSFER"
  | "WORKFLOW";

// auto-preview-design/04-api-binding-schema.md §9 — 로그인으로 받은 토큰을 나머지 모든 보호된 요청에
// 어떻게 실어 보낼지. Capability 하나가 아니라 분석 결과 전체에 하나만 존재한다.
export type PreviewAuthStrategyType = "NONE" | "BEARER" | "API_KEY_HEADER" | "API_KEY_QUERY";
export interface PreviewAuthStrategy {
  type: PreviewAuthStrategyType;
  headerName: string | null;
  prefix: string | null;
  queryParamName: string | null;
}

export interface PreviewCapability {
  id: string;
  resourceName: string;
  // kind=COMMAND일 때는 null — CRUD 6종 enum에 억지로 끼워 넣지 않는다.
  type: PreviewCapabilityType | null;
  operationId: string | null;
  path: string;
  method: string;
  hasSearch: boolean;
  hasSort: boolean;
  hasPagination: boolean;
  confidence: string;
  evidence: string[];
  fields: string[];
  // LOGIN 응답에서 access token이 위치한 dot-path(예: "data.accessToken"). 분석 단계에서 이름 힌트로
  // 못 찾으면 null이고 unresolved에 "auth.login.accessTokenPath"가 함께 온다 — 위자드에서 사용자가
  // 직접 지정하면 이 필드를 덮어써서 review/deploy 요청에 그대로 실어 보낸다.
  accessTokenPath: string | null;
  // hasSearch=true일 때 실제 쿼리 파라미터 이름(예: "keyword"). "search"로 고정해서 보내면 API가 다른
  // 이름을 쓸 때 검색이 조용히 실패하므로 렌더러는 이 값을 그대로 써야 한다.
  searchParam: string | null;
  risk: PreviewRiskLevel;
  automationPolicy: PreviewAutomationPolicy;
  // LIST 응답에서 실제 배열이 위치한 dot-path(예: "data.content"). 못 찾으면 null이고 렌더러가
  // 기존 재귀 휴리스틱(extractArray)으로 대체한다.
  collectionPath: string | null;
  // 같은 응답에서 총 개수가 위치한 dot-path(예: "data.totalElements"). 못 찾으면 null이고 렌더러가
  // 배열 길이로 대체한다(extractCount).
  totalCountPath: string | null;
  kind: PreviewCapabilityKind;
  // kind=COMMAND일 때 실제 동작 이름(예: "start", "invite"). 그 외 kind는 항상 null.
  action: string | null;
  // 이 capability가 의미상 의존하는 다른 capability id 목록(예: vm.start → ["vm.detail"]).
  dependencies: string[];
}

export interface PreviewPageDraft {
  id: string;
  title: string;
  skeleton: PreviewPageSkeletonType;
  capabilityIds: string[];
}

// Direction Recovery Change Request §17 — 이 pages가 어떻게 만들어졌는지 항상 명시적으로 리포트한다.
// FALLBACK_CRUD를 SERVICE_AWARE인 것처럼 보여주면 안 된다.
export type PreviewGenerationMode = "SERVICE_AWARE" | "RULE_BASED" | "FALLBACK_CRUD";

// Workflow Composition Phase 2 Change Request §5/WP-1 — PreviewPageDraft(4필드)를 대체할 풍부한 페이지
// 모델. 지금은 백엔드 PagePlanMapper가 결정론적으로 파생만 하고 어떤 화면도 아직 이 데이터를 쓰지
// 않는다(Navigation/FlowBlueprint가 실제로 소비하게 될 다음 작업들을 위해 타입만 미리 맞춰둠).
export type PreviewPageType =
  | "AUTH"
  | "DASHBOARD"
  | "RESOURCE_LIST"
  | "RESOURCE_OVERVIEW"
  | "RESOURCE_DETAIL"
  | "LIST_DETAIL"
  | "WORKFLOW"
  | "SETTINGS"
  | "ACTIVITY"
  | "FILE_MANAGER"
  | "ORGANIZATION";

// source는 문서 예시가 "navigation" 하나뿐이라 닫힌 유니언 대신 string으로 둔다(백엔드와 동일).
export interface PreviewRouteParameter {
  name: string;
  source: string;
}

export interface PreviewNavigationRule {
  sourcePageId: string;
  trigger: string;
  type: "OPEN_PAGE" | "OPEN_OVERLAY" | "GO_BACK" | "REPLACE_ROUTE";
  targetPageId: string;
  parameters: Record<string, string>;
}

export interface PreviewPagePlan {
  id: string;
  title: string;
  route: string;
  pageType: PreviewPageType;
  layoutRef: string | null;
  capabilityIds: string[];
  routeParameters: PreviewRouteParameter[];
  queryParameters: string[];
  navigationRules: PreviewNavigationRule[];
  features: Record<string, boolean>;
  confidence: string;
  reason: string;
  unsupportedCapabilityWarnings: string[];
}

// Workflow Composition Phase 2 Change Request §6(FlowBlueprint)/§8(ApiBinding)/§9(Polling) — Backend
// gj.cloud.ops.application.preview.flow.*/binding.ApiBinding의 TS 미러. RuleBasedFlowGenerator가
// 이 값들을 처음 실제로 만들어내면서(그 전까지는 이 모델을 만드는 생성기가 없어 네트워크로 전송되는
// 형태 자체가 없었다) 여기서 처음 정본으로 정의한다 — components/preview-runtime/flow/types.ts는
// 이 타입들을 재수출만 한다(다른 Preview* 타입과 같은 관례, components/preview-runtime/types.ts 참고).
export type PreviewFlowStepType =
  | "API_CALL"
  | "SET_CONTEXT"
  | "NAVIGATE"
  | "POLL"
  | "WAIT"
  | "CONDITION"
  | "SHOW_SUCCESS"
  | "SHOW_ERROR"
  | "REFRESH_BINDING"
  | "EVENT_STREAM"
  | "UPLOAD"
  | "DOWNLOAD"
  | "PARALLEL";

export interface PreviewPollCondition {
  path: string;
  equalsValue: string | null;
  in: string[] | null;
}

export interface PreviewFlowStep {
  id: string;
  type: PreviewFlowStepType;
  bindingRef: string | null;
  input: Record<string, string> | null;
  values: Record<string, string> | null;
  pageId: string | null;
  parameters: Record<string, string> | null;
  until: PreviewPollCondition[] | null;
  intervalMs: number | null;
  timeoutSeconds: number | null;
  condition: string | null;
  message: string | null;
}

export interface PreviewFlowTrigger {
  pageId: string | null;
  actionId: string | null;
}

export interface PreviewFlowBlueprint {
  id: string;
  trigger: PreviewFlowTrigger | null;
  steps: PreviewFlowStep[];
}

export type PreviewInputTarget = "PATH" | "QUERY" | "BODY" | "HEADER";

export interface PreviewInputMapping {
  target: string;
  targetKind: PreviewInputTarget;
  from: string;
}

export interface PreviewOutputMapping {
  from: string;
  to: string;
}

export interface PreviewApiBinding {
  id: string;
  capabilityId: string;
  inputMappings: PreviewInputMapping[];
  outputMappings: PreviewOutputMapping[];
  refreshBindingIds: string[];
}

export interface PreviewAnalysisResult {
  status: GenerationStatus;
  apiServerUrls: string[];
  capabilities: PreviewCapability[];
  availableCapabilities: PreviewCapability[];
  pages: PreviewPageDraft[];
  pagePlans: PreviewPagePlan[];
  // RuleBasedFlowGenerator가 pagePlans로부터 만든 것 중 검증을 통과한 항목만 담긴다(실패분은
  // 서버가 조용히 드랍하고 warnings에 사유를 남긴다).
  flows: PreviewFlowBlueprint[];
  bindings: PreviewApiBinding[];
  unresolved: UnresolvedField[];
  warnings: string[];
  evidenceRefs: string[];
  authStrategy: PreviewAuthStrategy;
  generationMode: PreviewGenerationMode;
  serviceUnderstanding: PreviewServiceUnderstanding;
  scenarios: PreviewCompiledScenario[];
  scenarioDiagnostics: PreviewScenarioDiagnostic[];
  previewMode: PreviewMode;
  scenarioPlanningSource: "LLM" | "RULE_BASED" | "OPERATION_ONLY";
  scenarioPromptVersion: string | null;
  activeCapabilityIds: string[];
  resolvedServiceDescription: string;
  serviceContextSources: Array<
    "USER_DESCRIPTION" | "SCENARIO_INTENT" | "DOCUMENTATION_PAGE" | "OPENAPI_INFO"
  >;
}

// Scenario-first Auto Preview Runtime v3 — Backend ScenarioModels의 TypeScript mirror.
export type PreviewMode = "SCENARIO_PREVIEW" | "INFERRED_SCENARIO_PREVIEW" | "OPERATION_PREVIEW";
export type PreviewScenarioStageRole =
  | "ENTRY"
  | "AUTHENTICATE"
  | "SELECT_CONTEXT"
  | "DISCOVER"
  | "INSPECT"
  | "SELECT"
  | "COMPARE"
  | "ACCUMULATE"
  | "CONFIGURE"
  | "PREPARE"
  | "REVIEW"
  | "COMMIT"
  | "WAIT"
  | "VERIFY"
  | "TRACK"
  | "RECOVER"
  | "CONTINUE"
  | "COMPLETE";
export type PreviewScenarioCompilationStatus = "EXECUTABLE" | "PARTIALLY_SUPPORTED" | "UNSUPPORTED";
export type PreviewScenarioDiagnosticStatus = "SUPPORTED" | "PARTIALLY_SUPPORTED" | "UNSUPPORTED";
export type PreviewScenarioResolutionStrategy =
  | "REMOVE_STAGE"
  | "MERGE_STAGE"
  | "REPLACE_WITH_LOCAL_STATE"
  | "REPLACE_VERIFICATION_METHOD"
  | "DOWNGRADE_TO_READ_ONLY"
  | "MARK_AS_UNSUPPORTED"
  | "REQUEST_MANUAL_BINDING";
export type PreviewVerificationType =
  | "HTTP_STATUS_MATCH"
  | "RESPONSE_SCHEMA_VALID"
  | "RESOURCE_EXISTS"
  | "RESOURCE_NOT_EXISTS"
  | "FIELD_EQUALS"
  | "STATE_EQUALS"
  | "COLLECTION_CONTAINS"
  | "COLLECTION_EXCLUDES"
  | "OUTPUT_EXTRACTABLE";
export type PreviewScenarioBindingTarget = "PATH" | "QUERY" | "BODY" | "HEADER";

export interface PreviewServiceActor {
  id: string;
  label: string;
}

export interface PreviewServiceUnderstanding {
  domain: string;
  serviceType: string;
  actors: PreviewServiceActor[];
  coreEntities: string[];
  primaryGoals: string[];
  confidence: number;
  evidence: string[];
}

export interface PreviewScenarioInputBinding {
  target: string;
  targetKind: PreviewScenarioBindingTarget;
  source: string;
  required: boolean;
}

export interface PreviewScenarioOutputBinding {
  fromCandidates: string[];
  to: string;
  sensitive: boolean;
}

export interface PreviewVerificationContract {
  type: PreviewVerificationType;
  capabilityId: string | null;
  responsePath: string | null;
  expectedSource: string | null;
  acceptedValues: string[];
  required: boolean;
}

export interface PreviewCompiledScenarioStage {
  id: string;
  role: PreviewScenarioStageRole;
  intent: string;
  capabilityId: string | null;
  operationId: string | null;
  optional: boolean;
  inputs: string[];
  outputs: string[];
  nextStageIds: string[];
  inputBindings: PreviewScenarioInputBinding[];
  outputBindings: PreviewScenarioOutputBinding[];
  verification: PreviewVerificationContract | null;
  risk: PreviewRiskLevel;
}

export interface PreviewScenarioDiagnostic {
  scenarioId: string | null;
  stageId: string | null;
  status: PreviewScenarioDiagnosticStatus;
  message: string;
  resolution: PreviewScenarioResolutionStrategy;
  replacementCapabilityId: string | null;
}

export interface PreviewCompiledScenario {
  id: string;
  name: string;
  actor: string;
  goal: string;
  entryStageId: string | null;
  stages: PreviewCompiledScenarioStage[];
  scenarioState: string[];
  status: PreviewScenarioCompilationStatus;
  diagnostics: PreviewScenarioDiagnostic[];
  confidence: number;
  schemaVersion: string;
  runtimeVersion: string;
}

export interface PreviewScenarioStagePlan {
  id: string;
  role: PreviewScenarioStageRole;
  intent: string;
  capabilityRequirement: string | null;
  required: boolean;
  inputs: string[];
  outputs: string[];
  nextStageIds: string[];
  verificationIntent: PreviewVerificationType | null;
}

export interface PreviewScenarioPlan {
  id: string;
  name: string;
  actor: string;
  goal: string;
  entryConditions: string[];
  stages: PreviewScenarioStagePlan[];
  scenarioState: string[];
  confidence: number;
  evidence: string[];
}

export type CustomScenarioStatus =
  | "GENERATING"
  | "DRAFT"
  | "VALIDATING"
  | "VALIDATED"
  | "ACTIVE"
  | "INVALIDATED"
  | "ARCHIVED";

export type CustomScenarioVisibility = "PRIVATE" | "TEAM";

export interface CustomScenarioView {
  id: string;
  serviceId: string;
  name: string;
  description: string | null;
  naturalLanguageSource: string;
  status: CustomScenarioStatus;
  visibility: CustomScenarioVisibility;
  definition: PreviewScenarioPlan;
  revision: number;
  openapiFingerprint: string;
  compiledScenario: PreviewCompiledScenario;
  valid: boolean;
  validationErrors: string[];
  createdAt: string;
  updatedAt: string;
}

export interface CustomScenarioExport {
  format: "gamjabox.custom-scenario.v1";
  name: string;
  description: string | null;
  naturalLanguageSource: string;
  visibility: CustomScenarioVisibility;
  definition: PreviewScenarioPlan;
}

export interface RegressionSuiteView {
  id: string;
  serviceId: string;
  name: string;
  description: string | null;
  apiDocsUrl: string;
  apiBaseUrl: string;
  scenarioIds: string[];
  deploymentTargetId: string | null;
  runOnDeployment: boolean;
  allowStateChangingOnDeployment: boolean;
  createdAt: string;
  updatedAt: string;
}

export type RegressionRunStatus = "QUEUED" | "RUNNING" | "PASSED" | "FAILED";
export type RegressionTriggerType = "MANUAL" | "CI" | "DEPLOYMENT";
export type ScenarioExecutionStatus = "RUNNING" | "PASSED" | "FAILED" | "SKIPPED";

export interface RegressionScenarioExecutionView {
  id: string;
  scenarioId: string;
  scenarioRevisionId: string;
  status: ScenarioExecutionStatus;
  inputSnapshot: unknown;
  stateSnapshot: unknown;
  result: unknown;
  failureStageId: string | null;
  failureRequest: unknown;
  startedAt: string;
  completedAt: string;
}

export interface RegressionRunView {
  id: string;
  suiteId: string;
  status: RegressionRunStatus;
  triggerType: RegressionTriggerType;
  triggerReference: string | null;
  totalCount: number;
  passedCount: number;
  failedCount: number;
  summary: unknown;
  executions: RegressionScenarioExecutionView[];
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

export type PreviewScenarioStageExecutionStatus =
  | "IDLE"
  | "WAITING_INPUT"
  | "RUNNING"
  | "SUCCESS"
  | "FAILED"
  | "SKIPPED"
  | "CANCELLED";

export interface PreviewScenarioAssertionResult {
  type: PreviewVerificationType;
  passed: boolean;
  message: string;
  actual: unknown;
  expected: unknown;
}

export interface PreviewScenarioStageExecution {
  stageId: string;
  status: PreviewScenarioStageExecutionStatus;
  operationId: string | null;
  method: string | null;
  url: string | null;
  request: {
    path: Record<string, string>;
    query: Record<string, string>;
    headers: Record<string, string>;
    body: Record<string, unknown>;
  } | null;
  response: unknown;
  responseHeaders: Record<string, string>;
  extractedOutputs: Record<string, unknown>;
  assertions: PreviewScenarioAssertionResult[];
  durationMs: number | null;
  error: string | null;
  startedAt: number | null;
  completedAt: number | null;
}

export interface PageReviewFinding {
  code: string;
  severity: "INFO" | "WARNING" | "CRITICAL";
  message: string;
  remediation: string;
}

// Direction Recovery Change Request Increment 5(2부) "Plan Review UI" — AiPageReviewer(코멘트만)와
// 달리 AiPagePlanner의 제안은 사용자가 검토해 실제로 pages를 바꿀 수 있다. propose(AI 호출, 아무것도
// 적용 안 함) / apply(사용자가 고른 서브셋만 결정론적으로 적용) 두 단계로 나뉜다.
export type PagePlanOperationType =
  | "RENAME_PAGE"
  | "MERGE_PAGES"
  | "MOVE_CAPABILITY"
  | "ADD_PAGE"
  | "REMOVE_PAGE"
  | "SPLIT_PAGE"
  | "SET_PAGE_TYPE"
  | "SET_LAYOUT"
  | "SET_FEATURE"
  | "ADD_NAVIGATION"
  | "ADD_FLOW"
  | "ASSIGN_FLOW";

// /plan/apply 요청에 보낼 원본 오퍼레이션 — 타입마다 실제로 쓰는 필드가 다르고 그 외는 항상 null.
export interface PagePlanOperation {
  type: PagePlanOperationType;
  pageId: string | null;
  otherPageId: string | null;
  newTitle: string | null;
  capabilityId: string | null;
  destinationPageId: string | null;
  capabilityIds: string[] | null;
  pageType: PreviewPageType | null;
  layoutRef: string | null;
  featureKey: string | null;
  featureEnabled: boolean | null;
  navigationRule: PreviewNavigationRule | null;
  flow: PreviewFlowBlueprint | null;
  flowId: string | null;
  actionId: string | null;
  reason: string | null;
}

// /plan/propose 응답의 오퍼레이션 — PagePlanOperation에 검토용 필드(id/valid/validationError)가 더 있다.
export interface PagePlanOperationView extends PagePlanOperation {
  id: string;
  valid: boolean;
  validationError: string | null;
}

export interface PagePlanProposalResult {
  operations: PagePlanOperationView[];
  aiSucceeded: boolean;
}

// /parts/suggest 응답 — 스왑 가능한 Block별 검증된 파츠 추천. componentId는 그 Block과 호환되는 등록
// 파츠 id(또는 현재 기본 컴포넌트 id)이며, {pageId}/{instanceId}를 키로 partOverrides에 그대로 병합한다.
export interface PartSuggestion {
  pageId: string;
  instanceId: string;
  componentId: string;
  reason: string | null;
}

export interface PartSuggestionResult {
  suggestions: PartSuggestion[];
  aiSucceeded: boolean;
  compositionFindings: Array<{
    severity: "WARNING" | "ERROR";
    code: string;
    message: string;
    groupIds: string[];
    reselectableGroupIds: string[];
  }>;
  reselectedGroups: string[];
  selectionStrategy: string | null;
}

// errors가 비어있지 않으면(all-or-nothing 실패) pages는 요청으로 보낸 pages 그대로다. pagePlans도
// 이 pages와 동일한 기준으로 파생된다(Workflow Composition Phase 2 WP-1).
export interface PreviewPlanApplyResponse {
  pages: PreviewPageDraft[];
  pagePlans: PreviewPagePlan[];
  flows: PreviewFlowBlueprint[];
  bindings: PreviewApiBinding[];
  decisions: string[];
  errors: string[];
  generationMode: PreviewGenerationMode;
}

export interface DeploymentEventPayload {
  sequence: number;
  eventType: "STAGE_CHANGE" | "BUILD_LOG" | "ERROR" | "DONE";
  message: string;
  payload: string | null;
  createdAt: string;
}

// 재시도/수정 후 재배포용 — repoUrl/branch/patToken은 서버에 저장되지 않아 포함되지 않음(재입력 필요)
export interface ComposeSpecResponse {
  composeContent: string;
  environmentFiles: EnvironmentFile[];
  exposedRoutes: ExposedRoute[];
  healthChecks: HealthCheck[];
  context: string | null;
  installPath: string | null;
  routerPlan?: ComposeRouterPlanResult | null;
}

// 11절 수동 DB 백업 — AES-GCM 암호문은 전용 id 기반 API에서만 복호화 다운로드함
export interface DbBackupResponse {
  id: string;
  vmId: string;
  serviceName: string;
  dbType: string;
  fileSizeBytes: number | null;
  checksumSha256: string | null;
  encryptionVersion: string | null;
  verifiedAt: string | null;
  expiresAt: string | null;
  succeeded: boolean;
  errorMessage: string | null;
  createdAt: string;
}

export type DocsArticleStatus = "DRAFT" | "PUBLISHED";

export interface DocsArticleSummary {
  id: string;
  slug: string;
  title: string;
  summary: string;
  category: string;
  coverImageUrl: string | null;
  tags: string[];
  status: DocsArticleStatus;
  featured: boolean;
  sortOrder: number;
  viewCount: number;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DocsArticle extends DocsArticleSummary {
  content: string;
  authorId: string;
}

export interface DocsCategory {
  name: string;
  articleCount: number;
}

export interface DocsArticleInput {
  slug?: string;
  title: string;
  summary: string;
  category: string;
  coverImageUrl?: string | null;
  content: string;
  tags: string[];
  featured: boolean;
  sortOrder: number;
}

export interface DocsImageUpload {
  url: string;
  filename: string;
}
