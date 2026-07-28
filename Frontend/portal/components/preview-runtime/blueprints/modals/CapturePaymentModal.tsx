"use client";

import { createModalPart } from "../core";

export const CapturePaymentModal = createModalPart({
  "title": "Capture payment",
  "description": "Capture an authorized payment amount.",
  "eyebrow": "Payments",
  "confirmLabel": "Capture",
  "style": "confirm",
  "requireReason": false,
  "fields": [
    {
      "key": "amount",
      "label": "Capture amount",
      "type": "number"
    }
  ]
});
