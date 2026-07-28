"use client";

import { createWorkflowPart } from "../core";

export const DeviceProvisioningWizard = createWorkflowPart({
  "title": "Device provisioning",
  "description": "Register identity, assign policy, and verify connectivity.",
  "style": "provision",
  "steps": [
    {
      "id": "register device",
      "label": "Register device",
      "description": "Complete the register device stage with validated inputs."
    },
    {
      "id": "assign site",
      "label": "Assign site",
      "description": "Complete the assign site stage with validated inputs."
    },
    {
      "id": "apply policy",
      "label": "Apply policy",
      "description": "Complete the apply policy stage with validated inputs."
    },
    {
      "id": "install credentials",
      "label": "Install credentials",
      "description": "Complete the install credentials stage with validated inputs."
    },
    {
      "id": "verify connection",
      "label": "Verify connection",
      "description": "Complete the verify connection stage with validated inputs."
    }
  ],
  "completeLabel": "Provision device"
});
