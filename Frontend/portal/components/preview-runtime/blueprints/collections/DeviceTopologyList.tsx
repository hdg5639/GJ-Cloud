"use client";

import { createCollectionPart } from "../core";

export const DeviceTopologyList = createCollectionPart({
  "title": "Device topology",
  "description": "Hierarchical gateways, devices, and sensors.",
  "style": "tree",
  "primaryField": "name",
  "secondaryField": "type",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No device topology records"
});
