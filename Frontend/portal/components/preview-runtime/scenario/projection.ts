import type {
  PreviewCompiledScenario,
  PreviewCompiledScenarioStage,
  PreviewScenarioStageRole,
} from "@/lib/types";
import type { Purpose } from "../types";
import type { BlueprintPartDescriptor, BlueprintPartKind } from "../blueprints/core";
import manifest from "../blueprints/manifests/component-manifest.json";
import {
  PRODUCT_BLUEPRINT_RECIPES,
  type ProductBlueprintRecipe,
} from "../blueprints/recipes";
import {
  stageRendererContract,
  type ScenarioStageRendererKind,
} from "./stageRendererContracts";

export type ScenarioProjectionMode = "GUIDED" | "COMPACT";
export type ScenarioProjectionLevel = "PAGE" | "SECTION" | "MODAL" | "DRAWER" | "FEEDBACK" | "INSPECTOR";
export type ScenarioContentTag =
  | "IDENTITY"
  | "COLLECTION"
  | "SELECTION"
  | "DETAIL"
  | "FORM"
  | "CONFIRMATION"
  | "MUTATION"
  | "PROGRESS"
  | "RESULT";
export type ScenarioInteractionTag =
  | "ENTER"
  | "BROWSE"
  | "CHOOSE"
  | "READ"
  | "INPUT"
  | "REVIEW"
  | "EXECUTE"
  | "WAIT"
  | "VERIFY";
export type ScenarioPresentationTag =
  | "SHELL"
  | "TABLE"
  | "CARDS"
  | "DETAIL"
  | "FORM"
  | "MODAL"
  | "PROGRESS"
  | "FEEDBACK";

export interface ScenarioUxTags {
  content: ScenarioContentTag;
  interaction: ScenarioInteractionTag;
  presentation: ScenarioPresentationTag;
}

export interface ScenarioStageProjection {
  stageId: string;
  rendererKind: ScenarioStageRendererKind;
  level: ScenarioProjectionLevel;
  tags: ScenarioUxTags;
  blueprintComponentId: string | null;
  blueprintLabel: string | null;
}

export interface ScenarioPageProjection {
  id: string;
  title: string;
  description: string;
  level: ScenarioProjectionLevel;
  stageIds: string[];
  previousPageId: string | null;
  nextPageId: string | null;
}

export interface ScenarioProjection {
  id: string;
  scenarioId: string;
  mode: ScenarioProjectionMode;
  label: string;
  description: string;
  recipeId: string | null;
  layoutComponentId: string | null;
  themeComponentId: string | null;
  pages: ScenarioPageProjection[];
  stages: Record<string, ScenarioStageProjection>;
}

type Boundary = {
  key: string;
  title: string;
  description: string;
  level: ScenarioProjectionLevel;
};

const DESCRIPTORS = manifest as BlueprintPartDescriptor[];
const GENERIC_RECIPE_TOKENS = new Set([
  "api", "service", "system", "manage", "management", "operation", "operations",
  "create", "update", "delete", "list", "get", "사용자", "서비스", "관리", "조회",
]);

const ROLE_UX: Record<PreviewScenarioStageRole, ScenarioUxTags> = {
  ENTRY: { content: "IDENTITY", interaction: "ENTER", presentation: "SHELL" },
  AUTHENTICATE: { content: "IDENTITY", interaction: "ENTER", presentation: "FORM" },
  SELECT_CONTEXT: { content: "SELECTION", interaction: "CHOOSE", presentation: "CARDS" },
  DISCOVER: { content: "COLLECTION", interaction: "BROWSE", presentation: "TABLE" },
  INSPECT: { content: "DETAIL", interaction: "READ", presentation: "DETAIL" },
  SELECT: { content: "SELECTION", interaction: "CHOOSE", presentation: "CARDS" },
  COMPARE: { content: "DETAIL", interaction: "READ", presentation: "TABLE" },
  ACCUMULATE: { content: "SELECTION", interaction: "CHOOSE", presentation: "CARDS" },
  CONFIGURE: { content: "FORM", interaction: "INPUT", presentation: "FORM" },
  PREPARE: { content: "FORM", interaction: "INPUT", presentation: "FORM" },
  REVIEW: { content: "CONFIRMATION", interaction: "REVIEW", presentation: "MODAL" },
  COMMIT: { content: "MUTATION", interaction: "EXECUTE", presentation: "MODAL" },
  WAIT: { content: "PROGRESS", interaction: "WAIT", presentation: "PROGRESS" },
  VERIFY: { content: "RESULT", interaction: "VERIFY", presentation: "FEEDBACK" },
  TRACK: { content: "PROGRESS", interaction: "WAIT", presentation: "PROGRESS" },
  RECOVER: { content: "RESULT", interaction: "INPUT", presentation: "FEEDBACK" },
  CONTINUE: { content: "PROGRESS", interaction: "ENTER", presentation: "PROGRESS" },
  COMPLETE: { content: "RESULT", interaction: "VERIFY", presentation: "FEEDBACK" },
};

