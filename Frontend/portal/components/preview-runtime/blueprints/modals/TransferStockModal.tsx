"use client";

import { createModalPart } from "../core";

export const TransferStockModal = createModalPart({
  "title": "Transfer stock",
  "description": "Move available stock between warehouse locations.",
  "eyebrow": "Inventory transfer",
  "confirmLabel": "Create transfer",
  "style": "form",
  "requireReason": false,
  "fields": [
    {
      "key": "source",
      "label": "Source",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    },
    {
      "key": "destination",
      "label": "Destination",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    },
    {
      "key": "quantity",
      "label": "Quantity",
      "type": "number"
    }
  ]
});
