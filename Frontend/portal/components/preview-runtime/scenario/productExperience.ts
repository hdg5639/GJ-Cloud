import type {
  PreviewCompiledScenario,
  PreviewCompiledScenarioStage,
  PreviewScenarioStageRole,
} from "@/lib/types";
import type { PreviewCapability } from "../types";

export type ProductArchetype =
  | "CALENDAR"
  | "COMMERCE"
  | "COMMUNITY"
  | "CONTENT"
  | "BOOKING"
  | "MESSAGING"
  | "FILES"
  | "LEARNING"
  | "WORKSPACE"
  | "ADMIN";

export type ExperienceScreenKind =
  | "HOME"
  | "CALENDAR"
  | "CATALOG"
  | "FEED"
  | "INBOX"
  | "EDITOR"
  | "FILES"
  | "BOOKING"
  | "LEARNING"
  | "COLLECTION"
  | "PROFILE";

export type ExperienceOverlayKind =
  | "FORM_MODAL"
  | "DETAIL_DRAWER"
  | "REVIEW_MODAL"
  | "DANGER_CONFIRM"
  | "PROGRESS_MODAL"
  | "RESULT_TOAST";

export interface ExperienceOverlay {
  id: string;
  actionId: string;
  screenId: string;
  kind: ExperienceOverlayKind;
  title: string;
  stageIds: string[];
}

export interface ExperienceAction {
  id: string;
  scenarioId: string;
  screenId: string;
  label: string;
  tone: "PRIMARY" | "SECONDARY" | "DANGER";
  icon: string;
  stageIds: string[];
  overlayIds: string[];
}

export interface ExperienceScreen {
  id: string;
  label: string;
  title: string;
  description: string;
  kind: ExperienceScreenKind;
  resourceNames: string[];
  capabilityIds: string[];
  actionIds: string[];
}

export interface ProductExperienceGraph {
  id: string;
  productName: string;
  archetype: ProductArchetype;
  screens: ExperienceScreen[];
  actions: ExperienceAction[];
  overlays: ExperienceOverlay[];
  defaultScreenId: string;
}

type ArchetypeDefinition = {
  archetype: ProductArchetype;
  keywords: string[];
  productName: string;
  screens: Array<Pick<ExperienceScreen, "id" | "label" | "title" | "description" | "kind">>;
};

