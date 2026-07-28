"use client";

import { createDashboardPart } from "../core";

export const TravelOperationsDashboard = createDashboardPart({
  "title": "Travel operations",
  "description": "Trips, disruptions, bookings, and traveler assistance.",
  "eyebrow": "Travel",
  "style": "glass",
  "primaryLabel": "Trip movement",
  "secondaryLabel": "On-time score",
  "activityLabel": "Recent activity"
});
