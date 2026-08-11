const SERVICES = [
  {
    id: "auth",
    badge: "AUTH",
    name: "Auth API",
    shortName: "Auth",
    description: "인증, 세션, 이메일 검증과 서비스 토큰",
    navDescription: "Identity & access",
    specUrl: "/openapi/auth",
  },
  {
    id: "user",
    badge: "USER",
    name: "User API",
    shortName: "User",
    description: "프로필, 플랜, SSH 키와 사용자 문서",
    navDescription: "Profiles & plans",
    specUrl: "/openapi/user",
  },
  {
    id: "vm",
    badge: "VM",
    name: "VM API",
    shortName: "VM",
    description: "인스턴스, 조직, 네트워크와 Proxmox 자원",
    navDescription: "Compute & network",
    specUrl: "/openapi/vm",
  },
  {
    id: "ops",
    badge: "OPS",
    name: "Ops API",
    shortName: "Ops",
    description: "배포, 터미널, 파일과 Preview 자동화",
    navDescription: "Runtime & delivery",
    specUrl: "/openapi/ops",
  },
];

// Curated cross-endpoint journeys. Each step is [method, OpenAPI path, fallback summary].
// The UI checks every tuple against the live spec, so contract drift is visible immediately.
const FLOW_CATALOG = {
  auth: [
    {
      id: "auth-onboarding",
      category: "회원 시작",
      title: "회원가입부터 로그인까지",
      description: "계정을 만들고 이메일을 검증한 뒤 Access/Refresh Token을 발급받습니다.",
      steps: [
        ["POST", "/auth/register", "계정 생성"],
        ["POST", "/auth/email/verify/send", "검증 메일 발송"],
        ["POST", "/auth/email/verify/confirm", "인증 코드 확인"],
        ["POST", "/auth/login", "세션 발급"],
      ],
    },
    {
      id: "auth-session",
      category: "세션",
      title: "세션 갱신과 종료",
      description: "httpOnly Refresh Token을 회전하고, 사용이 끝나면 서버 세션을 무효화합니다.",
      steps: [
        ["POST", "/auth/login", "Access/Refresh Token 발급"],
        ["POST", "/auth/token/refresh", "Access Token 갱신"],
        ["POST", "/auth/logout", "Refresh Token 폐기"],
      ],
    },
    {
      id: "auth-password-reset",
      category: "계정 복구",
      title: "비밀번호 재설정",
      description: "이메일 소유를 확인한 후 일회성 복구 흐름으로 비밀번호를 바꿉니다.",
      steps: [
        ["POST", "/auth/password/reset/send", "복구 메일 발송"],
        ["POST", "/auth/password/reset/confirm", "복구 코드 확인"],
        ["POST", "/auth/password/reset", "새 비밀번호 저장"],
      ],
    },
    {
      id: "auth-service-call",
      category: "서비스 인증",
      title: "내부 서비스 호출",
      description: "순수 서비스 인증과 사용자 위임 호출을 대상 audience에 맞게 나눕니다.",
      steps: [
        ["POST", "/auth/token/service", "client-credentials 토큰 발급"],
        ["POST", "/auth/token/exchange", "필요한 경우 사용자 위임 토큰 교환"],
      ],
    },
  ],
  user: [
    {
      id: "user-profile",
      category: "프로필",
      title: "프로필 조회와 수정",
      description: "현재 사용자의 프로필을 불러와 필드 또는 프로필 이미지를 갱신합니다.",
      steps: [
        ["GET", "/users/profile", "현재 프로필 조회"],
        ["PATCH", "/users/profile", "프로필 필드 수정"],
        ["POST", "/users/profile/image", "필요한 경우 이미지 교체"],
      ],
    },
    {
      id: "user-ssh-key",
      category: "SSH 키",
      title: "SSH 키 등록",
      description: "사용자 키를 직접 등록하거나 서버에서 키 쌍을 생성하고 목록으로 검증합니다.",
      steps: [
        ["GET", "/users/ssh-keys", "기존 키 확인"],
        ["POST", "/users/ssh-keys", "공개키 등록"],
        ["POST", "/users/ssh-keys/generate", "또는 키 쌍 생성"],
      ],
    },
    {
      id: "user-plan-upgrade",
      category: "플랜",
      title: "플랜 사용량과 업그레이드",
      description: "현재 사용량을 확인한 뒤 상위 플랜 요청을 등록하고 처리 상태를 조회합니다.",
      steps: [
        ["GET", "/users/usage", "자원 사용량 확인"],
        ["POST", "/users/{userId}/upgrade-requests", "업그레이드 요청"],
        ["GET", "/users/{userId}/upgrade-requests", "요청 상태 조회"],
      ],
    },
    {
      id: "user-support",
      category: "지원",
      title: "문의 등록과 종료",
      description: "사용자 문의를 등록하고 내 문의 이력을 확인한 뒤 해결 처리합니다.",
      steps: [
        ["POST", "/users/support-inquiries", "문의 등록"],
        ["GET", "/users/support-inquiries", "내 문의 조회"],
        ["PATCH", "/users/support-inquiries/{inquiryId}/close", "문의 종료"],
      ],
    },
  ],
  vm: [
    {
      id: "vm-create",
      category: "인스턴스",
      title: "VM 생성과 상태 확인",
      description: "생성 가능 자원을 확인하고 VM을 요청한 뒤 SSE와 상세 조회로 준비 상태를 추적합니다.",
      steps: [
        ["GET", "/vms/availability", "생성 가능 자원 확인"],
        ["POST", "/vms", "VM 생성 요청"],
        ["POST", "/vms/events/ticket", "SSE 일회성 티켓 발급"],
        ["GET", "/vms/events/subscribe", "생성 상태 구독"],
        ["GET", "/vms/{vmId}", "RUNNING 및 접속 정보 확인"],
      ],
    },
    {
      id: "vm-port-publish",
      category: "네트워크",
      title: "서비스 포트 공개",
      description: "서브도메인 사용 가능 여부를 확인하고 포트와 Access 정책을 연결합니다.",
      steps: [
        ["GET", "/vms/{vmId}/ports/subdomain/check", "서브도메인 중복 확인"],
        ["POST", "/vms/{vmId}/ports", "포트 및 라우트 생성"],
        ["POST", "/vms/{vmId}/ports/{portId}/access", "필요한 경우 Access 정책 적용"],
        ["GET", "/vms/{vmId}/ports", "최종 공개 주소 확인"],
      ],
    },
    {
      id: "vm-metrics",
      category: "모니터링",
      title: "VM 실시간 메트릭",
      description: "단기 메트릭 티켓을 발급하고 현재 자원과 실시간 스트림을 불러옵니다.",
      steps: [
        ["POST", "/vms/{vmId}/metrics/ticket", "메트릭 티켓 발급"],
        ["GET", "/vms/{vmId}/metrics/current", "최신 스냅샷 조회"],
        ["GET", "/vms/{vmId}/metrics/stream", "실시간 메트릭 구독"],
      ],
    },
    {
      id: "vm-organization",
      category: "조직",
      title: "조직 생성과 VM 공유",
      description: "조직을 만들고 멤버를 초대한 뒤 공동으로 사용할 VM을 연결합니다.",
      steps: [
        ["POST", "/vms/organizations", "조직 생성"],
        ["POST", "/vms/organizations/{orgId}/members", "멤버 초대"],
        ["PATCH", "/vms/organizations/{orgId}/members/{memberId}/respond", "초대 수락"],
        ["POST", "/vms/organizations/{orgId}/vms", "VM 연결"],
      ],
    },
  ],
  ops: [
    {
      id: "ops-docker-install",
      category: "런타임",
      title: "Docker 준비",
      description: "비동기 설치를 요청하고 상태를 폴링한 뒤 실제 컨테이너 실행 환경을 확인합니다.",
      steps: [
        ["POST", "/ops/{vmId}/docker/install", "Docker 설치 요청"],
        ["GET", "/ops/{vmId}/docker/status", "설치 단계 폴링"],
        ["GET", "/ops/{vmId}/docker/containers", "실행 환경 확인"],
      ],
    },
    {
      id: "ops-compose-deploy",
      category: "배포",
      title: "Compose 분석과 배포",
      description: "저장소의 Compose를 탐지·검토하고 배포를 시작한 뒤 DB 기반 이벤트로 진행률을 추적합니다.",
      steps: [
        ["POST", "/ops/{vmId}/deployments/compose/detect", "Compose 구조 탐지"],
        ["POST", "/ops/{vmId}/deployments/compose/review", "서비스·포트 검토"],
        ["POST", "/ops/{vmId}/deployments", "배포 시작"],
        ["GET", "/ops/{vmId}/deployments/{deploymentId}/events", "배포 로그 SSE 구독"],
        ["GET", "/ops/{vmId}/deployments/{deploymentId}", "최종 상태 확인"],
      ],
    },
    {
      id: "ops-auto-preview",
      category: "Auto Preview",
      title: "OpenAPI로 서비스 화면 생성",
      description: "API 역량을 분석하고 시나리오 계획을 확정한 뒤 동일 Runtime으로 Preview를 배포합니다.",
      steps: [
        ["POST", "/ops/preview/analyze", "OpenAPI와 서비스 의도 분석"],
        ["POST", "/ops/preview/review", "구조화된 검수"],
        ["POST", "/ops/preview/plan/propose", "화면·플로우 계획 제안"],
        ["POST", "/ops/preview/plan/apply", "선택한 계획 적용"],
        ["POST", "/ops/{vmId}/preview/deploy", "선택한 VM에 배포"],
      ],
    },
    {
      id: "ops-backup",
      category: "백업",
      title: "DB 백업 생성과 검증",
      description: "VM 내 DB 백업을 생성하고 무결성을 확인한 후 권한이 확인된 다운로드를 제공합니다.",
      steps: [
        ["POST", "/ops/{vmId}/backups", "암호화 백업 생성"],
        ["GET", "/ops/{vmId}/backups", "백업 목록 확인"],
        ["POST", "/ops/{vmId}/backups/{backupId}/verify", "복원 가능성 검증"],
        ["GET", "/ops/{vmId}/backups/{backupId}/download", "백업 다운로드"],
      ],
    },
  ],
};

