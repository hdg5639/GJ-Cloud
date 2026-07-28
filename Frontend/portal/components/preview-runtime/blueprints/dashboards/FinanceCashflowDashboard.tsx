"use client";

import { createDashboardPart } from "../core";

export const FinanceCashflowDashboard = createDashboardPart({
  "title": "Cashflow command",
  "description": "Cash position, inflow, outflow, and forecast variance.",
  "eyebrow": "Finance",
  "style": "editorial",
  "primaryLabel": "Cash movement",
  "secondaryLabel": "Forecast confidence",
  "activityLabel": "Recent activity"
});
