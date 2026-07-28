"use client";

import { createFeedbackPart } from "../core";

export const MaintenanceState = createFeedbackPart({
  "title": "Temporarily unavailable",
  "description": "This workspace is undergoing scheduled maintenance.",
  "style": "maintenance",
  "actionLabel": "View status"
});
