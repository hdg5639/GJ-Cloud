"use client";

import { createCollectionPart } from "../core";

export const PromptLibraryGrid = createCollectionPart({
  "title": "Prompt library",
  "description": "Reusable prompts organized by task and quality.",
  "style": "cards",
  "primaryField": "name",
  "secondaryField": "description",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No prompt library records"
});
