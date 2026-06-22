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
