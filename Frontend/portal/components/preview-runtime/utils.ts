import type { CapabilityType, PreviewCapability, PreviewPage } from "./types";

export function findCapabilityById(capabilities: PreviewCapability[], id: string): PreviewCapability | undefined {
  return capabilities.find((c) => c.id === id);
}

export function findCapabilityByType(
  capabilities: PreviewCapability[],
  page: PreviewPage,
  type: CapabilityType
): PreviewCapability | undefined {
  for (const id of page.capabilityIds) {
    const capability = findCapabilityById(capabilities, id);
    if (capability && capability.type === type) {
      return capability;
    }
  }
  return undefined;
}
