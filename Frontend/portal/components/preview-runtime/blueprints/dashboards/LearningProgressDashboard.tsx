"use client";

import { createDashboardPart } from "../core";

export const LearningProgressDashboard = createDashboardPart({
  "title": "Learning progress",
  "description": "Completion, study activity, and at-risk learners.",
  "eyebrow": "Education",
  "style": "soft",
  "primaryLabel": "Completion trend",
  "secondaryLabel": "Cohort health",
  "activityLabel": "Recent activity"
});
