"use client";

import { createFeedbackPart } from "../core";

export const ImportSuccessState = createFeedbackPart({
  "title": "Import complete",
  "description": "The records were validated and added successfully.",
  "style": "success",
  "actionLabel": "View records"
});
