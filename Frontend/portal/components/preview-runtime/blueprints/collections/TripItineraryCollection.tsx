"use client";

import { createCollectionPart } from "../core";

export const TripItineraryCollection = createCollectionPart({
  "title": "Trip itinerary",
  "description": "Ordered travel segments, reservations, and activities.",
  "style": "timeline",
  "primaryField": "title",
  "secondaryField": "location",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No trip itinerary records"
});
