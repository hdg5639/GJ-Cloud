"use client";

import { createDashboardPart } from "../core";

export const PromptAnalyticsDashboard = createDashboardPart({
  "title": "Prompt analytics",
  "description": "Usage, success, cost, and experiment outcomes.",
  "eyebrow": "Ai",
  "style": "glass",
  "primaryLabel": "Prompt activity",
  "secondaryLabel": "Success rate",
  "activityLabel": "Recent activity"
});
