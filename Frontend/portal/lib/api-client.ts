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
  MemberSearchResult,
  CollaborationResponse,
  TagResponse,
  ScopeType,
  CollaborationType,
  MemberRole,
  FileListResponse,
  FileContentResponse,
  ContainerInfo,
  ImageInfo,
  NetworkInfo,
  ComposeStackInfo,
  DockerStatusResponse,
  DeploymentResponse,
  DeploymentSpec,
  EnvironmentFile,
  ExposedRoute,
  HealthCheck,
  ServiceCard,
  InfraSelection,
  DeploymentEventPayload,
  ComposeSpecResponse,
  AiGenerationResult,
  ComposeReviewFinding,
  ComposeDetectionResult,
  ComposeRouterPlanResult,
  ComposeRouterRouteOverride,
  DbBackupResponse,
  DeploymentTargetResponse,
  GithubInstallationResponse,
  GithubRepositoryResponse,
  GithubInstallationCompleteResponse,
  PreviewAnalysisResult,
  PreviewAuthStrategy,
  PreviewCapability,
  PreviewPageDraft,
  PreviewPagePlan,
  PreviewFlowBlueprint,
  PreviewApiBinding,
  PreviewGenerationMode,
  PagePlanOperation,
  PagePlanProposalResult,
  PartSuggestionResult,
  PreviewPlanApplyResponse,
  PageReviewFinding,
  PreviewCompiledScenario,
  PreviewMode,
  CustomScenarioView,
  CustomScenarioExport,
  RegressionRunView,
  RegressionSuiteView,
  DocsArticle,
  DocsArticleSummary,
  DocsCategory,
  DocsArticleInput,
  DocsImageUpload,
  SupportInquiry,
  SupportInquiryStatus,
  CreateSupportInquiryInput,
} from "./types";
import type { Block } from "@/components/preview-runtime/blueprint";

const API_BASE = {
  auth: process.env.NEXT_PUBLIC_AUTH_API!,
  user: process.env.NEXT_PUBLIC_USER_API!,
  vm: process.env.NEXT_PUBLIC_VM_API!,
  ops: process.env.NEXT_PUBLIC_OPS_API!,
  adminUser: (process.env.NEXT_PUBLIC_ADMIN_API ?? process.env.NEXT_PUBLIC_USER_API)!,
  adminVm: (process.env.NEXT_PUBLIC_ADMIN_API ?? process.env.NEXT_PUBLIC_VM_API)!,
};

