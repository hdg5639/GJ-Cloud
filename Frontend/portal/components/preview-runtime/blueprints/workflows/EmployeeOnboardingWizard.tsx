"use client";

import { createWorkflowPart } from "../core";

export const EmployeeOnboardingWizard = createWorkflowPart({
  "title": "Employee onboarding",
  "description": "Coordinate profile, access, equipment, and first-week tasks.",
  "style": "provision",
  "steps": [
    {
      "id": "employee profile",
      "label": "Employee profile",
      "description": "Complete the employee profile stage with validated inputs."
    },
    {
      "id": "role and team",
      "label": "Role and team",
      "description": "Complete the role and team stage with validated inputs."
    },
    {
      "id": "system access",
      "label": "System access",
      "description": "Complete the system access stage with validated inputs."
    },
    {
      "id": "equipment",
      "label": "Equipment",
      "description": "Complete the equipment stage with validated inputs."
    },
    {
      "id": "welcome plan",
      "label": "Welcome plan",
      "description": "Complete the welcome plan stage with validated inputs."
    }
  ],
  "completeLabel": "Start onboarding"
});
