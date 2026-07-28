"use client";

import { createDashboardPart } from "../core";

export const IoTDeviceFleetDashboard = createDashboardPart({
  "title": "Device fleet",
  "description": "Connectivity, firmware, battery, and alert posture.",
  "eyebrow": "Iot",
  "style": "command",
  "primaryLabel": "Fleet activity",
  "secondaryLabel": "Online coverage",
  "activityLabel": "Recent activity"
});
