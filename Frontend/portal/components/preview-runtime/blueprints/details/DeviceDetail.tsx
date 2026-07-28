"use client";

import { createDetailPart } from "../core";

export const DeviceDetail = createDetailPart({
  "title": "Device",
  "description": "Connectivity, firmware, telemetry, commands, and alerts.",
  "style": "technical",
  "statusField": "status",
  "primaryFields": [
    "name",
    "deviceId",
    "firmware",
    "status"
  ],
  "secondaryFields": [
    "lastSeenAt",
    "battery"
  ]
});
