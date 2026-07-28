"use client";

import { createCollectionPart } from "../core";

export const SensorReadingTable = createCollectionPart({
  "title": "Sensor readings",
  "description": "Latest telemetry values, units, and alert state.",
  "style": "table",
  "primaryField": "sensor",
  "secondaryField": "value",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No sensor readings records"
});
