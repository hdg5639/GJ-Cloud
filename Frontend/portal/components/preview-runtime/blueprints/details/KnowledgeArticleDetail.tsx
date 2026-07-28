"use client";

import { createDetailPart } from "../core";

export const KnowledgeArticleDetail = createDetailPart({
  "title": "Knowledge article",
  "description": "Content, ownership, freshness, references, and feedback.",
  "style": "document",
  "statusField": "status",
  "primaryFields": [
    "title",
    "category",
    "owner",
    "status"
  ],
  "secondaryFields": [
    "updatedAt",
    "reviewDueAt"
  ]
});
