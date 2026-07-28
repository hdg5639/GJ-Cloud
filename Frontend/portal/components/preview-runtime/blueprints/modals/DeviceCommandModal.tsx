"use client";

import { createModalPart } from "../core";

export const DeviceCommandModal = createModalPart({
  "title": "Send device command",
  "description": "Execute a registered remote command.",
  "eyebrow": "Device control",
  "confirmLabel": "Send command",
  "style": "command",
  "requireReason": true,
  "fields": [
    {
      "key": "command",
      "label": "Command",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    },
    {
      "key": "payload",
      "label": "Payload",
      "type": "textarea"
    }
  ]
});
