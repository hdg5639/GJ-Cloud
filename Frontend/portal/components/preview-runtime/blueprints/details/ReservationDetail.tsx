"use client";

import { createDetailPart } from "../core";

export const ReservationDetail = createDetailPart({
  "title": "Reservation",
  "description": "Guest, resource, schedule, payment, and policy details.",
  "style": "hero",
  "statusField": "status",
  "primaryFields": [
    "name",
    "resource",
    "startAt",
    "endAt"
  ],
  "secondaryFields": [
    "status",
    "amount"
  ]
});
