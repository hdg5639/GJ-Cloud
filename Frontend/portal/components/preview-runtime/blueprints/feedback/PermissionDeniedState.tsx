"use client";

import { createFeedbackPart } from "../core";

export const PermissionDeniedState = createFeedbackPart({
  "title": "Access restricted",
  "description": "Your current role does not allow this operation.",
  "style": "permission",
  "actionLabel": "Request access"
});
