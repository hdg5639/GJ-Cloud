import type { PreviewApiBinding, PreviewFlowStep } from "@/lib/types";
import type { BlueprintFlowPreset, FlowPresetContext } from "./types";

const step = (value: PreviewFlowStep): PreviewFlowStep => value;

function binding(id: string, capabilityId: string, options?: Partial<PreviewApiBinding>): PreviewApiBinding {
  return {
    id,
    capabilityId,
    inputMappings: options?.inputMappings ?? [],
    outputMappings: options?.outputMappings ?? [],
    refreshBindingIds: options?.refreshBindingIds ?? [],
  };
}

export function createNavigatePreset(context: FlowPresetContext): BlueprintFlowPreset {
  const bindingId = context.bindingId ?? `${context.capabilityId}-binding`;
  const detailPageId = context.detailPageId ?? `${context.pageId}-detail`;
  return {
    id: `${context.capabilityId}-create-navigate`,
    category: "CREATE",
    title: "Create and open detail",
    description: "Submit a create operation, capture the created resource ID, and open its detail page.",
    requiredCapabilityIds: [context.capabilityId],
    bindings: [binding(bindingId, context.capabilityId, { outputMappings: [{ from: context.idPath ?? "data.id", to: "createdId" }] })],
    flow: {
      id: `${context.capabilityId}-create-navigate`,
      trigger: { pageId: context.pageId, actionId: "create" },
      steps: [
        step({ id: "create", type: "API_CALL", bindingRef: bindingId, input: { body: "$form" }, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null }),
        step({ id: "navigate", type: "NAVIGATE", bindingRef: null, input: null, values: null, pageId: detailPageId, parameters: { id: "$context.createdId" }, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null }),
      ],
    },
  };
}

export function createPollNavigatePreset(context: FlowPresetContext): BlueprintFlowPreset {
  const createBindingId = context.bindingId ?? `${context.capabilityId}-binding`;
  const detailBindingId = context.detailBindingId ?? `${context.capabilityId}-detail-binding`;
  const detailPageId = context.detailPageId ?? `${context.pageId}-detail`;
  return {
    id: `${context.capabilityId}-create-poll-navigate`,
    category: "CREATE",
    title: "Create, track, and open detail",
    description: "Create a resource, poll its detail binding until a terminal state, then navigate to detail.",
    requiredCapabilityIds: [context.capabilityId],
    bindings: [
      binding(createBindingId, context.capabilityId, { outputMappings: [{ from: context.idPath ?? "data.id", to: "createdId" }] }),
      binding(detailBindingId, context.capabilityId, { inputMappings: [{ target: "id", targetKind: "PATH", from: "$context.createdId" }] }),
    ],
    flow: {
      id: `${context.capabilityId}-create-poll-navigate`,
      trigger: { pageId: context.pageId, actionId: "create" },
      steps: [
        step({ id: "create", type: "API_CALL", bindingRef: createBindingId, input: { body: "$form" }, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null }),
        step({ id: "poll", type: "POLL", bindingRef: detailBindingId, input: null, values: null, pageId: null, parameters: { id: "$context.createdId" }, until: [{ path: context.statusPath ?? "response.data.status", equalsValue: null, in: ["READY", "RUNNING", "SUCCEEDED", "FAILED"] }], intervalMs: 3000, timeoutSeconds: 180, condition: null, message: "Tracking resource status" }),
        step({ id: "navigate", type: "NAVIGATE", bindingRef: null, input: null, values: null, pageId: detailPageId, parameters: { id: "$context.createdId" }, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null }),
      ],
    },
  };
}

export function commandRefreshPreset(context: FlowPresetContext, refreshBindingIds: string[] = []): BlueprintFlowPreset {
  const bindingId = context.bindingId ?? `${context.capabilityId}-binding`;
  return {
    id: `${context.capabilityId}-command-refresh`,
    category: "COMMAND",
    title: "Command and refresh",
    description: "Execute a user-initiated command and refresh dependent detail and collection bindings.",
    requiredCapabilityIds: [context.capabilityId],
    bindings: [binding(bindingId, context.capabilityId, { inputMappings: [{ target: "id", targetKind: "PATH", from: "$route.id" }], refreshBindingIds })],
    flow: {
      id: `${context.capabilityId}-command-refresh`,
      trigger: { pageId: context.pageId, actionId: context.capabilityId },
      steps: [
        step({ id: "command", type: "API_CALL", bindingRef: bindingId, input: null, values: null, pageId: null, parameters: { id: "$route.id" }, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null }),
        ...refreshBindingIds.map((id, index) => step({ id: `refresh-${index}`, type: "REFRESH_BINDING", bindingRef: id, input: null, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null })),
        step({ id: "success", type: "SHOW_SUCCESS", bindingRef: null, input: null, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: "Command completed" }),
      ],
    },
  };
}

