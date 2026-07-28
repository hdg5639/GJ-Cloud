"use client";

import { createDashboardPart } from "../core";

export const DeveloperUsageDashboard = createDashboardPart({
  "title": "Developer usage",
  "description": "API consumers, request volume, keys, and adoption.",
  "eyebrow": "Developer",
  "style": "terminal",
  "primaryLabel": "Usage movement",
  "secondaryLabel": "Adoption score",
  "activityLabel": "Recent activity"
});
