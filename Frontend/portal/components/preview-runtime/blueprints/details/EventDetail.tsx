"use client";

import { createDetailPart } from "../core";

export const EventDetail = createDetailPart({
  "title": "Event",
  "description": "Venue, schedule, capacity, attendance, and operational status.",
  "style": "hero",
  "statusField": "status",
  "primaryFields": [
    "title",
    "venue",
    "startAt",
    "capacity"
  ],
  "secondaryFields": [
    "status",
    "organizer"
  ]
});