export function childCreateRefreshPreset(context: FlowPresetContext, childListBindingId: string): BlueprintFlowPreset {
  const bindingId = context.bindingId ?? `${context.capabilityId}-binding`;
  return {
    id: `${context.capabilityId}-child-create-refresh`,
    category: "CHILD_RESOURCE",
    title: "Create child and refresh collection",
    description: "Create a nested child resource with the current parent Route context and refresh its list.",
    requiredCapabilityIds: [context.capabilityId],
    bindings: [binding(bindingId, context.capabilityId, { inputMappings: [{ target: "parentId", targetKind: "PATH", from: "$route.id" }], refreshBindingIds: [childListBindingId] })],
    flow: {
      id: `${context.capabilityId}-child-create-refresh`,
      trigger: { pageId: context.pageId, actionId: "create-child" },
      steps: [
        step({ id: "create-child", type: "API_CALL", bindingRef: bindingId, input: { body: "$form" }, values: null, pageId: null, parameters: { parentId: "$route.id" }, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null }),
        step({ id: "refresh-child", type: "REFRESH_BINDING", bindingRef: childListBindingId, input: null, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null }),
      ],
    },
  };
}

export function approvalRequestPreset(context: FlowPresetContext): BlueprintFlowPreset {
  const bindingId = context.bindingId ?? `${context.capabilityId}-binding`;
  return {
    id: `${context.capabilityId}-approval-request`, category: "APPROVAL", title: "Submit approval request", description: "Create an approval request and show a pending decision state.", requiredCapabilityIds: [context.capabilityId],
    bindings: [binding(bindingId, context.capabilityId, { outputMappings: [{ from: "data.id", to: "approvalId" }] })],
    flow: { id: `${context.capabilityId}-approval-request`, trigger: { pageId: context.pageId, actionId: "request-approval" }, steps: [
      step({ id: "submit", type: "API_CALL", bindingRef: bindingId, input: { body: "$form" }, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null }),
      step({ id: "message", type: "SHOW_SUCCESS", bindingRef: null, input: null, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: "Approval request submitted" }),
    ] },
  };
}

export function publishSchedulePreset(context: FlowPresetContext): BlueprintFlowPreset {
  const bindingId = context.bindingId ?? `${context.capabilityId}-binding`;
  return {
    id: `${context.capabilityId}-publish-schedule`, category: "CONTENT", title: "Publish or schedule content", description: "Submit publication settings and refresh the current content detail.", requiredCapabilityIds: [context.capabilityId],
    bindings: [binding(bindingId, context.capabilityId, { inputMappings: [{ target: "id", targetKind: "PATH", from: "$route.id" }], refreshBindingIds: context.detailBindingId ? [context.detailBindingId] : [] })],
    flow: { id: `${context.capabilityId}-publish-schedule`, trigger: { pageId: context.pageId, actionId: "publish" }, steps: [
      step({ id: "publish", type: "API_CALL", bindingRef: bindingId, input: { body: "$form" }, values: null, pageId: null, parameters: { id: "$route.id" }, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null }),
      ...(context.detailBindingId ? [step({ id: "refresh", type: "REFRESH_BINDING", bindingRef: context.detailBindingId, input: null, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null })] : []),
    ] },
  };
}

export function deploymentPollPreset(context: FlowPresetContext, statusBindingId: string): BlueprintFlowPreset {
  const bindingId = context.bindingId ?? `${context.capabilityId}-binding`;
  return {
    id: `${context.capabilityId}-deployment-poll`, category: "DEPLOYMENT", title: "Deploy and track status", description: "Start a deployment, capture its ID, and poll until a terminal deployment state.", requiredCapabilityIds: [context.capabilityId],
    bindings: [binding(bindingId, context.capabilityId, { outputMappings: [{ from: context.idPath ?? "data.id", to: "deploymentId" }] })],
    flow: { id: `${context.capabilityId}-deployment-poll`, trigger: { pageId: context.pageId, actionId: "deploy" }, steps: [
      step({ id: "deploy", type: "API_CALL", bindingRef: bindingId, input: { body: "$form" }, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null }),
      step({ id: "poll", type: "POLL", bindingRef: statusBindingId, input: null, values: null, pageId: null, parameters: { id: "$context.deploymentId" }, until: [{ path: context.statusPath ?? "response.data.status", equalsValue: null, in: ["SUCCEEDED", "FAILED", "CANCELLED"] }], intervalMs: 3000, timeoutSeconds: 600, condition: null, message: "Tracking deployment" }),
    ] },
  };
}

