"use client";

import { createFeedbackPart } from "../core";

export const SkeletonDashboardState = createFeedbackPart({
  "title": "Loading dashboard",
  "description": "Metrics and activity are being prepared.",
  "style": "loading"
});
