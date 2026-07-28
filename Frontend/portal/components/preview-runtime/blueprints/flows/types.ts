import type { PreviewApiBinding, PreviewFlowBlueprint } from "@/lib/types";

export interface BlueprintFlowPreset {
  id: string;
  category: "CREATE" | "COMMAND" | "CHILD_RESOURCE" | "APPROVAL" | "CONTENT" | "DEPLOYMENT" | "DATA" | "STATUS" | "OWNERSHIP" | "BULK" | "DELETE" | "TRANSFER";
  title: string;
  description: string;
  flow: PreviewFlowBlueprint;
  bindings: PreviewApiBinding[];
  requiredCapabilityIds: string[];
}

export interface FlowPresetContext {
  pageId: string;
  capabilityId: string;
  bindingId?: string;
  detailPageId?: string;
  detailBindingId?: string;
  statusPath?: string;
  idPath?: string;
}
