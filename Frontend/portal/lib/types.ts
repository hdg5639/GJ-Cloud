export interface VmResponse {
  id: string;
  name: string;
  planType: "FREE" | "PRO";
  status: "PENDING" | "CREATING" | "BOOTING" | "RUNNING" | "FAILED" | "DELETING" | "DELETED";
  internalIp: string | null;
  errorMessage: string | null;
  diskSizeGb: number;
  subdomain: string;
  needsReboot: boolean | null;
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
