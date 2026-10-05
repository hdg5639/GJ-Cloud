import type {
  PreviewCompiledScenario,
  PreviewCompiledScenarioStage,
  PreviewScenarioStageRole,
  PreviewPagePlan,
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
  ENTRY: "FORM_MODAL",
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

function humanize(value: string): string {
  return value
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replaceAll("_", " ")
    .replaceAll("-", " ")
    .trim();
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
  capabilities: PreviewCapability[],
  pagePlans: PreviewPagePlan[] = []
): ProductExperienceGraph {
  const definition = selectDefinition(scenarios, capabilities);
  const actions: ExperienceAction[] = [];
  const overlays: ExperienceOverlay[] = [];
  const screens: ExperienceScreen[] = [];
  // Navigation follows inferred user goals. Archetypes only suggest the data presentation/theme.
  for (const scenario of scenarios) {
    const capabilityIds = Array.from(new Set(scenario.stages.flatMap((stage) =>
      stage.capabilityId && capabilities.some((capability) => capability.id === stage.capabilityId)
        ? [stage.capabilityId] : []
    )));
    const related = capabilities.filter((capability) => capabilityIds.includes(capability.id));
    const plan = pagePlans.map((plan) => ({
      plan, score: plan.capabilityIds.filter((id) => capabilityIds.includes(id)).length,
    })).sort((left, right) => right.score - left.score).find((candidate) => candidate.score > 0)?.plan;
    const screenId = `flow-${scenario.id}`;
    const resourceNames = Array.from(new Set(related.map((capability) => capability.resourceName)));
    const kind: ExperienceScreenKind = definition.archetype === "COMMERCE"
      || definition.archetype === "BOOKING" ? "CATALOG" : "COLLECTION";
    const runnable = scenario.status !== "UNSUPPORTED" && scenario.stages.length > 0;
    const actionId = `action-${scenario.id}`;
    screens.push({
      id: screenId, label: scenario.name, title: plan?.title || scenario.name,
      description: scenario.goal, kind, resourceNames, capabilityIds,
      actionIds: runnable ? [actionId] : [],
    });
    if (!runnable) continue;
    const capability = related.find((candidate) => candidate.risk !== "SAFE") ?? related[0];
    const actionOverlays = groupOverlayStages(actionId, screenId, scenario,
      related.some((candidate) => candidate.risk === "DESTRUCTIVE"));
    overlays.push(...actionOverlays);
    actions.push({
      id: actionId, scenarioId: scenario.id, screenId,
      label: "흐름 테스트", tone: actionTone(capability), icon: actionIcon(capability),
      stageIds: scenario.stages.map((stage) => stage.id),
      overlayIds: actionOverlays.map((overlay) => overlay.id),
    });
  }
  // Unassigned read capabilities get real resource screens, never fabricated profile/store menus.
  for (const capability of capabilities.filter((candidate) => candidate.type === "LIST")) {
    if (screens.some((screen) => screen.capabilityIds.includes(capability.id))) continue;
    const related = capabilities.filter((candidate) => candidate.resourceName === capability.resourceName);
    screens.push({
      id: `resource-${capability.id}`, label: humanize(capability.resourceName),
      title: `${humanize(capability.resourceName)} 조회`, description: "실제 서버 응답을 조회하고 항목을 선택합니다.",
      kind: "COLLECTION", resourceNames: [capability.resourceName],
      capabilityIds: related.map((candidate) => candidate.id), actionIds: [],
    });
  }
  return {
    id: "user-flow-preview", productName: "사용자 흐름 프리뷰",
    archetype: definition.archetype, screens, actions, overlays,
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
