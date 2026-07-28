"use client";

import { createCollectionPart } from "../core";

export const SupportTicketInbox = createCollectionPart({
  "title": "Support ticket inbox",
  "description": "Customer conversations ordered by priority and SLA.",
  "style": "inbox",
  "primaryField": "subject",
  "secondaryField": "preview",
  "statusField": "priority",
  "actionLabel": "Open",
  "emptyLabel": "No support ticket inbox records"
});
