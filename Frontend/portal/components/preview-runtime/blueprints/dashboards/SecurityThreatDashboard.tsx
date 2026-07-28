"use client";

import { createDashboardPart } from "../core";

export const SecurityThreatDashboard = createDashboardPart({
  "title": "Threat overview",
  "description": "Current threat volume, severity, and response posture.",
  "eyebrow": "Security",
  "style": "command",
  "primaryLabel": "Threat movement",
  "secondaryLabel": "Risk posture",
  "activityLabel": "Recent activity"
});
