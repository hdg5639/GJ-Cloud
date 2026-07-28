"use client";

import { createCollectionPart } from "../core";

export const ShipmentTrackingBoard = createCollectionPart({
  "title": "Shipment tracking",
  "description": "Shipments grouped by delivery stage.",
  "style": "board",
  "primaryField": "trackingNumber",
  "secondaryField": "destination",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No shipment tracking records"
});
