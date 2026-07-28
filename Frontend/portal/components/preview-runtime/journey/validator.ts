import type { JourneyBlueprint, JourneyStep, JourneyValidationResult } from "./types";

const MAX_STEPS = 12;

export function validateJourney(blueprint: JourneyBlueprint): JourneyValidationResult {
  const errors: string[] = [];
  if (!blueprint.id.trim()) errors.push("journey id가 비어 있습니다.");
  if (!blueprint.entryStepId.trim()) errors.push("entryStepId가 비어 있습니다.");
  if (blueprint.steps.length === 0) errors.push("step이 하나 이상 필요합니다.");
  if (blueprint.steps.length > MAX_STEPS) errors.push(`step은 최대 ${MAX_STEPS}개까지 허용됩니다.`);

  const byId = new Map<string, JourneyBlueprint["steps"][number]>();
  for (const step of blueprint.steps) {
    if (!step.id.trim()) {
      errors.push("빈 step id가 있습니다.");
      continue;
    }
    if (byId.has(step.id)) errors.push(`중복 step id: ${step.id}`);
    byId.set(step.id, step);
    if (step.nextStepId && step.nextStepId === step.id) errors.push(`자기 자신을 가리키는 step: ${step.id}`);
    if (["COLLECT", "REVIEW", "CONFIRM"].includes(step.type) && !step.componentId && !step.fields?.length) {
      errors.push(`UI 정의가 없는 step: ${step.id}`);
    }
  }

  if (!byId.has(blueprint.entryStepId)) errors.push(`entry step을 찾을 수 없습니다: ${blueprint.entryStepId}`);
  for (const step of blueprint.steps) {
    if (step.nextStepId && !byId.has(step.nextStepId)) {
      errors.push(`존재하지 않는 다음 step: ${step.id} -> ${step.nextStepId}`);
    }
  }

  const executionSteps = blueprint.steps.filter((step) => step.type === "EXECUTE");
  const successSteps = blueprint.steps.filter((step) => step.type === "SUCCESS");
  if (executionSteps.length !== 1) errors.push("EXECUTE step은 정확히 하나여야 합니다.");
  if (successSteps.length !== 1) errors.push("SUCCESS step은 정확히 하나여야 합니다.");

  const visited = new Set<string>();
  let cursor: string | null = blueprint.entryStepId;
  let executionSeen = false;
  while (cursor && byId.has(cursor) && !visited.has(cursor)) {
    visited.add(cursor);
    const step: JourneyStep = byId.get(cursor)!;
    if (step.type === "EXECUTE") executionSeen = true;
    if (step.type === "SUCCESS" && !executionSeen) errors.push("SUCCESS보다 EXECUTE가 먼저 실행되어야 합니다.");
    cursor = step.nextStepId;
  }
  if (cursor && visited.has(cursor)) errors.push(`forward cycle이 감지되었습니다: ${cursor}`);
  for (const step of blueprint.steps) {
    if (!visited.has(step.id)) errors.push(`도달할 수 없는 step: ${step.id}`);
  }
  if (!executionSteps.every((step) => visited.has(step.id))) errors.push("EXECUTE step에 도달할 수 없습니다.");
  if (!successSteps.every((step) => visited.has(step.id))) errors.push("SUCCESS step에 도달할 수 없습니다.");

  return { valid: errors.length === 0, errors };
}

export function assertValidJourney(blueprint: JourneyBlueprint): JourneyBlueprint {
  const result = validateJourney(blueprint);
  if (!result.valid) {
    throw new Error(`잘못된 JourneyBlueprint(${blueprint.id}): ${result.errors.join(" | ")}`);
  }
  return blueprint;
}
