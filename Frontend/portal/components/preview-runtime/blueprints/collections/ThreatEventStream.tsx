"use client";

import { createCollectionPart } from "../core";

export const ThreatEventStream = createCollectionPart({
  "title": "Threat event stream",
  "description": "Prioritized security events and analyst triage.",
  "style": "timeline",
  "primaryField": "title",
  "secondaryField": "description",
  "statusField": "severity",
  "actionLabel": "Open",
  "emptyLabel": "No threat event stream records"
});
