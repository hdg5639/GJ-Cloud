"use client";

import { createDashboardPart } from "../core";

export const SocOverviewDashboard = createDashboardPart({
  "title": "SOC overview",
  "description": "Operational security signals for analysts and leads.",
  "eyebrow": "Security",
  "style": "terminal",
  "primaryLabel": "Active investigations",
  "secondaryLabel": "Detection coverage",
  "activityLabel": "Recent activity"
});