export function dataImportPreset(context: FlowPresetContext, validateBindingId: string, commitBindingId: string): BlueprintFlowPreset {
  return {
    id: `${context.capabilityId}-validate-commit`, category: "DATA", title: "Validate and commit import", description: "Upload or submit data for validation, then commit valid rows through an explicit user step.", requiredCapabilityIds: [context.capabilityId],
    bindings: [binding(validateBindingId, context.capabilityId), binding(commitBindingId, context.capabilityId)],
    flow: { id: `${context.capabilityId}-validate-commit`, trigger: { pageId: context.pageId, actionId: "import" }, steps: [
      step({ id: "validate", type: "API_CALL", bindingRef: validateBindingId, input: { body: "$form" }, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null }),
      step({ id: "commit", type: "API_CALL", bindingRef: commitBindingId, input: { body: "$steps.validate.response" }, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: "$context.userConfirmed", message: null }),
      step({ id: "success", type: "SHOW_SUCCESS", bindingRef: null, input: null, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: "Import completed" }),
    ] },
  };
}

export function statusTransitionPreset(context: FlowPresetContext, refreshBindingId?: string): BlueprintFlowPreset {
  const bindingId = context.bindingId ?? `${context.capabilityId}-binding`;
  return {
    id: `${context.capabilityId}-status-transition`, category: "STATUS", title: "Status transition", description: "Apply a lifecycle transition and refresh current resource state.", requiredCapabilityIds: [context.capabilityId],
    bindings: [binding(bindingId, context.capabilityId, { inputMappings: [{ target: "id", targetKind: "PATH", from: "$route.id" }], refreshBindingIds: refreshBindingId ? [refreshBindingId] : [] })],
    flow: { id: `${context.capabilityId}-status-transition`, trigger: { pageId: context.pageId, actionId: "change-status" }, steps: [
      step({ id: "transition", type: "API_CALL", bindingRef: bindingId, input: { body: "$form" }, values: null, pageId: null, parameters: { id: "$route.id" }, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null }),
      ...(refreshBindingId ? [step({ id: "refresh", type: "REFRESH_BINDING", bindingRef: refreshBindingId, input: null, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null })] : []),
    ] },
  };
}

export function assignOwnerPreset(context: FlowPresetContext, refreshBindingId?: string): BlueprintFlowPreset {
  const bindingId = context.bindingId ?? `${context.capabilityId}-binding`;
  return {
    id: `${context.capabilityId}-assign-owner`, category: "OWNERSHIP", title: "Assign owner", description: "Update resource ownership and refresh its detail state.", requiredCapabilityIds: [context.capabilityId],
    bindings: [binding(bindingId, context.capabilityId, { inputMappings: [{ target: "id", targetKind: "PATH", from: "$route.id" }], refreshBindingIds: refreshBindingId ? [refreshBindingId] : [] })],
    flow: { id: `${context.capabilityId}-assign-owner`, trigger: { pageId: context.pageId, actionId: "assign-owner" }, steps: [
      step({ id: "assign", type: "API_CALL", bindingRef: bindingId, input: { body: "$form" }, values: null, pageId: null, parameters: { id: "$route.id" }, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null }),
      ...(refreshBindingId ? [step({ id: "refresh", type: "REFRESH_BINDING", bindingRef: refreshBindingId, input: null, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null })] : []),
    ] },
  };
}

export function bulkMutationPreset(context: FlowPresetContext, refreshBindingId?: string): BlueprintFlowPreset {
  const bindingId = context.bindingId ?? `${context.capabilityId}-binding`;
  return {
    id: `${context.capabilityId}-bulk-mutation`, category: "BULK", title: "Bulk mutation", description: "Apply a user-confirmed mutation to selected resource IDs and refresh the collection.", requiredCapabilityIds: [context.capabilityId],
    bindings: [binding(bindingId, context.capabilityId, { refreshBindingIds: refreshBindingId ? [refreshBindingId] : [] })],
    flow: { id: `${context.capabilityId}-bulk-mutation`, trigger: { pageId: context.pageId, actionId: "bulk-action" }, steps: [
      step({ id: "mutate", type: "API_CALL", bindingRef: bindingId, input: { body: "$form" }, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: "$context.userConfirmed", message: null }),
      ...(refreshBindingId ? [step({ id: "refresh", type: "REFRESH_BINDING", bindingRef: refreshBindingId, input: null, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null })] : []),
    ] },
  };
}

