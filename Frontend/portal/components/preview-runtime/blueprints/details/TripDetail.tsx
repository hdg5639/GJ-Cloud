"use client";

import { createDetailPart } from "../core";

export const TripDetail = createDetailPart({
  "title": "Trip",
  "description": "Itinerary, reservations, travelers, documents, and disruptions.",
  "style": "timeline",
  "statusField": "status",
  "primaryFields": [
    "title",
    "destination",
    "startAt",
    "endAt"
  ],
  "secondaryFields": [
    "status",
    "travelerCount"
  ]
});
