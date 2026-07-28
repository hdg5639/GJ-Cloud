"use client";

import { createModalPart } from "../core";

export const ReassignShipmentModal = createModalPart({
  "title": "Reassign shipment",
  "description": "Move the shipment to another carrier, route, or driver.",
  "eyebrow": "Dispatch",
  "confirmLabel": "Reassign",
  "style": "picker",
  "requireReason": true,
  "fields": [
    {
      "key": "assignee",
      "label": "New assignee",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    }
  ]
});
