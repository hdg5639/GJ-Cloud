"use client";

import { createDashboardPart } from "../core";

export const WarehouseCapacityDashboard = createDashboardPart({
  "title": "Warehouse capacity",
  "description": "Storage utilization, labor load, and bin pressure.",
  "eyebrow": "Inventory",
  "style": "command",
  "primaryLabel": "Capacity trend",
  "secondaryLabel": "Space remaining",
  "activityLabel": "Recent activity"
});
