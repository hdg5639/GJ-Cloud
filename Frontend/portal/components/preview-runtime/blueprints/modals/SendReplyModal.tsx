"use client";

import { createModalPart } from "../core";

export const SendReplyModal = createModalPart({
  "title": "Send customer reply",
  "description": "Review and send a reply using the selected channel.",
  "eyebrow": "Customer support",
  "confirmLabel": "Send reply",
  "style": "form",
  "requireReason": false,
  "fields": [
    {
      "key": "channel",
      "label": "Channel",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    },
    {
      "key": "message",
      "label": "Message",
      "type": "textarea"
    }
  ]
});
