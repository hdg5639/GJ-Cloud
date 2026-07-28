"use client";

import { createDashboardPart } from "../core";

export const DeliveryPerformanceDashboard = createDashboardPart({
  "title": "Delivery performance",
  "description": "On-time rate, failed stops, and regional performance.",
  "eyebrow": "Logistics",
  "style": "enterprise",
  "primaryLabel": "Delivery trend",
  "secondaryLabel": "Exception rate",
  "activityLabel": "Recent activity"
});
