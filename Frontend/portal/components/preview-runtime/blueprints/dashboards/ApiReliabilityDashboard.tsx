"use client";

import { createDashboardPart } from "../core";

export const ApiReliabilityDashboard = createDashboardPart({
  "title": "API reliability",
  "description": "Latency, error rate, availability, and release health.",
  "eyebrow": "Developer",
  "style": "command",
  "primaryLabel": "Reliability trend",
  "secondaryLabel": "Availability",
  "activityLabel": "Recent activity"
});
