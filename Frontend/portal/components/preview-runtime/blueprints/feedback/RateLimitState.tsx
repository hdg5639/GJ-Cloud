"use client";

import { createFeedbackPart } from "../core";

export const RateLimitState = createFeedbackPart({
  "title": "Rate limit reached",
  "description": "Wait for the quota window to reset or adjust the plan.",
  "style": "warning",
  "actionLabel": "View usage"
});
