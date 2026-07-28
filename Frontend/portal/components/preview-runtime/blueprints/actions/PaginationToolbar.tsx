"use client";

import { createActionPart } from "../core";

export const PaginationToolbar = createActionPart({
  "title": "Pagination",
  "style": "segmented",
  "actions": [
    {
      "id": "previous",
      "label": "Previous",
      "tone": "primary"
    },
    {
      "id": "1",
      "label": "1",
      "tone": "secondary"
    },
    {
      "id": "2",
      "label": "2",
      "tone": "secondary"
    },
    {
      "id": "3",
      "label": "3",
      "tone": "secondary"
    },
    {
      "id": "next",
      "label": "Next",
      "tone": "secondary"
    }
  ]
});
