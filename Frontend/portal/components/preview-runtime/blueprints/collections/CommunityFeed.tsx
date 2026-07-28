"use client";

import { createCollectionPart } from "../core";

export const CommunityFeed = createCollectionPart({
  "title": "Community feed",
  "description": "Posts and conversations ordered by engagement.",
  "style": "cards",
  "primaryField": "title",
  "secondaryField": "content",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No community feed records"
});
