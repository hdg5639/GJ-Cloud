"use client";

import { createDashboardPart } from "../core";

export const RealEstatePortfolioDashboard = createDashboardPart({
  "title": "Portfolio overview",
  "description": "Occupancy, revenue, maintenance, and listing health.",
  "eyebrow": "Real Estate",
  "style": "editorial",
  "primaryLabel": "Portfolio movement",
  "secondaryLabel": "Occupancy score",
  "activityLabel": "Recent activity"
});
