"use client";

import { createDashboardPart } from "../core";

export const EnergyUsageDashboard = createDashboardPart({
  "title": "Energy usage",
  "description": "Consumption, peak demand, and device efficiency.",
  "eyebrow": "Iot",
  "style": "glass",
  "primaryLabel": "Consumption trend",
  "secondaryLabel": "Efficiency score",
  "activityLabel": "Recent activity"
});
