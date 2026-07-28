"use client";

import { createDetailPart } from "../core";

export const CandidateDetail = createDetailPart({
  "title": "Candidate",
  "description": "Application, interview stages, feedback, and decision context.",
  "style": "casefile",
  "statusField": "status",
  "primaryFields": [
    "name",
    "role",
    "stage",
    "owner"
  ],
  "secondaryFields": [
    "appliedAt",
    "score"
  ]
});
