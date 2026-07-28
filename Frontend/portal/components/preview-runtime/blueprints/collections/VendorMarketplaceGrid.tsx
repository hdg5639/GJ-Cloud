"use client";

import { createCollectionPart } from "../core";

export const VendorMarketplaceGrid = createCollectionPart({
  "title": "Vendor marketplace",
  "description": "Vendor offers, categories, ratings, and availability.",
  "style": "cards",
  "primaryField": "name",
  "secondaryField": "category",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No vendor marketplace records"
});
