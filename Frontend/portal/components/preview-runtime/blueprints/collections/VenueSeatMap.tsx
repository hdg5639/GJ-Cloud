"use client";

import { createCollectionPart } from "../core";

export const VenueSeatMap = createCollectionPart({
  "title": "Venue seat map",
  "description": "Interactive seat or zone status across the venue.",
  "style": "map",
  "primaryField": "seat",
  "secondaryField": "section",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No venue seat map records"
});
