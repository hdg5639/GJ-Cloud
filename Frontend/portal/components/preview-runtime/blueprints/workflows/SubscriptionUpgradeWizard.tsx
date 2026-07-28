"use client";

import { createWorkflowPart } from "../core";

export const SubscriptionUpgradeWizard = createWorkflowPart({
  "title": "Subscription upgrade",
  "description": "Select a plan, preview impact, and schedule billing changes.",
  "style": "checkout",
  "steps": [
    {
      "id": "choose plan",
      "label": "Choose plan",
      "description": "Complete the choose plan stage with validated inputs."
    },
    {
      "id": "configure seats",
      "label": "Configure seats",
      "description": "Complete the configure seats stage with validated inputs."
    },
    {
      "id": "preview charges",
      "label": "Preview charges",
      "description": "Complete the preview charges stage with validated inputs."
    },
    {
      "id": "confirm timing",
      "label": "Confirm timing",
      "description": "Complete the confirm timing stage with validated inputs."
    },
    {
      "id": "apply upgrade",
      "label": "Apply upgrade",
      "description": "Complete the apply upgrade stage with validated inputs."
    }
  ],
  "completeLabel": "Upgrade subscription"
});
