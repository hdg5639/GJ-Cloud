"use client";

import { createModalPart } from "../core";

export const AdjustInventoryModal = createModalPart({
  "title": "Adjust inventory",
  "description": "Apply a controlled stock correction with an audit reason.",
  "eyebrow": "Inventory",
  "confirmLabel": "Apply adjustment",
  "style": "form",
  "requireReason": true,
  "fields": [
    {
      "key": "quantity",
      "label": "Quantity delta",
      "type": "number"
    },
    {
      "key": "location",
      "label": "Location",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    }
  ]
});
