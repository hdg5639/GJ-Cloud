"use client";

import { createModalPart } from "../core";

export const FirmwareUpdateModal = createModalPart({
  "title": "Schedule firmware update",
  "description": "Select firmware and a safe maintenance window.",
  "eyebrow": "Device maintenance",
  "confirmLabel": "Schedule update",
  "style": "schedule",
  "requireReason": false,
  "fields": [
    {
      "key": "firmware",
      "label": "Firmware",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    },
    {
      "key": "date",
      "label": "Maintenance date",
      "type": "date"
    }
  ]
});
