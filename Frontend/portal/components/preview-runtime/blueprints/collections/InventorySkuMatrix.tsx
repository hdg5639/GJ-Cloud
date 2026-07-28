"use client";

import { createCollectionPart } from "../core";

export const InventorySkuMatrix = createCollectionPart({
  "title": "SKU matrix",
  "description": "Inventory quantities, locations, reorder points, and status.",
  "style": "matrix",
  "primaryField": "sku",
  "secondaryField": "name",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No sku matrix records"
});
