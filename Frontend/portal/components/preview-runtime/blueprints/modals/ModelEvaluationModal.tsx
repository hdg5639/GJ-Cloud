"use client";

import { createModalPart } from "../core";

export const ModelEvaluationModal = createModalPart({
  "title": "Run model evaluation",
  "description": "Choose a dataset and evaluation policy.",
  "eyebrow": "AI evaluation",
  "confirmLabel": "Start evaluation",
  "style": "form",
  "requireReason": false,
  "fields": [
    {
      "key": "dataset",
      "label": "Dataset",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    },
    {
      "key": "metric",
      "label": "Primary metric",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    }
  ]
});
