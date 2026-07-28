"use client";

import { createDetailPart } from "../core";

export const PromptDetail = createDetailPart({
  "title": "Prompt",
  "description": "Template, variables, evaluations, usage, and ownership.",
  "style": "document",
  "statusField": "status",
  "primaryFields": [
    "name",
    "task",
    "version",
    "status"
  ],
  "secondaryFields": [
    "owner",
    "updatedAt"
  ]
});
