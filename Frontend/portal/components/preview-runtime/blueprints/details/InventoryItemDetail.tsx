"use client";

import { createDetailPart } from "../core";

export const InventoryItemDetail = createDetailPart({
  "title": "Inventory item",
  "description": "Stock, locations, suppliers, and replenishment controls.",
  "style": "split",
  "statusField": "status",
  "primaryFields": [
    "sku",
    "name",
    "quantity",
    "reorderPoint"
  ],
  "secondaryFields": [
    "warehouse",
    "supplier"
  ]
});
