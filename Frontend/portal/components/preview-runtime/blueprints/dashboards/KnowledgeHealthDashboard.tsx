"use client";

import { createDashboardPart } from "../core";

export const KnowledgeHealthDashboard = createDashboardPart({
  "title": "Knowledge health",
  "description": "Coverage, freshness, searches, and unresolved gaps.",
  "eyebrow": "Knowledge",
  "style": "editorial",
  "primaryLabel": "Knowledge activity",
  "secondaryLabel": "Freshness score",
  "activityLabel": "Recent activity"
});
