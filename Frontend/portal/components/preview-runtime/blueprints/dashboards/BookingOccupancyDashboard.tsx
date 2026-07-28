"use client";

import { createDashboardPart } from "../core";

export const BookingOccupancyDashboard = createDashboardPart({
  "title": "Occupancy overview",
  "description": "Availability, utilization, cancellations, and demand.",
  "eyebrow": "Booking",
  "style": "soft",
  "primaryLabel": "Occupancy trend",
  "secondaryLabel": "Capacity score",
  "activityLabel": "Recent activity"
});
