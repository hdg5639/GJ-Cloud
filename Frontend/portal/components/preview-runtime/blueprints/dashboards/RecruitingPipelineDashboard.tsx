"use client";

import { createDashboardPart } from "../core";

export const RecruitingPipelineDashboard = createDashboardPart({
  "title": "Recruiting pipeline",
  "description": "Candidate flow, time-to-hire, and stage conversion.",
  "eyebrow": "Hr",
  "style": "soft",
  "primaryLabel": "Pipeline movement",
  "secondaryLabel": "Hiring velocity",
  "activityLabel": "Recent activity"
});
