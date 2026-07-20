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
  role: MemberRole;
  status: MemberStatus;
  invitedAt: string;
  joinedAt: string | null;
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
}

// 배포 파이프라인 (D-2 Raw Compose / D-1·D-3 DeploymentSpec 공용)
export interface DeploymentResponse {
  id: string;
  vmId: string;
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