const DEFINITIONS: ArchetypeDefinition[] = [
  {
    archetype: "CALENDAR",
    keywords: ["calendar", "schedule", "일정", "캘린더", "event", "이벤트"],
    productName: "Daylight",
    screens: [
      { id: "today", label: "오늘", title: "오늘의 일정", description: "해야 할 일과 다가오는 일정을 한눈에 확인하세요.", kind: "HOME" },
      { id: "calendar", label: "캘린더", title: "캘린더", description: "팀과 개인 일정을 함께 관리하세요.", kind: "CALENDAR" },
      { id: "upcoming", label: "다가오는 일정", title: "다가오는 일정", description: "예정된 일정을 빠르게 살펴보세요.", kind: "COLLECTION" },
    ],
  },
  {
    archetype: "COMMERCE",
    keywords: ["product", "cart", "order", "shop", "commerce", "상품", "장바구니", "주문", "결제", "market"],
    productName: "Morrow Market",
    screens: [
      { id: "discover", label: "둘러보기", title: "오늘의 발견", description: "취향에 맞는 새로운 상품을 만나보세요.", kind: "HOME" },
      { id: "catalog", label: "스토어", title: "스토어", description: "카테고리별 상품을 둘러보고 비교하세요.", kind: "CATALOG" },
      { id: "orders", label: "주문", title: "나의 주문", description: "주문과 배송 상태를 확인하세요.", kind: "COLLECTION" },
    ],
  },
  {
    archetype: "COMMUNITY",
    keywords: ["community", "post", "comment", "follow", "social", "feed", "커뮤니티", "게시글", "댓글", "피드"],
    productName: "Common",
    screens: [
      { id: "feed", label: "피드", title: "새로운 이야기", description: "사람들과 관심사를 나누고 발견하세요.", kind: "FEED" },
      { id: "discover", label: "발견", title: "인기 있는 이야기", description: "지금 많은 사람이 이야기하는 주제입니다.", kind: "CATALOG" },
      { id: "profile", label: "내 프로필", title: "내 활동", description: "내가 남긴 이야기와 반응을 모아보세요.", kind: "PROFILE" },
    ],
  },
  {
    archetype: "CONTENT",
    keywords: ["article", "content", "publish", "editor", "document", "blog", "글", "콘텐츠", "문서", "발행", "작가"],
    productName: "Draftroom",
    screens: [
      { id: "library", label: "라이브러리", title: "내 콘텐츠", description: "초안부터 발행된 콘텐츠까지 한곳에서 관리하세요.", kind: "COLLECTION" },
      { id: "editor", label: "에디터", title: "새로운 이야기", description: "아이디어를 독자가 읽고 싶은 이야기로 완성하세요.", kind: "EDITOR" },
      { id: "published", label: "발행됨", title: "발행된 콘텐츠", description: "공개된 콘텐츠와 반응을 확인하세요.", kind: "CATALOG" },
    ],
  },
  {
    archetype: "BOOKING",
    keywords: ["reservation", "booking", "seat", "room", "appointment", "예약", "좌석", "숙소", "방문"],
    productName: "Gather",
    screens: [
      { id: "explore", label: "공간 찾기", title: "어디에서 만나세요?", description: "시간과 목적에 맞는 공간을 찾아보세요.", kind: "CATALOG" },
      { id: "booking", label: "예약하기", title: "예약 가능한 시간", description: "원하는 날짜와 시간을 선택하세요.", kind: "BOOKING" },
      { id: "my-bookings", label: "내 예약", title: "나의 예약", description: "다가오는 예약과 지난 기록을 확인하세요.", kind: "COLLECTION" },
    ],
  },
  {
    archetype: "MESSAGING",
    keywords: ["message", "chat", "conversation", "inbox", "ticket", "support", "메시지", "채팅", "대화", "문의"],
    productName: "Relay",
    screens: [
      { id: "inbox", label: "받은 편지함", title: "대화", description: "중요한 대화를 놓치지 마세요.", kind: "INBOX" },
      { id: "people", label: "사람들", title: "연락처", description: "함께할 사람을 찾고 대화를 시작하세요.", kind: "COLLECTION" },
      { id: "saved", label: "보관함", title: "저장한 대화", description: "나중에 다시 볼 대화를 모았습니다.", kind: "COLLECTION" },
    ],
  },
  {
    archetype: "FILES",
    keywords: ["file", "folder", "asset", "upload", "download", "파일", "폴더", "업로드", "다운로드", "미디어"],
    productName: "Drop",
    screens: [
      { id: "files", label: "내 파일", title: "내 파일", description: "모든 파일과 폴더를 한곳에서 관리하세요.", kind: "FILES" },
      { id: "shared", label: "공유됨", title: "나와 공유된 항목", description: "팀이 공유한 파일을 확인하세요.", kind: "COLLECTION" },
      { id: "recent", label: "최근", title: "최근 사용한 항목", description: "최근 열어본 파일로 바로 돌아가세요.", kind: "COLLECTION" },
    ],
  },
  {
    archetype: "LEARNING",
    keywords: ["course", "lesson", "student", "learn", "class", "교육", "강의", "수업", "학습"],
    productName: "Luma Class",
    screens: [
      { id: "learning", label: "내 학습", title: "이어서 학습하기", description: "진행 중인 과정으로 바로 돌아가세요.", kind: "LEARNING" },
      { id: "courses", label: "강의 찾기", title: "새로운 강의", description: "관심사와 목표에 맞는 강의를 찾아보세요.", kind: "CATALOG" },
      { id: "progress", label: "학습 기록", title: "나의 성장", description: "완료한 학습과 성취를 확인하세요.", kind: "PROFILE" },
    ],
  },
  {
    archetype: "ADMIN",
    keywords: ["admin", "administrator", "backoffice", "governance", "운영자", "관리자", "백오피스"],
    productName: "Control",
    screens: [
      { id: "overview", label: "개요", title: "운영 현황", description: "서비스의 주요 상태를 확인하세요.", kind: "HOME" },
      { id: "resources", label: "리소스", title: "리소스 관리", description: "서비스 리소스를 조회하고 관리하세요.", kind: "COLLECTION" },
      { id: "settings", label: "설정", title: "서비스 설정", description: "운영 정책과 권한을 설정하세요.", kind: "PROFILE" },
    ],
  },
];

