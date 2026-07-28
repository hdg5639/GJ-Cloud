"use client";

import { createDetailPart } from "../core";

export const VendorDetail = createDetailPart({
  "title": "Vendor",
  "description": "Catalog, performance, reviews, disputes, and payout status.",
  "style": "commerce",
  "statusField": "status",
  "primaryFields": [
    "name",
    "category",
    "rating",
    "status"
  ],
  "secondaryFields": [
    "sales",
    "payoutStatus"
  ]
});
