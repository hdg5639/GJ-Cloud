import type { PreviewCapability } from "../types";
import { isOverlayPart } from "../blueprints/adapters";
import { assertValidJourney } from "./validator";
import type { JourneyBlueprint, JourneyMode, JourneyStep } from "./types";

const SPECIALIZED_MODAL_RULES: Array<[RegExp, string]> = [
  [/(acknowledge|resolve).*(alert|alarm)|(alert|alarm).*(acknowledge|resolve)/, "acknowledge-alert-modal"],
  [/(escalat).*(incident)|(incident).*(escalat)/, "escalate-incident-modal"],
  [/(merge).*(ticket)|(ticket).*(merge)/, "merge-tickets-modal"],
  [/(reply|respond).*(ticket)|(ticket).*(reply|respond)/, "send-reply-modal"],
  [/(refund).*(payment|order)|(payment|order).*(refund)/, "issue-refund-modal"],
  [/(capture).*(payment)|(payment).*(capture)/, "capture-payment-modal"],
  [/(adjust).*(inventory|stock)|(inventory|stock).*(adjust)/, "adjust-inventory-modal"],
  [/(transfer).*(inventory|stock)|(inventory|stock).*(transfer)/, "transfer-stock-modal"],
  [/(reassign).*(shipment)|(shipment).*(reassign)/, "reassign-shipment-modal"],
  [/(exception).*(delivery)|(delivery).*(exception)/, "delivery-exception-modal"],
  [/(reschedul).*(booking|reservation)|(booking|reservation).*(reschedul)/, "reschedule-booking-modal"],
  [/(assign).*(seat)|(seat).*(assign)/, "seat-assignment-modal"],
  [/(enroll).*(learner|student)|(learner|student).*(enroll)/, "enroll-learner-modal"],
  [/(grade).*(submission|student)|(submission|student).*(grade)/, "grade-submission-modal"],
  [/(time.?off|leave).*(request|approve)|(request|approve).*(time.?off|leave)/, "time-off-request-modal"],
  [/(compensation|salary).*(change|review)|(change|review).*(compensation|salary)/, "compensation-change-modal"],
  [/(rotate).*(api.?key|secret)|(api.?key|secret).*(rotate)/, "rotate-api-key-modal"],
  [/(promote).*(deployment|release)|(deployment|release).*(promote)/, "promote-deployment-modal"],
  [/(evaluat).*(model)|(model).*(evaluat)/, "model-evaluation-modal"],
  [/(test).*(prompt)|(prompt).*(test)/, "prompt-test-modal"],
  [/(command).*(device)|(device).*(command)/, "device-command-modal"],
  [/(firmware).*(update|deploy)|(update|deploy).*(firmware)/, "firmware-update-modal"],
  [/(inquiry).*(property)|(property).*(inquiry)/, "property-inquiry-modal"],
  [/(renew).*(lease)|(lease).*(renew)/, "lease-renewal-modal"],
  [/(moderate|review).*(content)|(content).*(moderate|review)/, "moderate-content-modal"],
  [/(payout).*(vendor)|(vendor).*(payout)/, "vendor-payout-modal"],
  [/(change).*(trip|travel)|(trip|travel).*(change)/, "trip-change-modal"],
  [/(legal).*(hold)|(hold).*(legal)/, "legal-hold-modal"],
  [/(publish).*(asset|content)|(asset|content).*(publish)/, "publish-asset-modal"],
  [/(merge).*(knowledge|article)|(knowledge|article).*(merge)/, "knowledge-merge-modal"],
  [/(assign).*(owner)|(owner).*(assign)/, "assign-owner-modal"],
  [/(change).*(status)|(status).*(change)/, "change-status-modal"],
  [/(schedule)/, "schedule-action-modal"],
  [/(duplicate|clone|copy)/, "duplicate-resource-modal"],
  [/(import)/, "import-data-modal"],
  [/(export)/, "export-data-modal"],
];

function normalizedOperation(capability: PreviewCapability): string {
  return [
    capability.resourceName,
    capability.action,
    capability.operationId,
    capability.path,
  ].filter(Boolean).join(" ").toLowerCase().replaceAll("_", " ").replaceAll("-", " ");
}

function specializedModal(capability: PreviewCapability): string | undefined {
  const operation = normalizedOperation(capability);
  return SPECIALIZED_MODAL_RULES.find(([pattern]) => pattern.test(operation))?.[1];
}

