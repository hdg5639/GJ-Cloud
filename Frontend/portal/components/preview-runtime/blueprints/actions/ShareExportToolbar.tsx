"use client";

import { createActionPart } from "../core";

export const ShareExportToolbar = createActionPart({
  "title": "Share and export",
  "style": "toolbar",
  "actions": [
    {
      "id": "share",
      "label": "Share",
      "tone": "primary"
    },
    {
      "id": "copy link",
      "label": "Copy link",
      "tone": "secondary"
    },
    {
      "id": "export",
      "label": "Export",
      "tone": "secondary"
    }
  ]
});