const SERVICE_AUDIENCE: Partial<Record<keyof typeof API_BASE, string>> = {
  user: "user-service",
  vm: "vm-service",
  ops: "ops-service",
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

  // FormData는 브라우저가 boundary 포함 Content-Type을 직접 설정해야 하므로 기본값을 강제하지 않음
  const isFormData = typeof FormData !== "undefined" && init.body instanceof FormData;

  const res = await fetch(`${API_BASE[service]}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      ...(isFormData ? {} : { "Content-Type": "application/json" }),
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
      request<{ accessToken: string; tokenType: string; expiresIn: number }>("auth", "/auth/email/verify/confirm", {
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
    sendPasswordResetCode: (email: string) =>
      request<void>("auth", "/auth/password/reset/send", {
        method: "POST",
        body: JSON.stringify({ email }),
      }),
    confirmPasswordResetCode: (email: string, code: string) =>
      request<{ resetToken: string }>("auth", "/auth/password/reset/confirm", {
        method: "POST",
        body: JSON.stringify({ email, code }),
      }),
    resetPassword: (resetToken: string, newPassword: string) =>
      request<void>("auth", "/auth/password/reset", {
        method: "POST",
        body: JSON.stringify({ resetToken, newPassword }),
      }),
    changePassword: (accessToken: string, currentPassword: string, newPassword: string) =>
      request<void>("auth", "/auth/password/change", {
        method: "POST",
        body: JSON.stringify({ currentPassword, newPassword }),
        accessToken,
      }),
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
    // SEC-006: EventSource는 Authorization 헤더를 못 붙이므로, 구독 전 짧은 TTL의 1회용 티켓을
    // 먼저 발급받아 ?ticket=으로만 연결한다(원본 액세스 토큰을 URL에 노출하지 않기 위함).
    issueEventsTicket: (accessToken: string) =>
      request<{ ticket: string }>("vm", "/vms/events/ticket", { method: "POST", accessToken }),
    issueMetricsTicket: (accessToken: string, id: string) =>
      request<{ ticket: string }>("vm", `/vms/${id}/metrics/ticket`, { method: "POST", accessToken }),
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
    uploadProfileImage: (accessToken: string, file: File) => {
      const formData = new FormData();
      formData.append("file", file);
      return request<ProfileResponse>("user", "/users/profile/image", {
        method: "POST",
        body: formData,
        accessToken,
      });
    },
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
  docs: {
    list: (accessToken: string, filters?: { query?: string; category?: string }) => {
      const params = new URLSearchParams();
      if (filters?.query?.trim()) params.set("query", filters.query.trim());
      if (filters?.category?.trim()) params.set("category", filters.category.trim());
      const suffix = params.size > 0 ? `?${params.toString()}` : "";
      return request<DocsArticleSummary[]>("user", `/users/docs${suffix}`, { accessToken });
    },
    categories: (accessToken: string) =>
      request<DocsCategory[]>("user", "/users/docs/categories", { accessToken }),
    get: (accessToken: string, slug: string) =>
      request<DocsArticle>("user", `/users/docs/${encodeURIComponent(slug)}`, { accessToken }),
  },
  support: {
    list: (accessToken: string, page: number = 1, size: number = 20) =>
      request<PagedResponse<SupportInquiry>>(
        "user",
        `/users/support-inquiries?page=${page}&size=${size}`,
        { accessToken }
      ),
    create: (accessToken: string, body: CreateSupportInquiryInput) =>
      request<SupportInquiry>("user", "/users/support-inquiries", {
        method: "POST",
        body: JSON.stringify(body),
        accessToken,
      }),
    close: (accessToken: string, inquiryId: string) =>
      request<SupportInquiry>("user", `/users/support-inquiries/${inquiryId}/close`, {
        method: "PATCH",
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
    invite: (
      accessToken: string,
      orgId: string,
      body: { userId?: string; email: string; nickname?: string; profileImageUrl?: string; role: MemberRole }
    ) =>
      request<MemberResponse>("vm", `/vms/organizations/${orgId}/members`, { method: "POST", body: JSON.stringify(body), accessToken }),
    searchMembers: (accessToken: string, orgId: string, query: string) =>
      request<MemberSearchResult[]>(
        "vm",
        `/vms/organizations/${orgId}/members/search?query=${encodeURIComponent(query)}`,
        { accessToken }
      ),
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
  ops: {
    issueTerminalTicket: (accessToken: string, vmId: string) =>
      request<{ ticket: string }>("ops", `/ops/${vmId}/terminal-ticket`, {
        method: "POST",
        accessToken,
      }),
    listFiles: (accessToken: string, vmId: string, path?: string) =>
      request<FileListResponse>(
        "ops",
        `/ops/${vmId}/files${path ? `?path=${encodeURIComponent(path)}` : ""}`,
        { accessToken }
      ),
    readFileContent: (accessToken: string, vmId: string, path: string) =>
      request<FileContentResponse>("ops", `/ops/${vmId}/files/content?path=${encodeURIComponent(path)}`, {
        accessToken,
      }),
    saveFileContent: (accessToken: string, vmId: string, path: string, content: string) =>
      request<void>("ops", `/ops/${vmId}/files/content?path=${encodeURIComponent(path)}`, {
        method: "PUT",
        body: JSON.stringify({ content }),
        accessToken,
      }),
    createDirectory: (accessToken: string, vmId: string, parentPath: string, name: string) =>
      request<void>("ops", `/ops/${vmId}/files/directories`, {
        method: "POST",
        body: JSON.stringify({ parentPath, name }),
        accessToken,
      }),
    uploadFile: (accessToken: string, vmId: string, path: string, file: File) => {
      const form = new FormData();
      form.append("file", file);
      return request<void>("ops", `/ops/${vmId}/files/upload?path=${encodeURIComponent(path)}`, {
        method: "POST",
        body: form,
        accessToken,
      });
    },
    deleteFile: (accessToken: string, vmId: string, path: string) =>
      request<void>("ops", `/ops/${vmId}/files?path=${encodeURIComponent(path)}`, {
        method: "DELETE",
        accessToken,
      }),
    // 이미지/오디오/비디오 미리보기용 — Range 요청(seek/버퍼링)을 지원하는 스트리밍 엔드포인트에 쓰는 티켓.
    // 티켓 자체가 인증이라 <video>/<audio> src에 그대로 꽂을 수 있음(Authorization 헤더 불필요)
    issueStreamTicket: (accessToken: string, vmId: string, path: string) =>
      request<{ ticket: string }>("ops", `/ops/${vmId}/files/stream-ticket?path=${encodeURIComponent(path)}`, {
        method: "POST",
        accessToken,
      }),
    // 다운로드는 ApiResponse 포맷이 아닌 원문 바이트 스트림이라 request<T>()를 못 쓰고 직접 fetch
    downloadFile: async (accessToken: string, vmId: string, path: string): Promise<Blob> => {
      const token = await getExchangedToken(accessToken, "ops-service");
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_OPS_API}/ops/${vmId}/files/download?path=${encodeURIComponent(path)}`,
        { headers: { Authorization: `Bearer ${token}` }, credentials: "include" }
      );
      if (!res.ok) throw new Error("파일 다운로드에 실패했습니다");
      return res.blob();
    },
    docker: {
      status: (accessToken: string, vmId: string) =>
        request<DockerStatusResponse>("ops", `/ops/${vmId}/docker/status`, { accessToken }),
      install: (accessToken: string, vmId: string) =>
        request<void>("ops", `/ops/${vmId}/docker/install`, { method: "POST", accessToken }),
      listContainers: (accessToken: string, vmId: string) =>
        request<ContainerInfo[]>("ops", `/ops/${vmId}/docker/containers`, { accessToken }),
      containerLogs: (accessToken: string, vmId: string, containerId: string, tail = 200) =>
        request<{ logs: string }>(
          "ops",
          `/ops/${vmId}/docker/containers/${containerId}/logs?tail=${tail}`,
          { accessToken }
        ),
      startContainer: (accessToken: string, vmId: string, containerId: string) =>
        request<void>("ops", `/ops/${vmId}/docker/containers/${containerId}/start`, {
          method: "POST",
          accessToken,
        }),
      stopContainer: (accessToken: string, vmId: string, containerId: string) =>
        request<void>("ops", `/ops/${vmId}/docker/containers/${containerId}/stop`, {
          method: "POST",
          accessToken,
        }),
      restartContainer: (accessToken: string, vmId: string, containerId: string) =>
        request<void>("ops", `/ops/${vmId}/docker/containers/${containerId}/restart`, {
          method: "POST",
          accessToken,
        }),
      removeContainer: (accessToken: string, vmId: string, containerId: string) =>
        request<void>("ops", `/ops/${vmId}/docker/containers/${containerId}`, {
          method: "DELETE",
          accessToken,
        }),
      listImages: (accessToken: string, vmId: string) =>
        request<ImageInfo[]>("ops", `/ops/${vmId}/docker/images`, { accessToken }),
      removeImage: (accessToken: string, vmId: string, imageId: string) =>
        request<void>("ops", `/ops/${vmId}/docker/images/${imageId}`, {
          method: "DELETE",
          accessToken,
        }),
      listNetworks: (accessToken: string, vmId: string) =>
        request<NetworkInfo[]>("ops", `/ops/${vmId}/docker/networks`, { accessToken }),
      createNetwork: (accessToken: string, vmId: string, name: string, driver?: string) =>
        request<void>("ops", `/ops/${vmId}/docker/networks`, {
          method: "POST",
          body: JSON.stringify({ name, driver }),
          accessToken,
        }),
      removeNetwork: (accessToken: string, vmId: string, networkId: string) =>
        request<void>("ops", `/ops/${vmId}/docker/networks/${networkId}`, {
          method: "DELETE",
          accessToken,
        }),
      listComposeStacks: (accessToken: string, vmId: string) =>
        request<ComposeStackInfo[]>("ops", `/ops/${vmId}/docker/compose`, { accessToken }),
    },
    deployments: {
      list: (accessToken: string, vmId: string) =>
        request<DeploymentResponse[]>("ops", `/ops/${vmId}/deployments`, { accessToken }),
      get: (accessToken: string, vmId: string, deploymentId: string) =>
        request<DeploymentResponse>("ops", `/ops/${vmId}/deployments/${deploymentId}`, { accessToken }),
      // 재시도/수정 후 재배포용 — 저장된 compose 원문/환경변수/라우트/헬스체크를 복호화해 가져옴 (repoUrl/branch/patToken은 미포함)
      getComposeSpec: (accessToken: string, vmId: string, deploymentId: string) =>
        request<ComposeSpecResponse>("ops", `/ops/${vmId}/deployments/${deploymentId}/compose-spec`, { accessToken }),
      rollback: (accessToken: string, vmId: string, deploymentId: string) =>
        request<DeploymentResponse>("ops", `/ops/${vmId}/deployments/${deploymentId}/rollback`, {
          method: "POST",
          accessToken,
        }),
      // removeRouteNicknames를 비워두면 컨테이너만 내리고 노출 포트는 그대로 둠
      teardown: (accessToken: string, vmId: string, deploymentId: string, removeRouteNicknames: string[]) =>
        request<DeploymentResponse>("ops", `/ops/${vmId}/deployments/${deploymentId}/teardown`, {
          method: "POST",
          body: JSON.stringify({ removeRouteNicknames }),
          accessToken,
        }),
      create: (
        accessToken: string,
        vmId: string,
        body: {
          repoUrl: string;
          branch: string;
          patToken?: string;
          composeContent: string;
          environmentFiles?: EnvironmentFile[];
          exposedRoutes?: ExposedRoute[];
          healthChecks?: HealthCheck[];
          context?: string;
          installPath?: string;
          targetName?: string;
          autoDeploy?: boolean;
          githubInstallationId?: number;
          githubRepositoryId?: number;
        }
      ) =>
        request<DeploymentResponse>("ops", `/ops/${vmId}/deployments`, {
          method: "POST",
          body: JSON.stringify(body),
          accessToken,
        }),
      createFromSpec: (
        accessToken: string,
        vmId: string,
        body: {
          repoUrl: string;
          branch: string;
          patToken?: string;
          spec: DeploymentSpec;
          installPath?: string;
          targetName?: string;
          autoDeploy?: boolean;
          githubInstallationId?: number;
          githubRepositoryId?: number;
        }
      ) =>
        request<DeploymentResponse>("ops", `/ops/${vmId}/deployments/from-spec`, {
          method: "POST",
          body: JSON.stringify(body),
          accessToken,
        }),
      generateSpec: (
        accessToken: string,
        vmId: string,
        body: {
          repoUrl: string;
          branch: string;
          patToken?: string;
          services: ServiceCard[];
          infrastructure?: InfraSelection[];
          existingNetworkName?: string;
          githubInstallationId?: number;
          githubRepositoryId?: number;
        }
      ) =>
        request<AiGenerationResult>("ops", `/ops/${vmId}/deployments/ai-spec/generate`, {
          method: "POST",
          body: JSON.stringify(body),
          accessToken,
        }),
      reviewSpec: (accessToken: string, vmId: string, spec: DeploymentSpec) =>
        request<ComposeReviewFinding[]>("ops", `/ops/${vmId}/deployments/ai-spec/review`, {
          method: "POST",
          body: JSON.stringify(spec),
          accessToken,
        }),
      renderSpec: (accessToken: string, vmId: string, spec: DeploymentSpec) =>
        request<ComposeSpecResponse>("ops", `/ops/${vmId}/deployments/ai-spec/render`, {
          method: "POST",
          body: JSON.stringify(spec),
          accessToken,
        }),
      detectCompose: (
        accessToken: string,
        vmId: string,
        body: {
          repoUrl: string;
          branch: string;
          patToken?: string;
          context?: string;
          githubInstallationId?: number;
          githubRepositoryId?: number;
        }
      ) =>
        request<ComposeDetectionResult>("ops", `/ops/${vmId}/deployments/compose/detect`, {
          method: "POST",
          body: JSON.stringify(body),
          accessToken,
        }),
      reviewCompose: (accessToken: string, vmId: string, composeContent: string) =>
        request<ComposeReviewFinding[]>("ops", `/ops/${vmId}/deployments/compose/review`, {
          method: "POST",
          body: JSON.stringify({ composeContent }),
          accessToken,
        }),
      planComposeRouter: (
        accessToken: string,
        vmId: string,
        body: {
          composeContent: string;
          routerHostPort?: number;
          servicePorts?: Record<string, number>;
          routeOverrides?: Record<string, ComposeRouterRouteOverride>;
          excludedServices?: string[];
        }
      ) =>
        request<ComposeRouterPlanResult>("ops", `/ops/${vmId}/deployments/compose/router/plan`, {
          method: "POST",
          body: JSON.stringify(body),
          accessToken,
        }),
      listTargets: (accessToken: string, vmId: string) =>
        request<DeploymentTargetResponse[]>("ops", `/ops/${vmId}/deployment-targets`, { accessToken }),
      setAutoDeploy: (accessToken: string, vmId: string, targetId: string, enabled: boolean) =>
        request<DeploymentTargetResponse>("ops", `/ops/${vmId}/deployment-targets/${targetId}/auto-deploy`, {
          method: "PATCH",
          body: JSON.stringify({ enabled }),
          accessToken,
        }),
      linkManualCname: (accessToken: string, vmId: string, targetId: string, portId: string) =>
        request<void>("ops", `/ops/${vmId}/deployment-targets/${targetId}/ports/${portId}`, {
          method: "PUT",
          accessToken,
        }),
      unlinkManualCname: (accessToken: string, vmId: string, targetId: string, portId: string) =>
        request<void>("ops", `/ops/${vmId}/deployment-targets/${targetId}/ports/${portId}`, {
          method: "DELETE",
          accessToken,
        }),
      redeployTarget: (accessToken: string, vmId: string, targetId: string) =>
        request<DeploymentResponse>("ops", `/ops/${vmId}/deployment-targets/${targetId}/redeploy`, {
          method: "POST",
          accessToken,
        }),
      // 컨테이너 중지 + 이 대상이 만든 모든 이미지/git 저장소/노출 라우트를 정리하고 대상 자체를 삭제
      deleteTarget: (accessToken: string, vmId: string, targetId: string) =>
        request<void>("ops", `/ops/${vmId}/deployment-targets/${targetId}`, {
          method: "DELETE",
          accessToken,
        }),
      // SSE는 EventSource로 커스텀 Authorization 헤더를 못 붙여서(백엔드도 알고 있는 기존 갭),
      // fetch 스트리밍으로 직접 SSE 프레임을 파싱함. afterSequence로 끊겼을 때 이어받기도 직접 구현.
      streamEvents: (
        accessToken: string,
        vmId: string,
        deploymentId: string,
        onEvent: (event: DeploymentEventPayload) => void,
        onError?: (err: Error) => void,
        onConnected?: () => void
      ): (() => void) => {
        let stopped = false;
        let lastSequence = 0;
        let retryTimer: ReturnType<typeof setTimeout> | null = null;
        let abortController: AbortController | null = null;

        async function connect() {
          if (stopped) return;
          abortController = new AbortController();
          try {
            const token = await getExchangedToken(accessToken, "ops-service");
            const res = await fetch(
              `${API_BASE.ops}/ops/${vmId}/deployments/${deploymentId}/events?afterSequence=${lastSequence}`,
              {
                headers: { Authorization: `Bearer ${token}`, Accept: "text/event-stream" },
                credentials: "include",
                signal: abortController.signal,
              }
            );
            if (!res.ok || !res.body) throw new Error("배포 이벤트 스트림 연결에 실패했습니다");
            onConnected?.();

            const reader = res.body.getReader();
            const decoder = new TextDecoder();
            let buffer = "";

            while (!stopped) {
              const { done, value } = await reader.read();
              if (done) break;
              buffer += decoder.decode(value, { stream: true });

              let boundary: number;
              while ((boundary = buffer.indexOf("\n\n")) !== -1) {
                const rawEvent = buffer.slice(0, boundary);
                buffer = buffer.slice(boundary + 2);

                const dataLines = rawEvent
                  .split("\n")
                  .filter((line) => line.startsWith("data:"))
                  .map((line) => line.slice(5).trimStart());
                if (dataLines.length === 0) continue;

                try {
                  const payload: DeploymentEventPayload = JSON.parse(dataLines.join("\n"));
                  lastSequence = payload.sequence;
                  onEvent(payload);
                  if (payload.eventType === "DONE") {
                    stopped = true;
                    return;
                  }
                } catch {
                  // 파싱 불가능한 프레임은 무시
                }
              }
            }

            // 스트림이 끝났는데 아직 DONE을 못 받았으면(재시작 등) 잠시 후 afterSequence로 재연결
            if (!stopped) {
              retryTimer = setTimeout(connect, 3000);
            }
          } catch (err) {
            if (stopped) return;
            onError?.(err instanceof Error ? err : new Error("배포 이벤트 스트림 오류"));
            retryTimer = setTimeout(connect, 3000);
          }
        }

        connect();

        return () => {
          stopped = true;
          if (retryTimer) clearTimeout(retryTimer);
          abortController?.abort();
        };
      },
    },
    github: {
      createInstallUrl: (accessToken: string, vmId: string) =>
        request<{ url: string }>("ops", `/ops/github/install-url?vmId=${encodeURIComponent(vmId)}`, {
          method: "POST",
          accessToken,
        }),
      completeInstallation: (accessToken: string, code: string, state: string) =>
        request<GithubInstallationCompleteResponse>("ops", "/ops/github/installations/complete", {
          method: "POST",
          body: JSON.stringify({ code, state }),
          accessToken,
        }),
      listInstallations: (accessToken: string) =>
        request<GithubInstallationResponse[]>("ops", "/ops/github/installations", { accessToken }),
      listRepositories: (accessToken: string) =>
        request<GithubRepositoryResponse[]>("ops", "/ops/github/repositories", { accessToken }),
    },
    // Auto Preview (GamjaBox_2.0_Key_Features.md 1단계) — analyze/review는 VM에 종속되지 않고
    // (분석만 함, 배포 안 함), deploy만 VM별 엔드포인트를 쓴다.
    preview: {
      analyze: (
        accessToken: string,
        body: {
          apiDocsUrl?: string;
          apiDocsContent?: string;
          documentationPageUrl?: string;
          serviceDescription?: string;
          scenarioIntent?: string;
          selectedCapabilityIds?: string[];
          purpose?: "API_TEST" | "PRODUCT_LIKE" | "ADMIN";
          previewMode?: "SCENARIO_PREVIEW" | "INFERRED_SCENARIO_PREVIEW" | "OPERATION_PREVIEW";
        }
      ) =>
        request<PreviewAnalysisResult>("ops", "/ops/preview/analyze", {
          method: "POST",
          body: JSON.stringify(body),
          accessToken,
        }),
      review: (
        accessToken: string,
        body: { serviceDescription?: string; capabilities: PreviewCapability[]; pages: PreviewPageDraft[] }
      ) =>
        request<PageReviewFinding[]>("ops", "/ops/preview/review", {
          method: "POST",
          body: JSON.stringify(body),
          accessToken,
        }),
      // Plan Review UI(Increment 5 2부) — AI가 제안한 오퍼레이션을 적용하지 않고 개별 검증된 상태로
      // 그대로 돌려준다. 사용자가 검토한 뒤 planApply로 원하는 것만 적용한다.
      planPropose: (
        accessToken: string,
        body: {
          serviceDescription?: string;
          purpose?: "API_TEST" | "PRODUCT_LIKE" | "ADMIN";
          capabilities: PreviewCapability[];
          pages: PreviewPageDraft[];
          pagePlans?: PreviewPagePlan[];
          flows?: PreviewFlowBlueprint[];
          bindings?: PreviewApiBinding[];
        }
      ) =>
        request<PagePlanProposalResult>("ops", "/ops/preview/plan/propose", {
          method: "POST",
          body: JSON.stringify(body),
          accessToken,
        }),
      planApply: (
        accessToken: string,
        body: {
          capabilities: PreviewCapability[];
          pages: PreviewPageDraft[];
          pagePlans?: PreviewPagePlan[];
          flows?: PreviewFlowBlueprint[];
          bindings?: PreviewApiBinding[];
          operations: PagePlanOperation[];
        }
      ) =>
        request<PreviewPlanApplyResponse>("ops", "/ops/preview/plan/apply", {
          method: "POST",
          body: JSON.stringify(body),
          accessToken,
        }),
      // AI 파츠 제안 — 스왑 가능한 Block(목록/상세/대시보드)마다 서비스 설명에 근거한 Blueprint 파츠를
      // 추천받는다. 검증 통과분만 오므로 그대로 partOverrides("pageId/instanceId"→componentId)로 병합해
      // refreshBlocks를 다시 부르면 된다(Phase C 오버라이드 배관 재사용).
      suggestParts: (
        accessToken: string,
        body: {
          serviceDescription?: string;
          purpose?: "API_TEST" | "PRODUCT_LIKE" | "ADMIN";
          capabilities: PreviewCapability[];
          pages: PreviewPageDraft[];
          pagePlans?: PreviewPagePlan[];
        }
      ) =>
        request<PartSuggestionResult>("ops", "/ops/preview/parts/suggest", {
          method: "POST",
          body: JSON.stringify(body),
          accessToken,
        }),
      customScenarios: {
        generate: (
          accessToken: string,
          body: {
            serviceId: string;
            apiDocsUrl: string;
            name?: string;
            description?: string;
            naturalLanguageSource: string;
            purpose?: "API_TEST" | "PRODUCT_LIKE" | "ADMIN";
            visibility?: "PRIVATE" | "TEAM";
          }
        ) =>
          request<CustomScenarioView>("ops", "/ops/preview/custom-scenarios", {
            method: "POST",
            body: JSON.stringify(body),
            accessToken,
          }),
        list: (accessToken: string, serviceId: string) =>
          request<CustomScenarioView[]>(
            "ops",
            `/ops/preview/custom-scenarios?serviceId=${encodeURIComponent(serviceId)}`,
            { accessToken }
          ),
        activate: (accessToken: string, scenarioId: string) =>
          request<CustomScenarioView>(
            "ops",
            `/ops/preview/custom-scenarios/${encodeURIComponent(scenarioId)}/activate`,
            { method: "POST", accessToken }
          ),
        revalidate: (accessToken: string, scenarioId: string, apiDocsUrl: string) =>
          request<CustomScenarioView>(
            "ops",
            `/ops/preview/custom-scenarios/${encodeURIComponent(scenarioId)}/revalidate`,
            {
              method: "POST",
              body: JSON.stringify({ apiDocsUrl }),
              accessToken,
            }
          ),
        export: (accessToken: string, scenarioId: string) =>
          request<CustomScenarioExport>(
            "ops",
            `/ops/preview/custom-scenarios/${encodeURIComponent(scenarioId)}/export`,
            { accessToken }
          ),
        import: (
          accessToken: string,
          serviceId: string,
          apiDocsUrl: string,
          scenario: CustomScenarioExport
        ) =>
          request<CustomScenarioView>("ops", "/ops/preview/custom-scenarios/import", {
            method: "POST",
            body: JSON.stringify({ serviceId, apiDocsUrl, scenario }),
            accessToken,
          }),
      },
      regressionSuites: {
        create: (
          accessToken: string,
          body: {
            serviceId: string;
            name: string;
            description?: string;
            apiDocsUrl: string;
            apiBaseUrl: string;
            scenarioIds: string[];
            deploymentTargetId?: string;
            runOnDeployment?: boolean;
            allowStateChangingOnDeployment?: boolean;
          }
        ) =>
          request<RegressionSuiteView>("ops", "/ops/preview/regression-suites", {
            method: "POST",
            body: JSON.stringify(body),
            accessToken,
          }),
        list: (accessToken: string, serviceId: string) =>
          request<RegressionSuiteView[]>(
            "ops",
            `/ops/preview/regression-suites?serviceId=${encodeURIComponent(serviceId)}`,
            { accessToken }
          ),
        run: (
          accessToken: string,
          suiteId: string,
          body: {
            initialState?: Record<string, unknown>;
            headers?: Record<string, string>;
            allowStateChanging?: boolean;
            failFast?: boolean;
          } = {}
        ) =>
          request<RegressionRunView>(
            "ops",
            `/ops/preview/regression-suites/${encodeURIComponent(suiteId)}/runs`,
            { method: "POST", body: JSON.stringify(body), accessToken }
          ),
        runFromCi: (
          accessToken: string,
          suiteId: string,
          body: {
            initialState?: Record<string, unknown>;
            headers?: Record<string, string>;
            allowStateChanging?: boolean;
            failFast?: boolean;
          } = {}
        ) =>
          request<RegressionRunView>(
            "ops",
            `/ops/preview/regression-suites/${encodeURIComponent(suiteId)}/ci/runs`,
            { method: "POST", body: JSON.stringify(body), accessToken }
          ),
        runs: (accessToken: string, suiteId: string) =>
          request<RegressionRunView[]>(
            "ops",
            `/ops/preview/regression-suites/${encodeURIComponent(suiteId)}/runs`,
            { accessToken }
          ),
        runDetail: (accessToken: string, runId: string) =>
          request<RegressionRunView>(
            "ops",
            `/ops/preview/regression-suites/runs/${encodeURIComponent(runId)}`,
            { accessToken }
          ),
        delete: (accessToken: string, suiteId: string) =>
          request<void>(
            "ops",
            `/ops/preview/regression-suites/${encodeURIComponent(suiteId)}`,
            { method: "DELETE", accessToken }
          ),
      },
      // Direction Recovery Change Request §13.1 — 라이브 프리뷰가 조립 규칙을 직접 계산하지 않고,
      // capability/페이지가 바뀔 때마다(analyze/plan 응답 직후 + accessTokenPath 지정·수동 로그인
      // 등록 같은 로컬 편집 직후) 이 엔드포인트로 Block을 다시 받는다.
      blocks: (
        accessToken: string,
        body: {
          capabilities: PreviewCapability[];
          pages: PreviewPageDraft[];
          pagePlans?: PreviewPagePlan[];
          purpose?: "API_TEST" | "PRODUCT_LIKE" | "ADMIN";
          partOverrides?: Record<string, string>;
        }
      ) =>
        request<{ pageBlocks: Record<string, Block[]> }>("ops", "/ops/preview/blocks", {
          method: "POST",
          body: JSON.stringify(body),
          accessToken,
        }),
      deploy: (
        accessToken: string,
        vmId: string,
        body: {
          targetName: string;
          apiBaseUrl: string;
          capabilities: PreviewCapability[];
          pages: PreviewPageDraft[];
          pagePlans?: PreviewPagePlan[];
          flows?: PreviewFlowBlueprint[];
          bindings?: PreviewApiBinding[];
          authStrategy: PreviewAuthStrategy;
          purpose?: "API_TEST" | "PRODUCT_LIKE" | "ADMIN";
          generationMode?: PreviewGenerationMode;
          scenarios?: PreviewCompiledScenario[];
          previewMode?: PreviewMode;
          partOverrides?: Record<string, string>;
        }
      ) =>
        request<DeploymentResponse>("ops", `/ops/${vmId}/preview/deploy`, {
          method: "POST",
          body: JSON.stringify(body),
          accessToken,
        }),
    },
    backups: {
      list: (accessToken: string, vmId: string) =>
        request<DbBackupResponse[]>("ops", `/ops/${vmId}/backups`, { accessToken }),
      trigger: (
        accessToken: string,
        vmId: string,
        body: { serviceName: string; dbType: string; database: string; username?: string; password?: string }
      ) =>
        request<DbBackupResponse>("ops", `/ops/${vmId}/backups`, {
          method: "POST",
          body: JSON.stringify(body),
          accessToken,
        }),
      download: async (accessToken: string, vmId: string, backupId: string): Promise<Blob> => {
        const token = await getExchangedToken(accessToken, "ops-service");
        const res = await fetch(
          `${process.env.NEXT_PUBLIC_OPS_API}/ops/${vmId}/backups/${backupId}/download`,
          { headers: { Authorization: `Bearer ${token}` }, credentials: "include" }
        );
        if (!res.ok) throw new Error("백업 다운로드에 실패했습니다");
        return res.blob();
      },
      verify: (accessToken: string, vmId: string, backupId: string) =>
        request<DbBackupResponse>("ops", `/ops/${vmId}/backups/${backupId}/verify`, {
          method: "POST",
          accessToken,
        }),
    },
  },
  admin: {
    docs: {
      list: (accessToken: string) =>
        request<DocsArticleSummary[]>("adminUser", "/admin/docs", { accessToken }),
      get: (accessToken: string, id: string) =>
        request<DocsArticle>("adminUser", `/admin/docs/${id}`, { accessToken }),
      create: (accessToken: string, body: DocsArticleInput) =>
        request<DocsArticle>("adminUser", "/admin/docs", {
          method: "POST",
          body: JSON.stringify(body),
          accessToken,
        }),
      update: (accessToken: string, id: string, body: DocsArticleInput) =>
        request<DocsArticle>("adminUser", `/admin/docs/${id}`, {
          method: "PUT",
          body: JSON.stringify(body),
          accessToken,
        }),
      publish: (accessToken: string, id: string) =>
        request<DocsArticle>("adminUser", `/admin/docs/${id}/publish`, { method: "POST", accessToken }),
      unpublish: (accessToken: string, id: string) =>
        request<DocsArticle>("adminUser", `/admin/docs/${id}/unpublish`, { method: "POST", accessToken }),
      delete: (accessToken: string, id: string) =>
        request<void>("adminUser", `/admin/docs/${id}`, { method: "DELETE", accessToken }),
      uploadImage: (accessToken: string, file: File) => {
        const form = new FormData();
        form.append("file", file);
        return request<DocsImageUpload>("adminUser", "/admin/docs/images", {
          method: "POST",
          body: form,
          accessToken,
        });
      },
    },
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
    support: {
      list: (accessToken: string, status: SupportInquiryStatus | "ALL" = "ALL", page: number = 1, size: number = 20) => {
        const params = new URLSearchParams({ page: String(page), size: String(size) });
        if (status !== "ALL") params.set("status", status);
        return request<PagedResponse<SupportInquiry>>(
          "adminUser",
          `/admin/users/support-inquiries?${params.toString()}`,
          { accessToken }
        );
      },
      update: (
        accessToken: string,
        inquiryId: string,
        body: { status: SupportInquiryStatus; response?: string }
      ) =>
        request<SupportInquiry>("adminUser", `/admin/users/support-inquiries/${inquiryId}`, {
          method: "PATCH",
          body: JSON.stringify(body),
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
  // 배포(자동배포)가 만든 포트면 값이 있고, 사용자가 직접 추가한 포트면 null
  deploymentId: string | null;
  // 재배포 후에도 유지되는 배포 대상 ID
  deploymentAppId: string | null;
  // 수동 CNAME을 배포 대상 카드에 표시하기 위한 연결 ID(자동 배포 소유권과 별개)
  linkedDeploymentTargetId: string | null;
}

export interface PortAddRequest {
  port: number;
  protocol: "HTTP" | "TCP";
  visibility: "PUBLIC" | "PRIVATE";
  nickname: string;
  initialEmails?: string[];
  customSubdomain?: string;
}
