"use client";

import { createModalPart } from "../core";

export const DeliveryExceptionModal = createModalPart({
  "title": "Resolve delivery exception",
  "description": "Record the cause and choose the next delivery action.",
  "eyebrow": "Delivery exception",
  "confirmLabel": "Apply resolution",
  "style": "impact",
  "requireReason": true,
  "fields": [
    {
      "key": "resolution",
      "label": "Resolution",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    },
    {
      "key": "nextAttemptAt",
      "label": "Next attempt",
      "type": "date"
    }
  ]
});
