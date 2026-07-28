"use client";

import { createActionPart } from "../core";

export const ProductHeroActions = createActionPart({
  "title": "Product actions",
  "style": "toolbar",
  "actions": [
    {
      "id": "try now",
      "label": "Try now",
      "tone": "primary"
    },
    {
      "id": "save",
      "label": "Save",
      "tone": "secondary"
    },
    {
      "id": "share",
      "label": "Share",
      "tone": "secondary"
    }
  ]
});
