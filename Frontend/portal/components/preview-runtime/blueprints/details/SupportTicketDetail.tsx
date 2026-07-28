"use client";

import { createDetailPart } from "../core";

export const SupportTicketDetail = createDetailPart({
  "title": "Support ticket",
  "description": "Conversation, customer context, SLA, and resolution.",
  "style": "timeline",
  "statusField": "status",
  "primaryFields": [
    "subject",
    "priority",
    "customer",
    "assignee"
  ],
  "secondaryFields": [
    "createdAt",
    "slaDueAt"
  ]
});