const FALLBACK_DEFINITION: ArchetypeDefinition = {
  archetype: "WORKSPACE",
  keywords: [],
  productName: "Canvas",
  screens: [
    { id: "home", label: "홈", title: "다시 오신 것을 환영해요", description: "최근 활동과 다음 할 일을 확인하세요.", kind: "HOME" },
    { id: "explore", label: "둘러보기", title: "모든 항목", description: "필요한 항목을 찾고 바로 작업을 시작하세요.", kind: "CATALOG" },
    { id: "mine", label: "내 공간", title: "내가 저장한 항목", description: "내 활동과 저장한 항목을 모아보세요.", kind: "PROFILE" },
  ],
};

const ROLE_PHASE: Partial<Record<PreviewScenarioStageRole, ExperienceOverlayKind>> = {
  AUTHENTICATE: "FORM_MODAL",
  SELECT_CONTEXT: "FORM_MODAL",
  CONFIGURE: "FORM_MODAL",
  PREPARE: "FORM_MODAL",
  INSPECT: "DETAIL_DRAWER",
  COMPARE: "DETAIL_DRAWER",
  REVIEW: "REVIEW_MODAL",
  COMMIT: "PROGRESS_MODAL",
  WAIT: "PROGRESS_MODAL",
  TRACK: "PROGRESS_MODAL",
  RECOVER: "FORM_MODAL",
  VERIFY: "RESULT_TOAST",
  COMPLETE: "RESULT_TOAST",
};

function normalizeWords(value: string): string[] {
  return value
    .toLowerCase()
    .split(/[^a-z0-9가-힣]+/)
    .filter((word) => word.length > 1);
}

function searchableText(scenarios: PreviewCompiledScenario[], capabilities: PreviewCapability[]): string {
  return [
    ...scenarios.flatMap((scenario) => [
      scenario.name,
      scenario.goal,
      scenario.actor,
      ...scenario.stages.flatMap((stage) => [stage.intent, stage.operationId ?? ""]),
    ]),
    ...capabilities.flatMap((capability) => [
      capability.resourceName,
      capability.operationId ?? "",
      capability.action ?? "",
      capability.path,
    ]),
  ].join(" ").toLowerCase();
}

function selectDefinition(
  scenarios: PreviewCompiledScenario[],
  capabilities: PreviewCapability[]
): ArchetypeDefinition {
  const text = searchableText(scenarios, capabilities);
  const ranked = DEFINITIONS.map((definition) => ({
    definition,
    score: definition.keywords.reduce((score, keyword) => score + (text.includes(keyword) ? 1 : 0), 0),
  })).sort((left, right) => right.score - left.score);
  const best = ranked[0];
  if (!best || best.score === 0) return FALLBACK_DEFINITION;

  // 일반 CRUD/운영이라는 이유만으로 관리자 화면으로 수렴하지 않는다. 명시적인 관리자 어휘가
  // 두 번 이상 관찰될 때만 ADMIN을 허용한다.
  if (best.definition.archetype === "ADMIN" && best.score < 2) return FALLBACK_DEFINITION;
  return best.definition;
}

function slug(value: string): string {
  const normalized = value
    .toLowerCase()
    .replace(/[^a-z0-9가-힣]+/g, "-")
    .replace(/^-+|-+$/g, "");
  return normalized || "experience";
}

function humanize(value: string): string {
  return value
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replaceAll("_", " ")
    .replaceAll("-", " ")
    .trim();
}

function actionLabel(
  scenario: PreviewCompiledScenario,
  capability: PreviewCapability | undefined
): string {
  if (capability?.action) return humanize(capability.action);
  if (capability?.type === "CREATE") return `새 ${humanize(capability.resourceName)}`;
  if (capability?.type === "UPDATE") return "수정하기";
  if (capability?.type === "DELETE") return "삭제하기";
  if (capability?.type === "LOGIN") return "로그인";
  const mutation = scenario.stages.find((stage) => stage.role === "COMMIT");
  return mutation?.intent || scenario.name;
}

function actionIcon(capability: PreviewCapability | undefined): string {
  if (capability?.type === "CREATE") return "+";
  if (capability?.type === "DELETE") return "×";
  if (capability?.type === "UPDATE") return "↗";
  if (capability?.type === "LOGIN") return "→";
  return "→";
}