const HTTP_METHODS = new Set(["get", "post", "put", "patch", "delete", "head", "options", "trace"]);
const elements = {
  serviceList: document.querySelector("#service-list"),
  breadcrumb: document.querySelector("#breadcrumb-service"),
  title: document.querySelector("#service-title"),
  description: document.querySelector("#service-description"),
  badge: document.querySelector("#service-badge"),
  rawSpecLink: document.querySelector("#raw-spec-link"),
  schemaToggle: document.querySelector("#toggle-schemas"),
  refreshButton: document.querySelector("#refresh-spec"),
  referenceTab: document.querySelector("#reference-tab"),
  flowsTab: document.querySelector("#flows-tab"),
  referenceView: document.querySelector("#reference-view"),
  flowsView: document.querySelector("#flows-view"),
  specState: document.querySelector("#spec-state"),
  specMessage: document.querySelector("#spec-message"),
  serverSelect: document.querySelector("#server-select"),
  authorizeButton: document.querySelector("#authorize-api"),
  referenceSearch: document.querySelector("#reference-search-input"),
  openapiVersion: document.querySelector("#openapi-version"),
  operationCount: document.querySelector("#operation-count"),
  tagCount: document.querySelector("#tag-count"),
  swaggerUi: document.querySelector("#swagger-ui"),
  swaggerLoading: document.querySelector("#swagger-loading"),
  flowSearch: document.querySelector("#flow-search-input"),
  flowCategories: document.querySelector("#flow-categories"),
  flowGrid: document.querySelector("#flow-grid"),
  flowEmpty: document.querySelector("#flow-empty"),
  flowResultCount: document.querySelector("#flow-result-count"),
};

