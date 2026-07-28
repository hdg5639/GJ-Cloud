"use client";

import { createModalPart } from "../core";

export const TripChangeModal = createModalPart({
  "title": "Change trip",
  "description": "Review the impact of changing a travel segment.",
  "eyebrow": "Travel operations",
  "confirmLabel": "Apply change",
  "style": "impact",
  "requireReason": true,
  "fields": [
    {
      "key": "segment",
      "label": "Segment",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    },
    {
      "key": "newDate",
      "label": "New date",
      "type": "date"
    }
  ]
});
