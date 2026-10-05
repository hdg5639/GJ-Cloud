import type {
  PreviewCompiledScenario,
  PreviewCompiledScenarioStage,
  PreviewPagePlan,
} from "@/lib/types";
import type { PreviewCapability } from "../types";
import { buildScenarioExecutionPath } from "./runtime";

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
  submitLabel: string;
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
};

const DEFINITIONS: ArchetypeDefinition[] = [
  { archetype: "CALENDAR", keywords: ["calendar", "schedule", "일정", "캘린더", "event", "이벤트"] },
  { archetype: "COMMERCE", keywords: ["product", "cart", "order", "shop", "commerce", "상품", "장바구니", "주문", "결제", "market"] },
  { archetype: "COMMUNITY", keywords: ["community", "post", "comment", "follow", "social", "feed", "커뮤니티", "게시글", "댓글", "피드"] },
  { archetype: "CONTENT", keywords: ["article", "content", "publish", "editor", "document", "blog", "글", "콘텐츠", "문서", "발행", "작가"] },
  { archetype: "BOOKING", keywords: ["reservation", "booking", "seat", "room", "appointment", "예약", "좌석", "숙소", "방문"] },
  { archetype: "MESSAGING", keywords: ["message", "chat", "conversation", "inbox", "ticket", "support", "메시지", "채팅", "대화", "문의"] },
  { archetype: "FILES", keywords: ["file", "folder", "asset", "upload", "download", "파일", "폴더", "업로드", "다운로드", "미디어"] },
  { archetype: "LEARNING", keywords: ["course", "lesson", "student", "learn", "class", "교육", "강의", "수업", "학습"] },
  { archetype: "ADMIN", keywords: ["admin", "administrator", "backoffice", "governance", "운영자", "관리자", "백오피스"] },
];

const FALLBACK_DEFINITION: ArchetypeDefinition = { archetype: "WORKSPACE", keywords: [] };

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

function stageLabel(stage: PreviewCompiledScenarioStage | undefined, fallback: string): string {
  return stage?.actionLabel?.trim() || fallback;
}