let selectedService = getInitialService();
let selectedView = getInitialView();
let selectedFlowCategory = "전체";
let selectedServerUrl = "";
let currentSpec;
let swaggerInstance;
let authorizationUnsubscribe;
let loadSequence = 0;
let schemasCollapsed = false;

function getInitialService() {
  const requested = new URLSearchParams(window.location.search).get("service");
  const remembered = window.localStorage.getItem("gamjabox-api-service");
  return SERVICES.find((service) => service.id === requested)
    ?? SERVICES.find((service) => service.id === remembered)
    ?? SERVICES.find((service) => service.id === "ops");
}

function getInitialView() {
  const requested = new URLSearchParams(window.location.search).get("view");
  return requested === "flows" ? "flows" : "reference";
}

function createServiceNavigation() {
  const fragment = document.createDocumentFragment();
  SERVICES.forEach((service) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "service-item";
    button.dataset.service = service.id;
    button.innerHTML = `
      <span class="service-icon">${service.badge}</span>
      <span class="service-copy"><strong>${service.shortName}</strong><small>${service.navDescription}</small></span>
      <svg class="service-arrow" viewBox="0 0 20 20" aria-hidden="true"><path d="m7.5 4 6 6-6 6-1.4-1.4 4.6-4.6-4.6-4.6L7.5 4Z"/></svg>
    `;
    button.addEventListener("click", () => selectService(service));
    fragment.appendChild(button);
  });
  elements.serviceList.replaceChildren(fragment);
}

