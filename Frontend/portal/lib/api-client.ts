import type {
  VmResponse,
  VmCreateRequest,
  VmAvailabilityResponse,
  SshKeyResponse,
  SshKeyGenerateResponse,
  ProfileResponse,
  UsageResponse,
  AdminUserResponse,
  AdminVmResponse,
  VmMetricsCurrentResponse,
  VmMetricsHistoryResponse,
  UpgradeRequestResponse,
  PagedResponse,
  OrgResponse,
  OrgDetailResponse,
  MemberResponse,
  CollaborationResponse,
  TagResponse,
  ScopeType,
  CollaborationType,
  MemberRole,
} from "./types";

const API_BASE = {
  auth: process.env.NEXT_PUBLIC_AUTH_API!,
  user: process.env.NEXT_PUBLIC_USER_API!,
  vm: process.env.NEXT_PUBLIC_VM_API!,
  adminUser: (process.env.NEXT_PUBLIC_ADMIN_API ?? process.env.NEXT_PUBLIC_USER_API)!,
  adminVm: (process.env.NEXT_PUBLIC_ADMIN_API ?? process.env.NEXT_PUBLIC_VM_API)!,
};

const SERVICE_AUDIENCE: Partial<Record<keyof typeof API_BASE, string>> = {
  user: "user-service",
  vm: "vm-service",
  adminUser: "user-service",
  adminVm: "vm-service",
};

interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string | null;
  errorCode?: string | null;
}

// ─── Exchange token cache with exp-based TTL ──────────────────────────────────
interface CachedToken {
  token: string;
  expiresAt: number; // ms
}

const exchangeCache = new Map<string, CachedToken>();

function parseJwtExp(token: string): number | null {
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    return typeof payload.exp === "number" ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
}

export function invalidateExchangeCache(accessToken: string) {
  for (const key of exchangeCache.keys()) {
    if (key.startsWith(`${accessToken}:`)) {
      exchangeCache.delete(key);
    }
  }
}

export async function getExchangedToken(accessToken: string, targetService: string): Promise<string> {
  const cacheKey = `${accessToken}:${targetService}`;
  const cached = exchangeCache.get(cacheKey);
  // 만료 30초 전부터 캐시 무효화
  if (cached && cached.expiresAt - Date.now() > 30_000) {
    return cached.token;
  }

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
  const expiresAt = parseJwtExp(token) ?? Date.now() + 14 * 60 * 1000; // fallback 14분
  exchangeCache.set(cacheKey, { token, expiresAt });
  return token;
}

// ─── 401 시 access token 갱신 콜백 (auth-context에서 등록) ───────────────────
let tokenRefresher: (() => Promise<string | null>) | null = null;

export function setTokenRefresher(fn: () => Promise<string | null>) {
  tokenRefresher = fn;
}

// ─── Core request ─────────────────────────────────────────────────────────────
async function request<T>(
  service: keyof typeof API_BASE,
  path: string,
  options: RequestInit & { accessToken?: string } = {},
  _retry = false
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

  // 401 처리 (auth 서비스 자체는 제외 — 로그인 실패가 401이므로)
  if (res.status === 401 && !_retry && service !== "auth") {
    if (accessToken) invalidateExchangeCache(accessToken);
    // 1단계: 교환 토큰만 새로 받아서 재시도
    try {
      return await request<T>(service, path, options, true);
    } catch {
      // 2단계: 액세스 토큰 자체가 만료된 경우 → 리프레시 후 재시도
      if (tokenRefresher) {
        const newToken = await tokenRefresher();
        if (newToken) {
          return request<T>(service, path, { ...options, accessToken: newToken }, true);
        }
      }
    }
  }

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: "요청에 실패했습니다", errorCode: null }));
    const err = new Error(error.message ?? "요청에 실패했습니다") as Error & { errorCode?: string };
    err.errorCode = error.errorCode ?? undefined;
    throw err;
  }

  if (res.status === 204 || res.headers.get("content-length") === "0") {
    return undefined as T;
  }

  const body: ApiResponse<T> = await res.json();
  return body.data;
}

