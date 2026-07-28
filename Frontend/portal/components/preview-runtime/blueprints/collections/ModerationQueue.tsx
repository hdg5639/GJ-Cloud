"use client";

import { createCollectionPart } from "../core";

export const ModerationQueue = createCollectionPart({
  "title": "Moderation queue",
  "description": "Reported content awaiting review and action.",
  "style": "inbox",
  "primaryField": "title",
  "secondaryField": "reason",
  "statusField": "severity",
  "actionLabel": "Open",
  "emptyLabel": "No moderation queue records"
});
