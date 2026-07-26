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
  // PRO 플랜 전용 — 외부 노출 시 자동 접미사 없는 CNAME을 사용
  customSubdomain?: string;
}

export interface InfraSelection {
  type: string;
  version?: string;
}

// services/infrastructure는 D-1/D-3 렌더러 전용 세부 스키마라 프론트에서는 구조화하지 않고
// 생성된 JSON을 그대로 보여주고 편집 후 그대로 되돌려보내는 용도로만 사용함
export interface DeploymentSpec {
  schemaVersion: string;
  services: unknown[];
  infrastructure?: unknown[];
  network: string;
  externalNetwork?: boolean;
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

// Auto Preview (GamjaBox_2.0_Key_Features.md 1단계) — Backend/Ops의
// application/preview/analysis 패키지 record와 필드명을 1:1로 맞춤.
export type PreviewCapabilityType = "LIST" | "DETAIL" | "CREATE" | "UPDATE" | "DELETE" | "LOGIN";
export type PreviewPageSkeletonType = "AUTH_PAGE" | "RESOURCE_LIST" | "LIST_DETAIL" | "DASHBOARD";
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

export interface PreviewAnalysisResult {
  status: GenerationStatus;
  apiServerUrls: string[];
  capabilities: PreviewCapability[];
  pages: PreviewPageDraft[];
  unresolved: UnresolvedField[];
  warnings: string[];
  evidenceRefs: string[];
  authStrategy: PreviewAuthStrategy;
  generationMode: PreviewGenerationMode;
}

export interface PageReviewFinding {
  code: string;
  severity: "INFO" | "WARNING" | "CRITICAL";
  message: string;
  remediation: string;
}

// Direction Recovery Change Request Increment 3 — AiPageReviewer(코멘트만)와 달리 AiPagePlanner의
// 제안은 검증을 통과하면 실제로 pages를 대체한다. 실패하면 요청에 보낸 pages가 그대로 돌아오고
// generationMode는 RULE_BASED로 유지된다(SERVICE_AWARE를 사칭하지 않음).
export interface PreviewPlanResponse {
  pages: PreviewPageDraft[];
  decisions: string[];
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
}

// 11절 수동 DB 백업 — 덤프 파일은 VM 파일시스템에 저장되고 다운로드는 파일 브라우저 API를 재사용함
export interface DbBackupResponse {
  id: string;
  vmId: string;
  serviceName: string;
  dbType: string;
  filePath: string | null;
  fileSizeBytes: number | null;
  succeeded: boolean;
  errorMessage: string | null;
  createdAt: string;
}