function boundaryFor(stage: PreviewCompiledScenarioStage, purpose: Purpose | null): Boundary {
  switch (stage.role) {
    case "ENTRY":
    case "AUTHENTICATE":
      return { key: "entry", title: "시작 및 인증", description: "서비스 진입 조건과 인증을 준비합니다.", level: "PAGE" };
    case "SELECT_CONTEXT":
    case "DISCOVER":
    case "SELECT":
    case "ACCUMULATE":
      return { key: "discover", title: "대상 탐색", description: "작업할 항목을 조회하고 선택합니다.", level: "PAGE" };
    case "INSPECT":
    case "COMPARE":
      return { key: "inspect", title: "상세 확인", description: "선택한 대상의 현재 상태를 확인합니다.", level: "PAGE" };
    case "CONFIGURE":
    case "PREPARE":
      return { key: "prepare", title: "요청 설정", description: "실행에 필요한 값을 입력하고 구성합니다.", level: "PAGE" };
    case "REVIEW":
    case "COMMIT":
      return {
        key: "commit",
        title: "검토 및 실행",
        description: "변경 내용과 위험도를 확인한 뒤 요청을 실행합니다.",
        level: purpose === "PRODUCT_LIKE" ? "PAGE" : "MODAL",
      };
    case "WAIT":
    case "TRACK":
    case "CONTINUE":
      return { key: "track", title: "진행 상태", description: "작업 완료까지 상태 변화를 추적합니다.", level: "PAGE" };
    case "VERIFY":
    case "RECOVER":
    case "COMPLETE":
      return { key: "result", title: "결과 확인", description: "응답과 최종 상태를 검증합니다.", level: "FEEDBACK" };
    default:
      return { key: "work", title: "작업", description: stage.intent, level: "SECTION" };
  }
}

function preferredKinds(stage: PreviewCompiledScenarioStage): BlueprintPartKind[] {
  switch (stage.role) {
    case "DISCOVER":
    case "SELECT":
    case "SELECT_CONTEXT":
    case "ACCUMULATE":
      return ["COLLECTION"];
    case "INSPECT":
    case "COMPARE":
      return ["DETAIL"];
    case "CONFIGURE":
    case "PREPARE":
    case "AUTHENTICATE":
      return ["FORM"];
    case "REVIEW":
      return ["MODAL", "WORKFLOW"];
    case "COMMIT":
      return stage.risk === "DESTRUCTIVE" ? ["MODAL", "WORKFLOW"] : ["WORKFLOW", "MODAL"];
    case "WAIT":
    case "TRACK":
    case "CONTINUE":
      return ["WORKFLOW", "FEEDBACK"];
    case "VERIFY":
    case "RECOVER":
    case "COMPLETE":
      return ["FEEDBACK", "DETAIL"];
    default:
      return [];
  }
}

function tokensFor(scenario: PreviewCompiledScenario, stage: PreviewCompiledScenarioStage): string[] {
  return [
    scenario.name,
    scenario.goal,
    scenario.actor,
    stage.intent,
    stage.operationId ?? "",
    ROLE_UX[stage.role].content,
    ROLE_UX[stage.role].interaction,
    ROLE_UX[stage.role].presentation,
  ]
    .join(" ")
    .toLowerCase()
    .split(/[^a-z0-9가-힣]+/)
    .filter((token) => token.length > 1);
}

