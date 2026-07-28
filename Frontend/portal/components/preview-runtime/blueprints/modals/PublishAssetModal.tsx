"use client";

import { createModalPart } from "../core";

export const PublishAssetModal = createModalPart({
  "title": "Publish media asset",
  "description": "Choose destinations, visibility, and release schedule.",
  "eyebrow": "Publishing",
  "confirmLabel": "Publish",
  "style": "schedule",
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
      "key": "date",
      "label": "Publish date",
      "type": "date"
    }
  ]
});
