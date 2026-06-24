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
