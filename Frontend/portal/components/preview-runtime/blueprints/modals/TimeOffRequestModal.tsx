"use client";

import { createModalPart } from "../core";

export const TimeOffRequestModal = createModalPart({
  "title": "Request time off",
  "description": "Submit dates, type, and coverage details.",
  "eyebrow": "People operations",
  "confirmLabel": "Submit request",
  "style": "form",
  "requireReason": false,
  "fields": [
    {
      "key": "type",
      "label": "Leave type",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    },
    {
      "key": "startDate",
      "label": "Start date",
      "type": "date"
    },
    {
      "key": "endDate",
      "label": "End date",
      "type": "date"
    }
  ]
});
