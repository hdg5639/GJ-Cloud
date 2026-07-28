"use client";

import { createCollectionPart } from "../core";

export const WarehouseBinExplorer = createCollectionPart({
  "title": "Warehouse bin explorer",
  "description": "Hierarchical zones, aisles, racks, and bins.",
  "style": "tree",
  "primaryField": "name",
  "secondaryField": "location",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No warehouse bin explorer records"
});
