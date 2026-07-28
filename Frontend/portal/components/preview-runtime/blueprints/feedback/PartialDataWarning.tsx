"use client";

import { createFeedbackPart } from "../core";

export const PartialDataWarning = createFeedbackPart({
  "title": "Partial data available",
  "description": "Some sources failed, so this view may be incomplete.",
  "style": "warning",
  "actionLabel": "Review sources"
});
