"use client";

import { createCollectionPart } from "../core";

export const CustomerHealthBoard = createCollectionPart({
  "title": "Customer health board",
  "description": "Accounts grouped by health and renewal posture.",
  "style": "board",
  "primaryField": "account",
  "secondaryField": "summary",
  "statusField": "health",
  "actionLabel": "Open",
  "emptyLabel": "No customer health board records"
});
