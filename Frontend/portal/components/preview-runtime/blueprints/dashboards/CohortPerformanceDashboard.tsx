"use client";

import { createDashboardPart } from "../core";

export const CohortPerformanceDashboard = createDashboardPart({
  "title": "Cohort performance",
  "description": "Compare outcomes, activity, and assessment signals.",
  "eyebrow": "Education",
  "style": "editorial",
  "primaryLabel": "Cohort movement",
  "secondaryLabel": "Achievement score",
  "activityLabel": "Recent activity"
});
