"use client";

import { createDetailPart } from "../core";

export const CustomerSuccessDetail = createDetailPart({
  "title": "Customer account",
  "description": "Health, adoption, contacts, renewal, and opportunity context.",
  "style": "profile",
  "statusField": "status",
  "primaryFields": [
    "name",
    "plan",
    "health",
    "renewalDate"
  ],
  "secondaryFields": [
    "owner",
    "segment"
  ]
});
