"use client";

import { createFeedbackPart } from "../core";

export const UnsavedChangesBanner = createFeedbackPart({
  "title": "Unsaved changes",
  "description": "Save or discard changes before leaving this page.",
  "style": "warning",
  "actionLabel": "Save"
});
