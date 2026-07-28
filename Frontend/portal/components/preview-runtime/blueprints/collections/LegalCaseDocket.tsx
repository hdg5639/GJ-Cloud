"use client";

import { createCollectionPart } from "../core";

export const LegalCaseDocket = createCollectionPart({
  "title": "Case docket",
  "description": "Hearings, filings, deadlines, and legal events.",
  "style": "timeline",
  "primaryField": "title",
  "secondaryField": "description",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No case docket records"
});
