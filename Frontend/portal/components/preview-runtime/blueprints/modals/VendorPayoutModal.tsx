"use client";

import { createModalPart } from "../core";

export const VendorPayoutModal = createModalPart({
  "title": "Release vendor payout",
  "description": "Review settlement and release funds to the vendor.",
  "eyebrow": "Marketplace finance",
  "confirmLabel": "Release payout",
  "style": "review",
  "requireReason": true,
  "fields": [
    {
      "key": "amount",
      "label": "Amount",
      "type": "number"
    },
    {
      "key": "method",
      "label": "Payout method",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    }
  ]
});
