"use client";

import { createActionPart } from "../core";

export const FloatingCreateAction = createActionPart({
  "title": "Create",
  "style": "floating",
  "actions": [
    {
      "id": "create resource",
      "label": "Create resource",
      "tone": "primary"
    }
  ]
});
