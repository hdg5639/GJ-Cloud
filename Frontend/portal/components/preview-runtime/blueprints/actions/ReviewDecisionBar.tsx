"use client";

import { createActionPart } from "../core";

export const ReviewDecisionBar = createActionPart({
  "title": "Review decision",
  "style": "review",
  "actions": [
    {
      "id": "request changes",
      "label": "Request changes",
      "tone": "primary"
    },
    {
      "id": "approve",
      "label": "Approve",
      "tone": "secondary"
    },
    {
      "id": "reject",
      "label": "Reject",
      "tone": "danger"
    }
  ]
});
