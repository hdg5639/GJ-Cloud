"use client";

import { createCollectionPart } from "../core";

export const PropertyListingGrid = createCollectionPart({
  "title": "Property listings",
  "description": "Listings with media, price, occupancy, and status.",
  "style": "gallery",
  "primaryField": "title",
  "secondaryField": "location",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No property listings records"
});
