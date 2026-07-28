"use client";

import { createDetailPart } from "../core";

export const ApiProductDetail = createDetailPart({
  "title": "API product",
  "description": "Endpoints, plans, consumers, keys, and usage context.",
  "style": "technical",
  "statusField": "status",
  "primaryFields": [
    "name",
    "version",
    "baseUrl",
    "status"
  ],
  "secondaryFields": [
    "owner",
    "updatedAt"
  ]
});