function actionTone(capability: PreviewCapability | undefined): ExperienceAction["tone"] {
  if (capability?.risk === "DESTRUCTIVE" || capability?.type === "DELETE") return "DANGER";
  if (capability?.type === "CREATE" || capability?.type === "LOGIN") return "PRIMARY";
  return "SECONDARY";
}

function resourceForScenario(
  scenario: PreviewCompiledScenario,
  capabilities: PreviewCapability[]
): string {
  for (const stage of scenario.stages) {
    const capability = capabilities.find((candidate) => candidate.id === stage.capabilityId);
    if (capability?.resourceName) return capability.resourceName;
  }
  return normalizeWords(scenario.name)[0] ?? "item";
}

function selectScreenId(
  scenario: PreviewCompiledScenario,
  definition: ArchetypeDefinition,
  capabilities: PreviewCapability[]
): string {
  const text = [
    scenario.name,
    scenario.goal,
    ...scenario.stages.map((stage) => stage.intent),
    resourceForScenario(scenario, capabilities),
  ].join(" ").toLowerCase();
  const scored = definition.screens.map((screen) => ({
    id: screen.id,
    score: normalizeWords(`${screen.label} ${screen.title} ${screen.kind}`)
      .reduce((total, token) => total + (text.includes(token) ? 1 : 0), 0),
  })).sort((left, right) => right.score - left.score);
  if (scored[0]?.score) return scored[0].id;

  const mutation = scenario.stages.some((stage) => stage.role === "PREPARE" || stage.role === "COMMIT");
  return mutation ? definition.screens[0].id : definition.screens[1]?.id ?? definition.screens[0].id;
}

function groupOverlayStages(
  actionId: string,
  screenId: string,
  scenario: PreviewCompiledScenario,
  destructive: boolean
): ExperienceOverlay[] {
  const groups: Array<{ kind: ExperienceOverlayKind; stages: PreviewCompiledScenarioStage[] }> = [];
  for (const stage of scenario.stages) {
    let kind = ROLE_PHASE[stage.role];
    if (stage.role === "REVIEW" && destructive) kind = "DANGER_CONFIRM";
    if (!kind) continue;
    const previous = groups.at(-1);
    if (previous?.kind === kind) previous.stages.push(stage);
    else groups.push({ kind, stages: [stage] });
  }
  if (groups.length === 0) {
    groups.push({ kind: "DETAIL_DRAWER", stages: scenario.stages });
  }
  return groups.map((group, index) => ({
    id: `${actionId}-overlay-${index + 1}`,
    actionId,
    screenId,
    kind: group.kind,
    title: group.stages[0]?.intent || scenario.name,
    stageIds: group.stages.map((stage) => stage.id),
  }));
}