function isHighRisk(capability: PreviewCapability): boolean {
  return ["DESTRUCTIVE", "IRREVERSIBLE", "EXTERNAL_SIDE_EFFECT"].includes(capability.risk)
    || ["EXPLICIT_CONFIRMATION", "TYPED_CONFIRMATION", "DISABLED_IN_AUTO_TEST"].includes(capability.automationPolicy);
}

function executableTail(): JourneyStep[] {
  return [
    {
      id: "execute",
      type: "EXECUTE",
      title: "작업 실행",
      description: "API 요청을 실행하고 결과를 확인합니다.",
      nextStepId: "success",
    },
    {
      id: "success",
      type: "SUCCESS",
      title: "작업 완료",
      description: "요청이 정상적으로 처리되었습니다.",
      nextStepId: null,
    },
  ];
}

function normalizedSelectedComponent(mode: JourneyMode, componentId?: string): string | undefined {
  if (!componentId || !isOverlayPart(componentId)) return undefined;
  if (componentId === "typed-confirm-modal") return "typed-danger-modal";
  if (["create-edit-modal", "delete-confirm-modal", "form-drawer"].includes(componentId)) return undefined;
  if (mode === "DELETE" && componentId !== "typed-danger-modal") return undefined;
  return componentId;
}

function collectStep(
  capability: PreviewCapability,
  mode: JourneyMode,
  componentId: string | undefined,
  nextStepId: string
): JourneyStep {
  return {
    id: "collect",
    type: "COLLECT",
    title: capability.action ?? (mode === "CREATE" ? "새 항목 생성" : mode === "UPDATE" ? "항목 수정" : "작업 설정"),
    description: `${capability.resourceName} 작업에 필요한 값을 입력하세요.`,
    componentId,
    fields: componentId ? undefined : capability.fields,
    nextStepId,
  };
}

export function createJourneyBlueprint({
  pageId,
  mode,
  capability,
  componentId,
}: {
  pageId: string;
  mode: JourneyMode;
  capability: PreviewCapability;
  componentId?: string;
}): JourneyBlueprint {
  const selected = normalizedSelectedComponent(mode, componentId);
  const specialized = mode === "COMMAND" ? specializedModal(capability) : undefined;
  const operationModal = specialized ?? selected;
  const highRisk = isHighRisk(capability);
  const steps: JourneyStep[] = [];

  if (mode === "DELETE") {
    steps.push({
      id: "impact",
      type: "REVIEW",
      title: "영향 범위 검토",
      description: "삭제 전에 연결된 리소스와 되돌릴 수 없는 영향을 확인하세요.",
      componentId: "dependency-impact-modal",
      nextStepId: "confirm",
    });
    steps.push({
      id: "confirm",
      type: "CONFIRM",
      title: "삭제 확인",
      description: "대상 이름을 입력해야 삭제를 진행할 수 있습니다.",
      componentId: "typed-danger-modal",
      nextStepId: "execute",
    });
  } else {
    const needsCollect = Boolean(operationModal || capability.fields.length > 0);
    const needsImpact = mode === "UPDATE" || (mode === "COMMAND" && highRisk);
    if (needsCollect) {
      steps.push(collectStep(
        capability,
        mode,
        operationModal,
        needsImpact ? "impact" : highRisk ? "confirm" : "execute"
      ));
    }
    if (needsImpact) {
      steps.push({
        id: "impact",
        type: "REVIEW",
        title: "변경 영향 검토",
        description: "연결된 데이터와 후속 작업에 미치는 영향을 확인하세요.",
        componentId: "dependency-impact-modal",
        nextStepId: highRisk ? "confirm" : "execute",
      });
    }
    if (highRisk) {
      steps.push({
        id: "confirm",
        type: "CONFIRM",
        title: "실행 확인",
        description: "위험도가 높은 작업입니다. 대상을 입력해 실행을 확인하세요.",
        componentId: "typed-danger-modal",
        nextStepId: "execute",
      });
    }
  }

  if (steps.length === 0) {
    steps.push({
      id: "review",
      type: "REVIEW",
      title: "요청 검토",
      description: `${capability.action ?? capability.resourceName} 작업을 실행합니다.`,
      componentId: "dependency-impact-modal",
      nextStepId: "execute",
    });
  }
  steps.push(...executableTail());

  return assertValidJourney({
    id: `${pageId}:${capability.id}:${mode.toLowerCase()}`,
    pageId,
    actionId: capability.id,
    mode,
    title: capability.action ?? capability.resourceName,
    entryStepId: steps[0].id,
    steps,
  });
}
