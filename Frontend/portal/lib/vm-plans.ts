export const VM_PLAN_SPECS = {
  FREE: { cores: 4, memory: "4GB", diskMin: 20, diskMax: 50, diskStep: 5 },
  PRO: { cores: 8, memory: "10GB", diskMin: 20, diskMax: 100, diskStep: 5 },
} as const;
