"use client";

import { createDashboardPart } from "../core";

export const MarketplaceLiquidityDashboard = createDashboardPart({
  "title": "Marketplace liquidity",
  "description": "Supply, demand, conversion, and payout movement.",
  "eyebrow": "Marketplace",
  "style": "commerce",
  "primaryLabel": "Marketplace movement",
  "secondaryLabel": "Liquidity score",
  "activityLabel": "Recent activity"
});
