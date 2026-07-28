"use client";

import { createModalPart } from "../core";

export const RescheduleBookingModal = createModalPart({
  "title": "Reschedule booking",
  "description": "Choose a new date and preserve booking context.",
  "eyebrow": "Booking",
  "confirmLabel": "Reschedule",
  "style": "schedule",
  "requireReason": false,
  "fields": [
    {
      "key": "date",
      "label": "New date",
      "type": "date"
    },
    {
      "key": "slot",
      "label": "Time slot",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    }
  ]
});