// Interaction boundaries follow the validated graph, including plans stored in a different array order.
// One confirmation can own consecutive APIs for the same action; it never consumes a later input form.
// Destructive reviews remain separate approval boundaries.
export function groupOverlayStages(
  actionId: string, screenId: string, scenario: PreviewCompiledScenario, destructive: boolean
): ExperienceOverlay[] {
  const ordered = buildScenarioExecutionPath(scenario);
  if (ordered.error) return [];
  const groups: Array<{ kind: ExperienceOverlayKind; stages: PreviewCompiledScenarioStage[] }> = [];
  let pending: PreviewCompiledScenarioStage[] = [];
  for (const stage of ordered.stages) {
    let kind: ExperienceOverlayKind | null = stage.role === "REVIEW"
      ? (destructive ? "DANGER_CONFIRM" : "REVIEW_MODAL")
      : ["INSPECT", "COMPARE"].includes(stage.role) ? "DETAIL_DRAWER"
      : ["PREPARE", "CONFIGURE", "SELECT_CONTEXT", "AUTHENTICATE", "RECOVER"].includes(stage.role)
        || (stage.role === "ENTRY" && (stage.outputs?.length ?? 0) > 0) ? "FORM_MODAL" : null;
    const previous = groups.at(-1);
    if (kind === "FORM_MODAL" && previous?.kind === "DETAIL_DRAWER") kind = "DETAIL_DRAWER";
    if (kind && (previous?.kind !== kind || (destructive && stage.role === "REVIEW"))) {
      groups.push({ kind, stages: [...pending, stage] }); pending = [];
    } else if (previous) previous.stages.push(stage);
    else pending.push(stage);
  }
  if (groups.length === 0) groups.push({ kind: "DETAIL_DRAWER", stages: pending });
  const overlays = groups.map((group, index) => {
    const mutation = group.stages.filter(stage => stage.role === "COMMIT" || stage.role === "AUTHENTICATE").at(-1);
    const display = group.stages.find(stage => stage.role === "PREPARE" || stage.role === "REVIEW" || stage.role === "INSPECT") ?? group.stages[0];
    return {
      id: `${actionId}-overlay-${index + 1}`, actionId, screenId, kind: group.kind,
      title: stageLabel(display, display?.intent || scenario.name),
      submitLabel: stageLabel(mutation, group.kind === "REVIEW_MODAL" ? "확인하고 저장" : "다음으로"),
      stageIds: group.stages.map(stage => stage.id),
    };
  });
  overlays.push({ id: `${actionId}-result`, actionId, screenId, kind: "RESULT_TOAST", title: "완료", submitLabel: "목록으로", stageIds: [] });
  return overlays;
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
    const anchor = related.find(capability => capability.type === "LIST") ?? related[0];
    const plan = pagePlans.find(plan => anchor?.type === "LIST" && plan.capabilityIds.includes(anchor.id)) ?? pagePlans.map((plan) => ({
      plan, score: plan.capabilityIds.filter((id) => capabilityIds.includes(id)).length,
    })).sort((left, right) => right.score - left.score).find((candidate) => candidate.score > 0)?.plan;
    const screenId = plan ? `page-${plan.id}` : `resource-${anchor?.resourceName ?? scenario.id}`;
    const resourceNames = Array.from(new Set(related.map((capability) => capability.resourceName)));
    const screenDefinition = selectDefinition([scenario], related);
    const kind: ExperienceScreenKind = screenDefinition.archetype === "COMMERCE" || screenDefinition.archetype === "BOOKING" ? "CATALOG"
      : screenDefinition.archetype === "COMMUNITY" || screenDefinition.archetype === "CONTENT" ? "FEED" : "COLLECTION";
    const runnable = scenario.status !== "UNSUPPORTED" && scenario.stages.length > 0;
    const actionId = `action-${scenario.id}`;
    const existing = screens.find(screen => screen.id === screenId);
    if (existing) {
      existing.capabilityIds = Array.from(new Set([...existing.capabilityIds, ...capabilityIds]));
      existing.resourceNames = Array.from(new Set([...existing.resourceNames, ...resourceNames]));
      if (runnable) existing.actionIds.push(actionId);
    } else screens.push({
      id: screenId, label: plan?.title || humanize(anchor?.resourceName ?? scenario.name),
      title: plan?.title || humanize(anchor?.resourceName ?? scenario.name),
      description: "", kind, resourceNames, capabilityIds, actionIds: runnable ? [actionId] : [],
    });
    if (!runnable) continue;
    const capability = related.find((candidate) => candidate.risk !== "SAFE") ?? related[0];
    const actionOverlays = groupOverlayStages(actionId, screenId, scenario,
      related.some((candidate) => candidate.risk === "DESTRUCTIVE"));
    overlays.push(...actionOverlays);
    actions.push({
      id: actionId, scenarioId: scenario.id, screenId,
      label: buildScenarioExecutionPath(scenario).stages.find(stage => stage.role === "COMMIT")?.actionLabel
        || (capability?.type === "CREATE" ? `${humanize(capability.resourceName)} 추가`
          : capability?.type === "DELETE" ? "삭제" : capability?.type === "UPDATE" ? "수정" : scenario.name),
      tone: actionTone(capability), icon: actionIcon(capability),
      stageIds: scenario.stages.map((stage) => stage.id),
      overlayIds: actionOverlays.map((overlay) => overlay.id),
    });
  }
  // Unassigned read capabilities get real resource screens, never fabricated profile/store menus.
  for (const capability of capabilities.filter((candidate) => candidate.type === "LIST")) {
    if (screens.some((screen) => screen.capabilityIds.includes(capability.id))) continue;
    const related = capabilities.filter((candidate) => candidate.resourceName === capability.resourceName);
    const existing = screens.find(screen => screen.resourceNames.includes(capability.resourceName));
    if (existing) {
      existing.capabilityIds = Array.from(new Set([...existing.capabilityIds, ...related.map(cap => cap.id)]));
      continue;
    }
    screens.push({
      id: `resource-${capability.id}`, label: humanize(capability.resourceName),
      title: `${humanize(capability.resourceName)} 조회`, description: "실제 서버 응답을 조회하고 항목을 선택합니다.",
      kind: "COLLECTION", resourceNames: [capability.resourceName],
      capabilityIds: related.map((candidate) => candidate.id), actionIds: [],
    });
  }
  return {
    id: "user-flow-preview", productName: "서비스 프리뷰",
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
