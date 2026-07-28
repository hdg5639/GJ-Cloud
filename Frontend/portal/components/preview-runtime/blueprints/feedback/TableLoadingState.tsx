"use client";

import { createFeedbackPart } from "../core";

export const TableLoadingState = createFeedbackPart({
  "title": "Loading records",
  "description": "The collection is being synchronized.",
  "style": "loading"
});
