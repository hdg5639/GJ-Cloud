"use client";

import { createDetailPart } from "../core";

export const ShipmentDetail = createDetailPart({
  "title": "Shipment",
  "description": "Route, packages, stops, tracking, and delivery exceptions.",
  "style": "timeline",
  "statusField": "status",
  "primaryFields": [
    "trackingNumber",
    "carrier",
    "origin",
    "destination"
  ],
  "secondaryFields": [
    "eta",
    "status"
  ]
});