function selectService(service, options = {}) {
  selectedService = service;
  selectedFlowCategory = "전체";
  window.localStorage.setItem("gamjabox-api-service", service.id);
  updateUrl();

  document.querySelectorAll(".service-item").forEach((item) => {
    const active = item.dataset.service === service.id;
    item.classList.toggle("is-active", active);
    item.setAttribute("aria-current", active ? "page" : "false");
  });

  elements.breadcrumb.textContent = service.shortName;
  elements.title.textContent = service.name;
  elements.description.textContent = service.description;
  elements.badge.textContent = service.badge;
  elements.rawSpecLink.href = service.specUrl;
  document.title = `${service.name} · GamjaBox API Console`;
  renderFlows();
  loadSpecification(service, Boolean(options.forceRefresh));
}

function setView(view) {
  selectedView = view;
  const referenceActive = view === "reference";
  elements.referenceView.hidden = !referenceActive;
  elements.flowsView.hidden = referenceActive;
  elements.referenceTab.classList.toggle("is-active", referenceActive);
  elements.flowsTab.classList.toggle("is-active", !referenceActive);
  elements.referenceTab.setAttribute("aria-selected", String(referenceActive));
  elements.flowsTab.setAttribute("aria-selected", String(!referenceActive));
  elements.schemaToggle.hidden = !referenceActive;
  updateUrl();
  if (!referenceActive) renderFlows();
}

function updateUrl() {
  const url = new URL(window.location.href);
  url.searchParams.set("service", selectedService.id);
  if (selectedView === "flows") url.searchParams.set("view", "flows");
  else url.searchParams.delete("view");
  window.history.replaceState({ service: selectedService.id, view: selectedView }, "", url);
}

