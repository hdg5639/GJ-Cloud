"use client";

import { createModalPart } from "../core";

export const SeatAssignmentModal = createModalPart({
  "title": "Assign seat",
  "description": "Select a venue section or seat for the attendee.",
  "eyebrow": "Event seating",
  "confirmLabel": "Assign",
  "style": "picker",
  "requireReason": false,
  "fields": [
    {
      "key": "section",
      "label": "Section",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    },
    {
      "key": "seat",
      "label": "Seat",
      "type": "text"
    }
  ]
});
