import type { PreviewScenarioStageRole } from "@/lib/types";
import type { BlueprintPartKind } from "../blueprints/core";

export type ScenarioStageRendererKind =
  | "ENTRY"
  | "INPUT"
  | "SELECTION"
  | "READ"
  | "REVIEW"
  | "OPERATION"
  | "PROGRESS"
  | "RESULT";

export interface ScenarioStageRendererContract {
  kind: ScenarioStageRendererKind;
  roles: PreviewScenarioStageRole[];
  compatibleBlueprintKinds: BlueprintPartKind[];
  ownsExecution: boolean;
  acceptsLocalInput: boolean;
}

export const SCENARIO_STAGE_RENDERER_CONTRACTS: ScenarioStageRendererContract[] = [
  {
    kind: "ENTRY",
    roles: ["ENTRY", "AUTHENTICATE"],
    compatibleBlueprintKinds: ["FORM", "LAYOUT"],
    ownsExecution: true,
    acceptsLocalInput: true,
  },
  {
    kind: "SELECTION",
    roles: ["SELECT_CONTEXT", "DISCOVER", "SELECT", "ACCUMULATE"],
    compatibleBlueprintKinds: ["COLLECTION", "NAVIGATION"],
    ownsExecution: true,
    acceptsLocalInput: true,
  },
  {
    kind: "READ",
    roles: ["INSPECT", "COMPARE"],
    compatibleBlueprintKinds: ["DETAIL", "DASHBOARD"],
    ownsExecution: true,
    acceptsLocalInput: false,
  },
  {
    kind: "INPUT",
    roles: ["CONFIGURE", "PREPARE"],
    compatibleBlueprintKinds: ["FORM", "MODAL"],
    ownsExecution: false,
    acceptsLocalInput: true,
  },
  {
    kind: "REVIEW",
    roles: ["REVIEW"],
    compatibleBlueprintKinds: ["MODAL", "WORKFLOW", "DETAIL"],
    ownsExecution: false,
    acceptsLocalInput: true,
  },
  {
    kind: "OPERATION",
    roles: ["COMMIT"],
    compatibleBlueprintKinds: ["MODAL", "WORKFLOW", "ACTION"],
    ownsExecution: true,
    acceptsLocalInput: false,
  },
  {
    kind: "PROGRESS",
    roles: ["WAIT", "TRACK", "CONTINUE"],
    compatibleBlueprintKinds: ["WORKFLOW", "FEEDBACK", "DASHBOARD"],
    ownsExecution: true,
    acceptsLocalInput: false,
  },
  {
    kind: "RESULT",
    roles: ["VERIFY", "RECOVER", "COMPLETE"],
    compatibleBlueprintKinds: ["FEEDBACK", "DETAIL"],
    ownsExecution: true,
    acceptsLocalInput: false,
  },
];

const CONTRACT_BY_ROLE = new Map(
  SCENARIO_STAGE_RENDERER_CONTRACTS.flatMap((contract) =>
    contract.roles.map((role) => [role, contract] as const)
  )
);

export function stageRendererContract(role: PreviewScenarioStageRole): ScenarioStageRendererContract {
  const contract = CONTRACT_BY_ROLE.get(role);
  if (!contract) throw new Error(`지원하지 않는 Scenario stage role: ${role}`);
  return contract;
}
