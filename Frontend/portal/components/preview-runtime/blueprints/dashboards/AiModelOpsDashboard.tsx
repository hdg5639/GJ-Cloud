"use client";

import { createDashboardPart } from "../core";

export const AiModelOpsDashboard = createDashboardPart({
  "title": "Model operations",
  "description": "Quality, latency, cost, and deployment status.",
  "eyebrow": "Ai",
  "style": "neon",
  "primaryLabel": "Model performance",
  "secondaryLabel": "Quality score",
  "activityLabel": "Recent activity"
});