export function selectScenarioBlueprint(
  scenario: PreviewCompiledScenario,
  stage: PreviewCompiledScenarioStage,
  purpose: Purpose | null,
  recipe: ProductBlueprintRecipe | null = selectScenarioRecipe(scenario)
): BlueprintPartDescriptor | null {
  const kinds = preferredKinds(stage);
  if (kinds.length === 0) return null;
  const recipeComponents: Partial<Record<BlueprintPartKind, string>> = recipe ? {
    COLLECTION: recipe.collectionId,
    DETAIL: recipe.detailId,
    WORKFLOW: recipe.workflowId,
  } : {};
  const recipeComponentId = recipeComponents[kinds[0]];
  const recipeDescriptor = recipeComponentId
    ? DESCRIPTORS.find((descriptor) =>
      descriptor.componentId === recipeComponentId && kinds.includes(descriptor.kind)
    )
    : undefined;
  if (recipeDescriptor) return recipeDescriptor;
  const tokens = tokensFor(scenario, stage);
  const candidates = DESCRIPTORS.filter((descriptor) =>
    descriptor.autoSelectable && kinds.includes(descriptor.kind)
  );
  return candidates
    .map((descriptor) => {
      const searchable = [descriptor.componentId, descriptor.family, descriptor.category, ...descriptor.tags]
        .join(" ")
        .toLowerCase();
      const semanticScore = tokens.reduce(
        (score, token) => score + (searchable.includes(token) ? 3 : 0),
        0
      );
      const purposeScore = purpose && descriptor.preferredPurposes.includes(purpose) ? 5 : 0;
      const kindScore = Math.max(0, 4 - kinds.indexOf(descriptor.kind));
      const riskScore = stage.risk === "DESTRUCTIVE" && descriptor.tags.includes("danger") ? 8 : 0;
      return { descriptor, score: semanticScore + purposeScore + kindScore + riskScore };
    })
    .sort((left, right) =>
      right.score - left.score || left.descriptor.componentId.localeCompare(right.descriptor.componentId)
    )[0]?.descriptor ?? null;
}

export function selectScenarioRecipe(scenario: PreviewCompiledScenario): ProductBlueprintRecipe | null {
  const tokens = [scenario.name, scenario.goal, scenario.actor]
    .join(" ")
    .toLowerCase()
    .split(/[^a-z0-9가-힣]+/)
    .filter((token) => token.length > 2 && !GENERIC_RECIPE_TOKENS.has(token));
  const ranked = PRODUCT_BLUEPRINT_RECIPES.map((recipe) => {
    const searchable = [
      recipe.id,
      recipe.name,
      recipe.category,
      recipe.layoutId,
      recipe.dashboardId,
      recipe.collectionId,
      recipe.detailId,
      recipe.workflowId,
      recipe.themeId,
    ].join(" ").toLowerCase();
    const score = tokens.reduce((total, token) => total + (searchable.includes(token) ? 1 : 0), 0);
    return { recipe, score };
  }).sort((left, right) => right.score - left.score || left.recipe.id.localeCompare(right.recipe.id));
  return ranked[0]?.score > 0 ? ranked[0].recipe : null;
}

function guidedProjection(
  scenario: PreviewCompiledScenario,
  purpose: Purpose | null,
  stageProjections: Record<string, ScenarioStageProjection>,
  recipe: ProductBlueprintRecipe | null
): ScenarioProjection {
  const groups: Array<{ boundary: Boundary; stageIds: string[] }> = [];
  for (const stage of scenario.stages) {
    const boundary = boundaryFor(stage, purpose);
    const previous = groups.at(-1);
    if (previous?.boundary.key === boundary.key) previous.stageIds.push(stage.id);
    else groups.push({ boundary, stageIds: [stage.id] });
  }
  const rawPages = groups.map((group, index) => ({
    id: `${scenario.id}-guided-${group.boundary.key}-${index + 1}`,
    title: group.boundary.title,
    description: group.boundary.description,
    level: group.boundary.level,
    stageIds: group.stageIds,
  }));
  const pages = rawPages.map((page, index): ScenarioPageProjection => ({
    ...page,
    previousPageId: rawPages[index - 1]?.id ?? null,
    nextPageId: rawPages[index + 1]?.id ?? null,
  }));
  return {
    id: `${scenario.id}-guided`,
    scenarioId: scenario.id,
    mode: "GUIDED",
    label: "안내형",
    description: "업무 흐름에 맞춰 탐색·설정·검토·결과를 화면 단위로 나눕니다.",
    recipeId: recipe?.id ?? null,
    layoutComponentId: recipe?.layoutId ?? null,
    themeComponentId: recipe?.themeId ?? null,
    pages,
    stages: stageProjections,
  };
}

