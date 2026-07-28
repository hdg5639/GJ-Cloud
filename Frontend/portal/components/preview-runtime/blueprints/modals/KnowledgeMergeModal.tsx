"use client";

import { createModalPart } from "../core";

export const KnowledgeMergeModal = createModalPart({
  "title": "Merge knowledge articles",
  "description": "Combine duplicate articles and preserve references.",
  "eyebrow": "Knowledge management",
  "confirmLabel": "Merge articles",
  "style": "impact",
  "requireReason": true,
  "fields": [
    {
      "key": "targetArticle",
      "label": "Primary article",
      "type": "text"
    }
  ]
});