export const api = {
  auth: {
    login: (email: string, password: string, rememberMe = false) =>
      request<{ accessToken: string; tokenType: string; expiresIn: number }>(
        "auth",
        "/auth/login",
        { method: "POST", body: JSON.stringify({ email, password, rememberMe }) }
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
    withdraw: (accessToken: string) =>
      request<void>("auth", "/auth/withdraw", { method: "DELETE", accessToken }),
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
    power: (accessToken: string, id: string, action: "START" | "STOP" | "REBOOT" | "SUSPEND") =>
      request<VmResponse>("vm", `/vms/${id}/power`, {
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
    metricsCurrent: (accessToken: string, id: string) =>
      request<VmMetricsCurrentResponse>("vm", `/vms/${id}/metrics/current`, { accessToken }),
    metricsHistory: (accessToken: string, id: string, timeframe: string = "hour") =>
      request<VmMetricsHistoryResponse>("vm", `/vms/${id}/metrics/history?timeframe=${timeframe}`, {
        accessToken,
      }),
    getSshAccess: (accessToken: string, id: string) =>
      request<string[]>("vm", `/vms/${id}/ssh-access`, { accessToken }),
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
    checkSubdomain: (accessToken: string, vmId: string, value: string) =>
      request<{ available: boolean; reason: string | null }>("vm", `/vms/${vmId}/ports/subdomain/check?value=${encodeURIComponent(value)}`, { accessToken }),
    deletePort: (accessToken: string, id: string, portId: string) =>
      request<void>("vm", `/vms/${id}/ports/${portId}`, { method: "DELETE", accessToken }),
    addPortAccess: (accessToken: string, vmId: string, portId: string, email: string) =>
      request<PortResponse>("vm", `/vms/${vmId}/ports/${portId}/access`, {
        method: "POST",
        body: JSON.stringify({ email }),
        accessToken,
      }),
    removePortAccess: (accessToken: string, vmId: string, portId: string, email: string) =>
      request<PortResponse>("vm", `/vms/${vmId}/ports/${portId}/access/${encodeURIComponent(email)}`, {
        method: "DELETE",
        accessToken,
      }),
  },
  user: {
    profile: (accessToken: string) =>
      request<ProfileResponse>("user", "/users/profile", { accessToken }),
    updateProfile: (accessToken: string, body: { nickname?: string; profileImageUrl?: string }) =>
      request<ProfileResponse>("user", "/users/profile", {
        method: "PATCH",
        body: JSON.stringify(body),
        accessToken,
      }),
    usage: (accessToken: string) =>
      request<UsageResponse>("user", "/users/usage", { accessToken }),
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
    createUpgradeRequest: (accessToken: string, userId: string, targetPlanType: string) =>
      request<UpgradeRequestResponse>("user", `/users/${userId}/upgrade-requests`, {
        method: "POST",
        body: JSON.stringify({ targetPlanType }),
        accessToken,
      }),
    getUpgradeRequests: (accessToken: string, userId: string) =>
      request<UpgradeRequestResponse[]>("user", `/users/${userId}/upgrade-requests`, { accessToken }),
    cancelUpgradeRequest: (accessToken: string, userId: string, requestId: string) =>
      request<void>("user", `/users/${userId}/upgrade-requests/${requestId}`, {
        method: "DELETE",
        accessToken,
      }),
  },
  org: {
    list: (accessToken: string) =>
      request<OrgResponse[]>("vm", "/vms/organizations", { accessToken }),
    invitations: (accessToken: string) =>
      request<OrgResponse[]>("vm", "/vms/organizations/invitations", { accessToken }),
    get: (accessToken: string, orgId: string) =>
      request<OrgDetailResponse>("vm", `/vms/organizations/${orgId}`, { accessToken }),
    create: (accessToken: string, body: { name: string; invites?: { email: string; role: MemberRole }[]; vmIds?: string[] }) =>
      request<OrgDetailResponse>("vm", "/vms/organizations", { method: "POST", body: JSON.stringify(body), accessToken }),
    update: (accessToken: string, orgId: string, name: string) =>
      request<OrgDetailResponse>("vm", `/vms/organizations/${orgId}`, { method: "PATCH", body: JSON.stringify({ name }), accessToken }),
    delete: (accessToken: string, orgId: string) =>
      request<void>("vm", `/vms/organizations/${orgId}`, { method: "DELETE", accessToken }),
    invite: (accessToken: string, orgId: string, email: string, role: MemberRole) =>
      request<MemberResponse>("vm", `/vms/organizations/${orgId}/members`, { method: "POST", body: JSON.stringify({ email, role }), accessToken }),
    respond: (accessToken: string, orgId: string, memberId: string, accept: boolean) =>
      request<MemberResponse>("vm", `/vms/organizations/${orgId}/members/${memberId}/respond`, { method: "PATCH", body: JSON.stringify({ accept }), accessToken }),
    removeMember: (accessToken: string, orgId: string, memberId: string) =>
      request<void>("vm", `/vms/organizations/${orgId}/members/${memberId}`, { method: "DELETE", accessToken }),
    updateRole: (accessToken: string, orgId: string, memberId: string, role: MemberRole) =>
      request<MemberResponse>("vm", `/vms/organizations/${orgId}/members/${memberId}/role`, { method: "PATCH", body: JSON.stringify({ role }), accessToken }),
    addVm: (accessToken: string, orgId: string, vmId: string) =>
      request<void>("vm", `/vms/organizations/${orgId}/vms`, { method: "POST", body: JSON.stringify({ vmId }), accessToken }),
    removeVm: (accessToken: string, orgId: string, vmId: string) =>
      request<void>("vm", `/vms/organizations/${orgId}/vms/${vmId}`, { method: "DELETE", accessToken }),
  },
  collab: {
    list: (accessToken: string, scopeType: ScopeType, scopeId: string, type?: CollaborationType) => {
      const params = new URLSearchParams({ scopeType, scopeId });
      if (type) params.append("type", type);
      return request<CollaborationResponse[]>("vm", `/vms/collaborations?${params}`, { accessToken });
    },
    get: (accessToken: string, id: string) =>
      request<CollaborationResponse>("vm", `/vms/collaborations/${id}`, { accessToken }),
    create: (accessToken: string, body: { scopeType: ScopeType; scopeId: string; type: CollaborationType; tag?: string; title: string; content: string }) =>
      request<CollaborationResponse>("vm", "/vms/collaborations", { method: "POST", body: JSON.stringify(body), accessToken }),
    update: (accessToken: string, id: string, body: { tag?: string; title: string; content: string }) =>
      request<CollaborationResponse>("vm", `/vms/collaborations/${id}`, { method: "PATCH", body: JSON.stringify(body), accessToken }),
    delete: (accessToken: string, id: string) =>
      request<void>("vm", `/vms/collaborations/${id}`, { method: "DELETE", accessToken }),
    pin: (accessToken: string, id: string) =>
      request<CollaborationResponse>("vm", `/vms/collaborations/${id}/pin`, { method: "PATCH", accessToken }),
    resolve: (accessToken: string, id: string) =>
      request<CollaborationResponse>("vm", `/vms/collaborations/${id}/resolve`, { method: "PATCH", accessToken }),
    searchTags: (accessToken: string, scopeType: ScopeType, scopeId: string, query?: string) => {
      const params = new URLSearchParams({ scopeType, scopeId });
      if (query) params.append("query", query);
      return request<TagResponse[]>("vm", `/vms/collaboration-tags?${params}`, { accessToken });
    },
    listTags: (accessToken: string, scopeType: ScopeType, scopeId: string) =>
      request<TagResponse[]>("vm", `/vms/collaboration-tags/all?scopeType=${scopeType}&scopeId=${scopeId}`, { accessToken }),
    deleteTag: (accessToken: string, tagId: string) =>
      request<void>("vm", `/vms/collaboration-tags/${tagId}`, { method: "DELETE", accessToken }),
  },
  admin: {
    users: {
      list: (accessToken: string) =>
        request<AdminUserResponse[]>("adminUser", "/admin/users", { accessToken }),
      get: (accessToken: string, userId: string) =>
        request<AdminUserResponse>("adminUser", `/admin/users/${userId}`, { accessToken }),
      suspend: (accessToken: string, userId: string) =>
        request<AdminUserResponse>("adminUser", `/admin/users/${userId}/suspend`, { method: "PATCH", accessToken }),
      activate: (accessToken: string, userId: string) =>
        request<AdminUserResponse>("adminUser", `/admin/users/${userId}/activate`, { method: "PATCH", accessToken }),
      updatePlan: (accessToken: string, userId: string, planType: string) =>
        request<AdminUserResponse>("adminUser", `/admin/users/${userId}/plan`, {
          method: "PATCH",
          body: JSON.stringify({ planType }),
          accessToken,
        }),
    },
    vms: {
      list: (accessToken: string) =>
        request<AdminVmResponse[]>("adminVm", "/admin/vms", { accessToken }),
      get: (accessToken: string, vmId: string) =>
        request<AdminVmResponse>("adminVm", `/admin/vms/${vmId}`, { accessToken }),
      forceDelete: (accessToken: string, vmId: string) =>
        request<void>("adminVm", `/admin/vms/${vmId}/force`, { method: "DELETE", accessToken }),
    },
    upgradeRequests: {
      list: (accessToken: string, page: number = 1, size: number = 20) =>
        request<PagedResponse<UpgradeRequestResponse>>(
          "adminUser",
          `/admin/users/upgrade-requests?page=${page}&size=${size}`,
          { accessToken }
        ),
      review: (accessToken: string, requestId: string, approved: boolean, reason?: string) =>
        request<UpgradeRequestResponse>("adminUser", `/admin/users/upgrade-requests/${requestId}`, {
          method: "PATCH",
          body: JSON.stringify({ approved, reason }),
          accessToken,
        }),
    },
  },
};

export interface PortResponse {
  id: string;
  port: number;
  protocol: string;
  visibility: string;
  nickname: string;
  subdomain: string;
  fullDomain: string;
  accessEmails: string[];
  createdAt: string;
}

export interface PortAddRequest {
  port: number;
  protocol: "HTTP" | "TCP";
  visibility: "PUBLIC" | "PRIVATE";
  nickname: string;
  initialEmails?: string[];
  customSubdomain?: string;
}
