"use client";

import { createWorkflowPart } from "../core";

export const ModelDeploymentWizard = createWorkflowPart({
  "title": "Model deployment",
  "description": "Evaluate, configure, approve, and deploy a model version.",
  "style": "provision",
  "steps": [
    {
      "id": "select model",
      "label": "Select model",
      "description": "Complete the select model stage with validated inputs."
    },
    {
      "id": "review evaluation",
      "label": "Review evaluation",
      "description": "Complete the review evaluation stage with validated inputs."
    },
    {
      "id": "configure serving",
      "label": "Configure serving",
      "description": "Complete the configure serving stage with validated inputs."
    },
    {
      "id": "approve risk",
      "label": "Approve risk",
      "description": "Complete the approve risk stage with validated inputs."
    },
    {
      "id": "deploy",
      "label": "Deploy",
      "description": "Complete the deploy stage with validated inputs."
    }
  ],
  "completeLabel": "Deploy model"
});