export function composeProductExperience(
  scenarios: PreviewCompiledScenario[],
  capabilities: PreviewCapability[]
): ProductExperienceGraph {
  const available = scenarios.filter(
    (scenario) => scenario.status !== "UNSUPPORTED" && scenario.stages.length > 0
  );
  const definition = selectDefinition(available, capabilities);
  const actions: ExperienceAction[] = [];
  const overlays: ExperienceOverlay[] = [];

  for (const scenario of available) {
    const actionId = `action-${slug(scenario.id)}`;
    const screenId = selectScreenId(scenario, definition, capabilities);
    const capability = scenario.stages
      .map((stage) => capabilities.find((candidate) => candidate.id === stage.capabilityId))
      .find((candidate) => candidate?.type && candidate.type !== "LIST" && candidate.type !== "DETAIL")
      ?? scenario.stages
        .map((stage) => capabilities.find((candidate) => candidate.id === stage.capabilityId))
        .find(Boolean);
    const actionOverlays = groupOverlayStages(
      actionId,
      screenId,
      scenario,
      capability?.risk === "DESTRUCTIVE" || capability?.type === "DELETE"
    );
    overlays.push(...actionOverlays);
    actions.push({
      id: actionId,
      scenarioId: scenario.id,
      screenId,
      label: actionLabel(scenario, capability),
      tone: actionTone(capability),
      icon: actionIcon(capability),
      stageIds: scenario.stages.map((stage) => stage.id),
      overlayIds: actionOverlays.map((overlay) => overlay.id),
    });
  }

  const resources = Array.from(new Set(capabilities.map((capability) => capability.resourceName).filter(Boolean)));
  const screens: ExperienceScreen[] = definition.screens.map((screen) => ({
    ...screen,
    resourceNames: [],
    capabilityIds: [],
    actionIds: actions.filter((action) => action.screenId === screen.id).map((action) => action.id),
  }));

  // 생성·수정·삭제가 서로 다른 화면으로 흩어지면 실제 제품의 한 리소스 액션처럼 보이지 않는다.
  // 화면에 매핑되지 않은 액션은 기본 화면의 퀵 액션으로 합쳐 한 페이지가 여러 기능을 소유하게 한다.
  const knownScreenIds = new Set(screens.map((screen) => screen.id));
  for (const action of actions) {
    if (!knownScreenIds.has(action.screenId)) action.screenId = screens[0].id;
  }
  for (const screen of screens) {
    screen.actionIds = actions.filter((action) => action.screenId === screen.id).map((action) => action.id);
    const scenarioIds = new Set(
      actions.filter((action) => action.screenId === screen.id).map((action) => action.scenarioId)
    );
    const stageCapabilityIds = available
      .filter((scenario) => scenarioIds.has(scenario.id))
      .flatMap((scenario) => scenario.stages.map((stage) => stage.capabilityId))
      .filter((id): id is string => Boolean(id));
    const stageResources = stageCapabilityIds
      .map((id) => capabilities.find((capability) => capability.id === id)?.resourceName)
      .filter((resource): resource is string => Boolean(resource));
    screen.capabilityIds = Array.from(new Set(stageCapabilityIds));
    screen.resourceNames = Array.from(new Set(stageResources));
  }
  // 조회-only API는 별도 action이 없을 수 있다. 같은 리소스를 가진 화면에 붙이고, 의미 관계를
  // 찾을 수 없는 경우에만 기본 탐색 화면이 데이터를 소유한다.
  for (const capability of capabilities) {
    if (screens.some((screen) => screen.capabilityIds.includes(capability.id))) continue;
    const owner = screens.find((screen) => screen.resourceNames.includes(capability.resourceName))
      ?? screens.find((screen) => ["HOME", "CATALOG", "COLLECTION", "FEED", "FILES"].includes(screen.kind))
      ?? screens[0];
    owner.capabilityIds.push(capability.id);
    if (capability.resourceName && !owner.resourceNames.includes(capability.resourceName)) {
      owner.resourceNames.push(capability.resourceName);
    }
  }
  // 리소스가 많더라도 화면 제목만 늘리지 않고, 의미적으로 연결된 목록/상세/변경 API를 같은
  // 페이지에 묶는다. 비어 있는 보조 화면에는 대표 리소스를 공유해 실제 내비게이션으로 유지한다.
  for (const [index, screen] of screens.entries()) {
    if (screen.resourceNames.length === 0 && resources.length > 0) {
      screen.resourceNames.push(resources[index % resources.length]);
    }
  }

  return {
    id: `product-${definition.archetype.toLowerCase()}`,
    productName: definition.productName,
    archetype: definition.archetype,
    screens,
    actions,
    overlays,
    defaultScreenId: screens[0]?.id ?? "home",
  };
}

export function validateProductExperience(
  graph: ProductExperienceGraph,
  scenarios: PreviewCompiledScenario[]
): string[] {
  const errors: string[] = [];
  const screenIds = new Set(graph.screens.map((screen) => screen.id));
  const actionIds = new Set<string>();
  const overlayIds = new Set<string>();
  for (const action of graph.actions) {
    if (actionIds.has(action.id)) errors.push(`중복된 action id: ${action.id}`);
    actionIds.add(action.id);
    if (!screenIds.has(action.screenId)) errors.push(`존재하지 않는 action screen: ${action.screenId}`);
  }
  for (const overlay of graph.overlays) {
    if (overlayIds.has(overlay.id)) errors.push(`중복된 overlay id: ${overlay.id}`);
    overlayIds.add(overlay.id);
    if (!actionIds.has(overlay.actionId)) errors.push(`존재하지 않는 overlay action: ${overlay.actionId}`);
  }
  const reachableScenarioIds = new Set(graph.actions.map((action) => action.scenarioId));
  for (const scenario of scenarios.filter((candidate) => candidate.status !== "UNSUPPORTED")) {
    if (!reachableScenarioIds.has(scenario.id)) errors.push(`UI에서 실행할 수 없는 scenario: ${scenario.id}`);
  }
  return errors;
}
