"use client";

import { createModalPart } from "../core";

export const PromptTestModal = createModalPart({
  "title": "Test prompt",
  "description": "Run the prompt with controlled variables and sample input.",
  "eyebrow": "Prompt lab",
  "confirmLabel": "Run test",
  "style": "command",
  "requireReason": false,
  "fields": [
    {
      "key": "input",
      "label": "Test input",
      "type": "textarea"
    },
    {
      "key": "temperature",
      "label": "Temperature",
      "type": "number"
    }
  ]
});
