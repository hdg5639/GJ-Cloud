"use client";

import { createDetailPart } from "../core";

export const ModelDetail = createDetailPart({
  "title": "Model",
  "description": "Version, quality, latency, cost, and deployment information.",
  "style": "technical",
  "statusField": "status",
  "primaryFields": [
    "name",
    "version",
    "stage",
    "status"
  ],
  "secondaryFields": [
    "quality",
    "latency"
  ]
});
