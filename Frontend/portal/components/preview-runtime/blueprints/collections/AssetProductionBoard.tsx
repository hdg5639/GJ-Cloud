"use client";

import { createCollectionPart } from "../core";

export const AssetProductionBoard = createCollectionPart({
  "title": "Asset production board",
  "description": "Media assets grouped by production and review stage.",
  "style": "board",
  "primaryField": "title",
  "secondaryField": "format",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No asset production board records"
});