async function loadSpecification(service, forceRefresh) {
  const sequence = ++loadSequence;
  const requestUrl = forceRefresh ? `${service.specUrl}?t=${Date.now()}` : service.specUrl;
  setLoadingState();

  try {
    const response = await fetch(requestUrl, {
      credentials: "same-origin",
      cache: forceRefresh ? "reload" : "default",
      headers: { Accept: "application/json" },
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const specDocument = await response.json();
    if (sequence !== loadSequence) return;

    currentSpec = specDocument;
    const operations = getOperations(specDocument);
    const tags = new Set(operations.flatMap(({ operation }) => operation?.tags ?? []));
    populateServers(specDocument);

    elements.specState.className = "spec-state is-ready";
    elements.specState.innerHTML = "<i></i>연결됨";
    elements.specMessage.textContent = "";
    elements.openapiVersion.textContent = specDocument.openapi ?? "—";
    elements.operationCount.textContent = operations.length.toLocaleString("ko-KR");
    elements.tagCount.textContent = tags.size.toLocaleString("ko-KR");
    updateAuthorizationControl(specDocument);
    renderSwagger(specDocument);
    renderFlows();
  } catch (error) {
    if (sequence !== loadSequence) return;
    currentSpec = undefined;
    elements.specState.className = "spec-state is-error";
    elements.specState.innerHTML = "<i></i>연결 실패";
    elements.specMessage.textContent = `OpenAPI 문서를 불러오지 못했습니다. ${error.message}`;
    elements.openapiVersion.textContent = "—";
    elements.operationCount.textContent = "—";
    elements.tagCount.textContent = "—";
    elements.serverSelect.replaceChildren(new Option("서버 확인 불가", ""));
    elements.serverSelect.disabled = true;
    elements.authorizeButton.hidden = true;
    elements.authorizeButton.disabled = true;
    elements.swaggerLoading.hidden = true;
    renderFlows();
  }
}

function getOperations(specDocument) {
  return Object.entries(specDocument.paths ?? {}).flatMap(([path, pathItem]) =>
    Object.entries(pathItem ?? {})
      .filter(([method]) => HTTP_METHODS.has(method.toLowerCase()))
      .map(([method, operation]) => ({ path, method: method.toUpperCase(), operation })),
  );
}

function populateServers(specDocument) {
  const servers = specDocument.servers?.length
    ? specDocument.servers
    : [{ url: window.location.origin, description: "Current origin" }];
  const preferred = servers.some(({ url }) => url === selectedServerUrl) ? selectedServerUrl : servers[0].url;
  selectedServerUrl = preferred;
  elements.serverSelect.replaceChildren(...servers.map(({ url, description }) => {
    const label = description ? `${description} · ${url}` : url;
    return new Option(label, url, false, url === preferred);
  }));
  elements.serverSelect.disabled = false;
  elements.serverSelect.title = preferred;
}

function setLoadingState() {
  elements.specState.className = "spec-state is-loading";
  elements.specState.innerHTML = "<i></i>확인 중";
  elements.specMessage.textContent = "";
  elements.openapiVersion.textContent = "—";
  elements.operationCount.textContent = "—";
  elements.tagCount.textContent = "—";
  elements.serverSelect.replaceChildren(new Option("불러오는 중…", ""));
  elements.serverSelect.disabled = true;
  elements.authorizeButton.hidden = true;
  elements.authorizeButton.disabled = true;
  elements.swaggerLoading.hidden = false;
}

function renderSwagger(specDocument) {
  authorizationUnsubscribe?.();
  authorizationUnsubscribe = undefined;
  elements.swaggerUi.replaceChildren();
  if (!window.SwaggerUIBundle || !window.SwaggerUIStandalonePreset) {
    elements.swaggerLoading.hidden = true;
    elements.specState.className = "spec-state is-error";
    elements.specState.innerHTML = "<i></i>UI 자산 로드 실패";
    elements.specMessage.textContent = "Swagger UI 자산을 불러오지 못했습니다.";
    return;
  }

  const renderedSpec = structuredClone(specDocument);
  if (selectedServerUrl) renderedSpec.servers = [{ url: selectedServerUrl }];
  swaggerInstance = window.SwaggerUIBundle({
    spec: renderedSpec,
    dom_id: "#swagger-ui",
    deepLinking: true,
    displayRequestDuration: true,
    docExpansion: "list",
    filter: true,
    persistAuthorization: true,
    showExtensions: true,
    showCommonExtensions: true,
    tryItOutEnabled: false,
    defaultModelsExpandDepth: 1,
    presets: [window.SwaggerUIBundle.presets.apis, window.SwaggerUIStandalonePreset],
    plugins: [window.SwaggerUIBundle.plugins.DownloadUrl],
    layout: "BaseLayout",
    oauth2RedirectUrl: `${window.location.origin}/swagger-ui/oauth2-redirect.html`,
    requestInterceptor: (request) => {
      request.credentials = "include";
      return request;
    },
    onComplete: () => {
      elements.swaggerLoading.hidden = true;
      elements.authorizeButton.disabled = false;
      const store = swaggerInstance?.getSystem?.().getStore?.();
      if (store) authorizationUnsubscribe = store.subscribe(updateAuthorizationStatus);
      updateAuthorizationStatus();
      applyReferenceFilter();
      window.requestAnimationFrame(applySchemasState);
    },
  });
}

function updateAuthorizationStatus() {
  const authorized = swaggerInstance?.getSystem?.().authSelectors?.authorized?.();
  const isAuthorized = Number(authorized?.size ?? 0) > 0;
  elements.authorizeButton.classList.toggle("is-authorized", isAuthorized);
  elements.authorizeButton.querySelector("span").textContent = isAuthorized ? "인증 설정됨" : "API 인증";
}

function updateAuthorizationControl(specDocument) {
  const schemes = specDocument.components?.securitySchemes ?? {};
  const hasSecuritySchemes = Object.keys(schemes).length > 0;
  elements.authorizeButton.hidden = !hasSecuritySchemes;
  elements.authorizeButton.disabled = true;
  elements.authorizeButton.title = hasSecuritySchemes
    ? `${Object.keys(schemes).join(", ")} 인증 정보 설정`
    : "";
}

function applyReferenceFilter() {
  swaggerInstance?.getSystem?.().layoutActions?.updateFilter?.(elements.referenceSearch.value.trim());
}

function renderFlows() {
  const flows = FLOW_CATALOG[selectedService.id] ?? [];
  const categories = ["전체", ...new Set(flows.map(({ category }) => category))];
  if (!categories.includes(selectedFlowCategory)) selectedFlowCategory = "전체";

  elements.flowCategories.replaceChildren(...categories.map((category) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `category-chip${category === selectedFlowCategory ? " is-active" : ""}`;
    button.textContent = category;
    button.setAttribute("aria-pressed", String(category === selectedFlowCategory));
    button.addEventListener("click", () => {
      selectedFlowCategory = category;
      renderFlows();
    });
    return button;
  }));

  const query = elements.flowSearch.value.trim().toLocaleLowerCase("ko-KR");
  const visibleFlows = flows.filter((flow) => {
    if (selectedFlowCategory !== "전체" && flow.category !== selectedFlowCategory) return false;
    const searchable = [flow.title, flow.description, flow.category, ...flow.steps.flat()].join(" ").toLocaleLowerCase("ko-KR");
    return !query || searchable.includes(query);
  });

  elements.flowResultCount.textContent = `${visibleFlows.length} flow${visibleFlows.length === 1 ? "" : "s"}`;
  elements.flowGrid.replaceChildren(...visibleFlows.map(createFlowCard));
  elements.flowEmpty.hidden = visibleFlows.length > 0;
}

function createFlowCard(flow) {
  const article = document.createElement("article");
  article.className = "flow-card";
  article.innerHTML = `
    <div class="flow-card-head">
      <span class="flow-category">${flow.category}</span>
      <span class="flow-step-count">${flow.steps.length}단계</span>
      <h4>${flow.title}</h4>
      <p>${flow.description}</p>
    </div>
    <ol class="flow-steps"></ol>
  `;

  const stepList = article.querySelector(".flow-steps");
  flow.steps.forEach(([method, path, fallbackSummary], index) => {
    const operation = currentSpec?.paths?.[path]?.[method.toLowerCase()];
    const missing = Boolean(currentSpec) && !operation;
    const step = document.createElement("li");
    step.className = missing ? "is-missing" : "";
    step.innerHTML = `
      <span class="step-index">${String(index + 1).padStart(2, "0")}</span>
      <button class="step-reference" type="button" title="API Reference에서 보기">
        <span class="method method-${method.toLowerCase()}">${method}</span>
        <code>${path}</code>
        <small>${operation?.summary || fallbackSummary}${missing ? " · 현재 스펙에서 확인되지 않음" : ""}</small>
      </button>
    `;
    step.querySelector("button").addEventListener("click", () => openReference(path));
    stepList.appendChild(step);
  });
  return article;
}

function openReference(path) {
  elements.referenceSearch.value = path;
  setView("reference");
  applyReferenceFilter();
  window.requestAnimationFrame(() => elements.referenceSearch.focus());
}

function toggleSchemas() {
  schemasCollapsed = !schemasCollapsed;
  applySchemasState();
}

function applySchemasState() {
  const models = elements.swaggerUi.querySelector("section.models");
  const label = elements.schemaToggle.querySelector("span");
  elements.schemaToggle.disabled = !models;
  elements.schemaToggle.setAttribute("aria-expanded", String(!schemasCollapsed));
  label.textContent = schemasCollapsed ? "Schemas 펼치기" : "Schemas 접기";
  if (!models) return;
  models.classList.toggle("api-console-collapsed", schemasCollapsed);
}

elements.swaggerUi.addEventListener("click", (event) => {
  const heading = event.target.closest("h4");
  if (!heading || heading.parentElement?.matches("section.models") !== true) return;
  event.preventDefault();
  event.stopPropagation();
  toggleSchemas();
}, true);

elements.schemaToggle.addEventListener("click", toggleSchemas);
elements.referenceTab.addEventListener("click", () => setView("reference"));
elements.flowsTab.addEventListener("click", () => setView("flows"));
elements.referenceSearch.addEventListener("input", applyReferenceFilter);
elements.flowSearch.addEventListener("input", renderFlows);
elements.serverSelect.addEventListener("change", () => {
  selectedServerUrl = elements.serverSelect.value;
  elements.serverSelect.title = selectedServerUrl;
  if (currentSpec) renderSwagger(currentSpec);
});
elements.authorizeButton.addEventListener("click", () => {
  elements.swaggerUi.querySelector(".btn.authorize")?.click();
});

elements.refreshButton.addEventListener("click", () => {
  elements.refreshButton.classList.add("is-spinning");
  selectService(selectedService, { forceRefresh: true });
  window.setTimeout(() => elements.refreshButton.classList.remove("is-spinning"), 650);
});

document.addEventListener("keydown", (event) => {
  if (event.key !== "/" || event.metaKey || event.ctrlKey || event.altKey) return;
  const target = event.target;
  if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) return;
  event.preventDefault();
  (selectedView === "reference" ? elements.referenceSearch : elements.flowSearch).focus();
});

window.addEventListener("popstate", () => {
  const params = new URLSearchParams(window.location.search);
  const service = SERVICES.find((candidate) => candidate.id === params.get("service"));
  const view = params.get("view") === "flows" ? "flows" : "reference";
  if (service && service.id !== selectedService.id) selectService(service);
  if (view !== selectedView) setView(view);
});

createServiceNavigation();
setView(selectedView);
selectService(selectedService);
