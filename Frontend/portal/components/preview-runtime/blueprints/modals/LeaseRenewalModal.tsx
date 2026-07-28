"use client";

import { createModalPart } from "../core";

export const LeaseRenewalModal = createModalPart({
  "title": "Renew lease",
  "description": "Review dates, terms, and adjusted rent.",
  "eyebrow": "Lease management",
  "confirmLabel": "Create renewal",
  "style": "review",
  "requireReason": false,
  "fields": [
    {
      "key": "endDate",
      "label": "New end date",
      "type": "date"
    },
    {
      "key": "rent",
      "label": "Monthly rent",
      "type": "number"
    }
  ]
});
