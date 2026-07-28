import type { PreviewCapability } from "../types";

export type JourneyMode = "CREATE" | "UPDATE" | "DELETE" | "COMMAND";
export type JourneyStepType = "COLLECT" | "REVIEW" | "CONFIRM" | "EXECUTE" | "SUCCESS";

export interface JourneyStep {
  id: string;
  type: JourneyStepType;
  title: string;
  description?: string;
  componentId?: string;
  fields?: string[];
  nextStepId: string | null;
}

export interface JourneyBlueprint {
  id: string;
  pageId: string;
  actionId: string;
  mode: JourneyMode;
  title: string;
  entryStepId: string;
  steps: JourneyStep[];
}

export interface JourneySession {
  id: string;
  blueprint: JourneyBlueprint;
  capability: PreviewCapability;
  targetId: string;
  initialValues: Record<string, unknown>;
}

export interface JourneyExecutionResult {
  message?: string;
  response?: unknown;
}

export interface JourneyValidationResult {
  valid: boolean;
  errors: string[];
}
