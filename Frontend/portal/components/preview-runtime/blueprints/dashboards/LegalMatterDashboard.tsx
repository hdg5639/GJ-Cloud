"use client";

import { createDashboardPart } from "../core";

export const LegalMatterDashboard = createDashboardPart({
  "title": "Matter portfolio",
  "description": "Open matters, deadlines, risk, and review workload.",
  "eyebrow": "Legal",
  "style": "paper",
  "primaryLabel": "Matter movement",
  "secondaryLabel": "Risk coverage",
  "activityLabel": "Recent activity"
});