function compactProjection(
  scenario: PreviewCompiledScenario,
  stageProjections: Record<string, ScenarioStageProjection>,
  recipe: ProductBlueprintRecipe | null
): ScenarioProjection {
  return {
    id: `${scenario.id}-compact`,
    scenarioId: scenario.id,
    mode: "COMPACT",
    label: "압축형",
    description: "모든 단계를 한 작업공간에서 빠르게 실행하고 검사합니다.",
    recipeId: recipe?.id ?? null,
    layoutComponentId: recipe?.layoutId ?? null,
    themeComponentId: recipe?.themeId ?? null,
    pages: [{
      id: `${scenario.id}-compact-workbench`,
      title: "통합 작업공간",
      description: "시나리오 의미와 실행 순서를 유지한 단일 화면입니다.",
      level: "INSPECTOR",
      stageIds: scenario.stages.map((stage) => stage.id),
      previousPageId: null,
      nextPageId: null,
    }],
    stages: Object.fromEntries(Object.entries(stageProjections).map(([stageId, projection]) => [
      stageId,
      { ...projection, level: "SECTION" as const },
    ])),
  };
}

export function buildScenarioProjections(
  scenario: PreviewCompiledScenario,
  purpose: Purpose | null
): ScenarioProjection[] {
  const recipe = selectScenarioRecipe(scenario);
  const stageProjections = Object.fromEntries(scenario.stages.map((stage) => {
    const boundary = boundaryFor(stage, purpose);
    const blueprint = selectScenarioBlueprint(scenario, stage, purpose, recipe);
    return [stage.id, {
      stageId: stage.id,
      rendererKind: stageRendererContract(stage.role).kind,
      level: boundary.level,
      tags: ROLE_UX[stage.role],
      blueprintComponentId: blueprint?.componentId ?? null,
      blueprintLabel: blueprint?.label ?? null,
    } satisfies ScenarioStageProjection];
  }));
  return [
    guidedProjection(scenario, purpose, stageProjections, recipe),
    compactProjection(scenario, stageProjections, recipe),
  ];
}

export function validateScenarioProjection(
  scenario: PreviewCompiledScenario,
  projection: ScenarioProjection
): string[] {
  const errors: string[] = [];
  const scenarioIds = new Set(scenario.stages.map((stage) => stage.id));
  const projectedIds = projection.pages.flatMap((page) => page.stageIds);
  const occurrences = new Map<string, number>();
  for (const id of projectedIds) occurrences.set(id, (occurrences.get(id) ?? 0) + 1);
  for (const id of scenarioIds) {
    if (!projection.stages[id]) errors.push(`stage projection 누락: ${id}`);
    if ((occurrences.get(id) ?? 0) !== 1) errors.push(`stage는 화면 경계에 정확히 한 번 있어야 함: ${id}`);
  }
  for (const stage of scenario.stages) {
    const stageProjection = projection.stages[stage.id];
    if (!stageProjection) continue;
    const contract = stageRendererContract(stage.role);
    if (stageProjection.rendererKind !== contract.kind) {
      errors.push(`stage renderer 계약 불일치: ${stage.id}`);
    }
    if (stageProjection.blueprintComponentId) {
      const descriptor = DESCRIPTORS.find((candidate) =>
        candidate.componentId === stageProjection.blueprintComponentId
      );
      if (!descriptor) errors.push(`등록되지 않은 blueprint: ${stageProjection.blueprintComponentId}`);
      else if (!contract.compatibleBlueprintKinds.includes(descriptor.kind)) {
        errors.push(`호환되지 않는 blueprint renderer: ${stage.id}/${descriptor.componentId}`);
      }
    }
  }
  for (const id of projectedIds) {
    if (!scenarioIds.has(id)) errors.push(`알 수 없는 stage projection: ${id}`);
  }
  return errors;
}
