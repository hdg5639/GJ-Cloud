import type {
  VmResponse,
  VmCreateRequest,
  VmAvailabilityResponse,
  SshKeyResponse,
  SshKeyGenerateResponse,
  ProfileResponse,
} from "./types";

const API_BASE = {
  auth: process.env.NEXT_PUBLIC_AUTH_API!,
  user: process.env.NEXT_PUBLIC_USER_API!,
  vm: process.env.NEXT_PUBLIC_VM_API!,
};

// service → targetService 이름 매핑
const SERVICE_AUDIENCE: Partial<Record<keyof typeof API_BASE, string>> = {
  user: "user-service",
  vm: "vm-service",
};

interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string | null;
}

// accessToken + targetService 조합으로 캐싱 (access token이 바뀌면 자연히 무효화)
const exchangeCache = new Map<string, string>();

export async function getExchangedToken(accessToken: string, targetService: string): Promise<string> {
  const cacheKey = `${accessToken}:${targetService}`;
  const cached = exchangeCache.get(cacheKey);
  if (cached) return cached;

  const res = await fetch(`${API_BASE.auth}/auth/token/exchange`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ targetService }),
  });

  if (!res.ok) throw new Error("토큰 교환에 실패했습니다");

  const body: ApiResponse<{ accessToken: string }> = await res.json();
  const token = body.data.accessToken;
  exchangeCache.set(cacheKey, token);
  return token;
}

async function request<T>(
  service: keyof typeof API_BASE,
  path: string,
  options: RequestInit & { accessToken?: string } = {}
): Promise<T> {
  const { accessToken, ...init } = options;

  let token = accessToken;
  const targetAudience = SERVICE_AUDIENCE[service];
  if (accessToken && targetAudience) {
    token = await getExchangedToken(accessToken, targetAudience);
  }

  const res = await fetch(`${API_BASE[service]}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init.headers as Record<string, string> | undefined),
    },
  });

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: "요청에 실패했습니다" }));
    throw new Error(error.message ?? "요청에 실패했습니다");
  }

  const body: ApiResponse<T> = await res.json();
  return body.data;
}

export const api = {
  auth: {
    login: (email: string, password: string) =>
      request<{ accessToken: string; tokenType: string; expiresIn: number }>(
        "auth",
        "/auth/login",
        { method: "POST", body: JSON.stringify({ email, password }) }
      ),
    register: (email: string, password: string) =>
      request<void>("auth", "/auth/register", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      }),
    verifyEmail: (email: string, code: string) =>
      request<void>("auth", "/auth/email/verify/confirm", {
        method: "POST",
        body: JSON.stringify({ email, code }),
      }),
    resendVerification: (email: string) =>
      request<void>("auth", "/auth/email/verify/send", {
        method: "POST",
        body: JSON.stringify({ email }),
      }),
    logout: (accessToken: string) =>
      request<void>("auth", "/auth/logout", { method: "POST", accessToken }),
  },
  vm: {
    list: (accessToken: string) =>
      request<VmResponse[]>("vm", "/vms", { accessToken }),
    get: (accessToken: string, id: string) =>
      request<VmResponse>("vm", `/vms/${id}`, { accessToken }),
    create: (accessToken: string, body: VmCreateRequest) =>
      request<VmResponse>("vm", "/vms", {
        method: "POST",
        body: JSON.stringify(body),
        accessToken,
      }),
    delete: (accessToken: string, id: string) =>
      request<void>("vm", `/vms/${id}`, { method: "DELETE", accessToken }),
    power: (accessToken: string, id: string, action: string) =>
      request<void>("vm", `/vms/${id}/power`, {
        method: "PATCH",
        body: JSON.stringify({ action }),
        accessToken,
      }),
    availability: (accessToken: string) =>
      request<VmAvailabilityResponse>("vm", "/vms/availability", { accessToken }),
    updatePlan: (
      accessToken: string,
      id: string,
      body: { planType: string; diskSizeGb: number }
    ) =>
      request<VmResponse>("vm", `/vms/${id}/plan`, {
        method: "PATCH",
        body: JSON.stringify(body),
        accessToken,
      }),
    getSshAccess: (accessToken: string, id: string) =>
      request<{ emails: string[] }>("vm", `/vms/${id}/ssh-access`, { accessToken }),
    addSshAccess: (accessToken: string, id: string, email: string) =>
      request<void>("vm", `/vms/${id}/ssh-access`, {
        method: "POST",
        body: JSON.stringify({ email }),
        accessToken,
      }),
    removeSshAccess: (accessToken: string, id: string, email: string) =>
      request<void>("vm", `/vms/${id}/ssh-access/${encodeURIComponent(email)}`, {
        method: "DELETE",
        accessToken,
      }),
    getPorts: (accessToken: string, id: string) =>
      request<PortResponse[]>("vm", `/vms/${id}/ports`, { accessToken }),
    addPort: (accessToken: string, id: string, body: PortAddRequest) =>
      request<PortResponse>("vm", `/vms/${id}/ports`, {
        method: "POST",
        body: JSON.stringify(body),
        accessToken,
      }),
    deletePort: (accessToken: string, id: string, portId: string) =>
      request<void>("vm", `/vms/${id}/ports/${portId}`, { method: "DELETE", accessToken }),
  },
  user: {
    profile: (accessToken: string) =>
      request<ProfileResponse>("user", "/users/profile", { accessToken }),
    sshKeys: (accessToken: string) =>
      request<SshKeyResponse[]>("user", "/users/ssh-keys", { accessToken }),
    registerSshKey: (accessToken: string, body: { publicKey: string; name: string }) =>
      request<SshKeyResponse>("user", "/users/ssh-keys", {
        method: "POST",
        body: JSON.stringify(body),
        accessToken,
      }),
    generateSshKey: (accessToken: string, name: string) =>
      request<SshKeyGenerateResponse>("user", "/users/ssh-keys/generate", {
        method: "POST",
        body: JSON.stringify({ name }),
        accessToken,
      }),
    deleteSshKey: (accessToken: string, keyId: string) =>
      request<void>("user", `/users/ssh-keys/${keyId}`, { method: "DELETE", accessToken }),
  },
};

export interface PortResponse {
  id: string;
  port: number;
  protocol: string;
  visibility: string;
  subdomain: string;
  fullDomain: string;
  accessEmails: string[];
  createdAt: string;
}

export interface PortAddRequest {
  port: number;
  protocol: "HTTP" | "TCP";
  visibility: "PUBLIC" | "PRIVATE";
  initialEmails?: string[];
}
