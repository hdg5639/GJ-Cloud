"use client";

import { createDashboardPart } from "../core";

export const EventAttendanceDashboard = createDashboardPart({
  "title": "Attendance control",
  "description": "Registrations, check-ins, capacity, and engagement.",
  "eyebrow": "Events",
  "style": "neon",
  "primaryLabel": "Attendance curve",
  "secondaryLabel": "Venue fill",
  "activityLabel": "Recent activity"
});
