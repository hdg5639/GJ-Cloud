"use client";

import { createFeedbackPart } from "../core";

export const SearchNoResultsState = createFeedbackPart({
  "title": "No matching results",
  "description": "Adjust the search term or remove one of the active filters.",
  "style": "empty",
  "actionLabel": "Clear filters"
});
