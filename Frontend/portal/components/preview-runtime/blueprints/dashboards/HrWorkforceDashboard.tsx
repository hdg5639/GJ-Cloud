"use client";

import { createDashboardPart } from "../core";

export const HrWorkforceDashboard = createDashboardPart({
  "title": "Workforce overview",
  "description": "Headcount, retention, attendance, and organizational health.",
  "eyebrow": "Hr",
  "style": "enterprise",
  "primaryLabel": "Workforce movement",
  "secondaryLabel": "Retention score",
  "activityLabel": "Recent activity"
});
