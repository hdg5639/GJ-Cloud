"use client";

import { createDetailPart } from "../core";

export const LegalMatterDetail = createDetailPart({
  "title": "Legal matter",
  "description": "Parties, risk, deadlines, documents, and review history.",
  "style": "casefile",
  "statusField": "status",
  "primaryFields": [
    "title",
    "matterNumber",
    "risk",
    "status"
  ],
  "secondaryFields": [
    "owner",
    "nextDeadline"
  ]
});
