"use client";

import { createDetailPart } from "../core";

export const MediaAssetDetail = createDetailPart({
  "title": "Media asset",
  "description": "Preview, metadata, versions, rights, review, and publishing.",
  "style": "commerce",
  "statusField": "status",
  "primaryFields": [
    "title",
    "format",
    "duration",
    "status"
  ],
  "secondaryFields": [
    "owner",
    "updatedAt"
  ]
});
