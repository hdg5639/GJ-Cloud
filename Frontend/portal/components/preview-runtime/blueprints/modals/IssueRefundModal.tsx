"use client";

import { createModalPart } from "../core";

export const IssueRefundModal = createModalPart({
  "title": "Issue refund",
  "description": "Refund all or part of a captured payment.",
  "eyebrow": "Payments",
  "confirmLabel": "Issue refund",
  "style": "danger",
  "requireReason": true,
  "fields": [
    {
      "key": "amount",
      "label": "Refund amount",
      "type": "number"
    },
    {
      "key": "reasonCode",
      "label": "Reason",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    }
  ]
});
