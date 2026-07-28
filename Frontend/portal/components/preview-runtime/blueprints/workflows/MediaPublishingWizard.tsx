"use client";

import { createWorkflowPart } from "../core";

export const MediaPublishingWizard = createWorkflowPart({
  "title": "Media publishing",
  "description": "Review asset, rights, destinations, and release timing.",
  "style": "wizard",
  "steps": [
    {
      "id": "select asset",
      "label": "Select asset",
      "description": "Complete the select asset stage with validated inputs."
    },
    {
      "id": "review quality",
      "label": "Review quality",
      "description": "Complete the review quality stage with validated inputs."
    },
    {
      "id": "verify rights",
      "label": "Verify rights",
      "description": "Complete the verify rights stage with validated inputs."
    },
    {
      "id": "choose channels",
      "label": "Choose channels",
      "description": "Complete the choose channels stage with validated inputs."
    },
    {
      "id": "publish",
      "label": "Publish",
      "description": "Complete the publish stage with validated inputs."
    }
  ],
  "completeLabel": "Publish media"
});
