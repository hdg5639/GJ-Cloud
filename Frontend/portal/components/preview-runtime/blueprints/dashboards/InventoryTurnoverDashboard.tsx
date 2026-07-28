"use client";

import { createDashboardPart } from "../core";

export const InventoryTurnoverDashboard = createDashboardPart({
  "title": "Inventory turnover",
  "description": "Sell-through, aging stock, and replenishment demand.",
  "eyebrow": "Inventory",
  "style": "dense",
  "primaryLabel": "Stock movement",
  "secondaryLabel": "Replenishment score",
  "activityLabel": "Recent activity"
});
