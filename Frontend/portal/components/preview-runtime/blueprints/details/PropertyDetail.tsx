"use client";

import { createDetailPart } from "../core";

export const PropertyDetail = createDetailPart({
  "title": "Property",
  "description": "Listing, occupancy, financial, maintenance, and tenant context.",
  "style": "commerce",
  "statusField": "status",
  "primaryFields": [
    "title",
    "location",
    "price",
    "status"
  ],
  "secondaryFields": [
    "occupancy",
    "owner"
  ]
});
