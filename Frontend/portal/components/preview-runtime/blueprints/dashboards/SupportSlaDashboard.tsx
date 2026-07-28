"use client";

import { createDashboardPart } from "../core";

export const SupportSlaDashboard = createDashboardPart({
  "title": "Support SLA",
  "description": "Queue pressure, response time, and SLA performance.",
  "eyebrow": "Support",
  "style": "enterprise",
  "primaryLabel": "SLA movement",
  "secondaryLabel": "At-risk queue",
  "activityLabel": "Recent activity"
});
