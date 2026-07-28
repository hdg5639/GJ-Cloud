"use client";

import { createDetailPart } from "../core";

export const ThreatIncidentDetail = createDetailPart({
  "title": "Threat incident",
  "description": "Evidence, impact, response, and investigation context.",
  "style": "casefile",
  "statusField": "status",
  "primaryFields": [
    "severity",
    "source",
    "target",
    "owner"
  ],
  "secondaryFields": [
    "createdAt",
    "updatedAt"
  ]
});
