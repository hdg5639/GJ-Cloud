"use client";

import { createDashboardPart } from "../core";

export const BillingRevenueDashboard = createDashboardPart({
  "title": "Revenue operations",
  "description": "Subscriptions, MRR movement, collections, and churn.",
  "eyebrow": "Billing",
  "style": "commerce",
  "primaryLabel": "Revenue movement",
  "secondaryLabel": "Collection health",
  "activityLabel": "Recent activity"
});