export function deleteNavigateBackPreset(context: FlowPresetContext, listPageId: string, listBindingId?: string): BlueprintFlowPreset {
  const bindingId = context.bindingId ?? `${context.capabilityId}-binding`;
  return {
    id: `${context.capabilityId}-delete-back`, category: "DELETE", title: "Delete and return to collection", description: "Execute an explicitly confirmed deletion, refresh the list, and navigate back.", requiredCapabilityIds: [context.capabilityId],
    bindings: [binding(bindingId, context.capabilityId, { inputMappings: [{ target: "id", targetKind: "PATH", from: "$route.id" }], refreshBindingIds: listBindingId ? [listBindingId] : [] })],
    flow: { id: `${context.capabilityId}-delete-back`, trigger: { pageId: context.pageId, actionId: "delete" }, steps: [
      step({ id: "delete", type: "API_CALL", bindingRef: bindingId, input: null, values: null, pageId: null, parameters: { id: "$route.id" }, until: null, intervalMs: null, timeoutSeconds: null, condition: "$context.userConfirmed", message: null }),
      ...(listBindingId ? [step({ id: "refresh", type: "REFRESH_BINDING", bindingRef: listBindingId, input: null, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null })] : []),
      step({ id: "navigate", type: "NAVIGATE", bindingRef: null, input: null, values: null, pageId: listPageId, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null }),
    ] },
  };
}

export function uploadProcessPreset(context: FlowPresetContext, statusBindingId?: string): BlueprintFlowPreset {
  const bindingId = context.bindingId ?? `${context.capabilityId}-binding`;
  return {
    id: `${context.capabilityId}-upload-process`, category: "TRANSFER", title: "Upload and process file", description: "Upload a file through a registered binding and optionally track server-side processing.", requiredCapabilityIds: [context.capabilityId],
    bindings: [binding(bindingId, context.capabilityId, { outputMappings: [{ from: "data.id", to: "uploadId" }] })],
    flow: { id: `${context.capabilityId}-upload-process`, trigger: { pageId: context.pageId, actionId: "upload" }, steps: [
      step({ id: "upload", type: "UPLOAD", bindingRef: bindingId, input: { body: "$form" }, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: null }),
      ...(statusBindingId ? [step({ id: "poll", type: "POLL", bindingRef: statusBindingId, input: null, values: null, pageId: null, parameters: { id: "$context.uploadId" }, until: [{ path: "response.data.status", equalsValue: null, in: ["COMPLETED", "FAILED"] }], intervalMs: 2000, timeoutSeconds: 300, condition: null, message: "Processing uploaded file" })] : []),
    ] },
  };
}

export function downloadExportPreset(context: FlowPresetContext): BlueprintFlowPreset {
  const bindingId = context.bindingId ?? `${context.capabilityId}-binding`;
  return {
    id: `${context.capabilityId}-download-export`, category: "TRANSFER", title: "Generate and download export", description: "Request an export and download it through a registered download binding.", requiredCapabilityIds: [context.capabilityId],
    bindings: [binding(bindingId, context.capabilityId)],
    flow: { id: `${context.capabilityId}-download-export`, trigger: { pageId: context.pageId, actionId: "export" }, steps: [
      step({ id: "download", type: "DOWNLOAD", bindingRef: bindingId, input: { body: "$form" }, values: null, pageId: null, parameters: null, until: null, intervalMs: null, timeoutSeconds: null, condition: null, message: "Preparing export" }),
    ] },
  };
}

export const FLOW_PRESET_FACTORIES = {
  createNavigatePreset,
  createPollNavigatePreset,
  commandRefreshPreset,
  childCreateRefreshPreset,
  approvalRequestPreset,
  publishSchedulePreset,
  deploymentPollPreset,
  dataImportPreset,
  statusTransitionPreset,
  assignOwnerPreset,
  bulkMutationPreset,
  deleteNavigateBackPreset,
  uploadProcessPreset,
  downloadExportPreset,
};
