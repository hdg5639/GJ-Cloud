"use client";

import { createCollectionPart } from "../core";

export const CandidatePipeline = createCollectionPart({
  "title": "Candidate pipeline",
  "description": "Candidates grouped by hiring stage.",
  "style": "board",
  "primaryField": "name",
  "secondaryField": "role",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No candidate pipeline records"
});
