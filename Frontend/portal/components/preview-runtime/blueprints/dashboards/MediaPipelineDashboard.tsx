"use client";

import { createDashboardPart } from "../core";

export const MediaPipelineDashboard = createDashboardPart({
  "title": "Media pipeline",
  "description": "Asset throughput, reviews, publishing, and channel health.",
  "eyebrow": "Media",
  "style": "neon",
  "primaryLabel": "Production movement",
  "secondaryLabel": "Publish readiness",
  "activityLabel": "Recent activity"
});
