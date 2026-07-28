"use client";

import { createModalPart } from "../core";

export const EscalateIncidentModal = createModalPart({
  "title": "Escalate incident",
  "description": "Increase severity and notify the response team.",
  "eyebrow": "Incident response",
  "confirmLabel": "Escalate",
  "style": "danger",
  "requireReason": true,
  "fields": [
    {
      "key": "severity",
      "label": "Severity",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    },
    {
      "key": "team",
      "label": "Response team",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    }
  ],
  "requireText": "ESCALATE"
});
