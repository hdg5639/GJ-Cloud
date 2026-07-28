"use client";

import { createModalPart } from "../core";

export const CompensationChangeModal = createModalPart({
  "title": "Change compensation",
  "description": "Review a sensitive compensation adjustment.",
  "eyebrow": "Restricted HR",
  "confirmLabel": "Apply change",
  "style": "danger",
  "requireReason": true,
  "fields": [
    {
      "key": "amount",
      "label": "New amount",
      "type": "number"
    },
    {
      "key": "effectiveDate",
      "label": "Effective date",
      "type": "date"
    }
  ],
  "requireText": "CONFIRM"
});
