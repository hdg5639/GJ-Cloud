"use client";

import { createDashboardPart } from "../core";

export const PropertyOccupancyDashboard = createDashboardPart({
  "title": "Occupancy control",
  "description": "Units, leases, vacancy, and upcoming turnover.",
  "eyebrow": "Real Estate",
  "style": "enterprise",
  "primaryLabel": "Occupancy movement",
  "secondaryLabel": "Lease coverage",
  "activityLabel": "Recent activity"
});
