"use client";

import { createDashboardPart } from "../core";

export const LogisticsFleetDashboard = createDashboardPart({
  "title": "Fleet control",
  "description": "Vehicle status, route coverage, and active exceptions.",
  "eyebrow": "Logistics",
  "style": "command",
  "primaryLabel": "Fleet movement",
  "secondaryLabel": "On-time coverage",
  "activityLabel": "Recent activity"
});
