"use client";

import { createFeedbackPart } from "../core";

export const OperationFailedState = createFeedbackPart({
  "title": "Operation failed",
  "description": "Review the error details and retry when the issue is resolved.",
  "style": "error",
  "actionLabel": "Retry"
});
