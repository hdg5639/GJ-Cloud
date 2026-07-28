"use client";

import { createCollectionPart } from "../core";

export const RouteStopTimeline = createCollectionPart({
  "title": "Route stop timeline",
  "description": "Ordered stops, ETA, completion, and exceptions.",
  "style": "timeline",
  "primaryField": "location",
  "secondaryField": "notes",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No route stop timeline records"
});
