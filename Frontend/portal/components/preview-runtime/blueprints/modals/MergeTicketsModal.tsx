"use client";

import { createModalPart } from "../core";

export const MergeTicketsModal = createModalPart({
  "title": "Merge tickets",
  "description": "Combine related customer conversations into a primary ticket.",
  "eyebrow": "Support",
  "confirmLabel": "Merge tickets",
  "style": "impact",
  "requireReason": true,
  "fields": [
    {
      "key": "targetTicket",
      "label": "Primary ticket",
      "type": "text"
    }
  ]
});
