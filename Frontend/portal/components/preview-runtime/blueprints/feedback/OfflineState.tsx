"use client";

import { createFeedbackPart } from "../core";

export const OfflineState = createFeedbackPart({
  "title": "You are offline",
  "description": "Reconnect to continue working with live data.",
  "style": "offline",
  "actionLabel": "Retry"
});
