"use client";

import { createFeedbackPart } from "../core";

export const DataLoadErrorState = createFeedbackPart({
  "title": "Unable to load data",
  "description": "The request failed before the current view could be rendered.",
  "style": "error",
  "actionLabel": "Retry"
});
