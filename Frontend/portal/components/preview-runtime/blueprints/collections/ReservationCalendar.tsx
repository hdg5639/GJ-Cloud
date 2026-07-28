"use client";

import { createCollectionPart } from "../core";

export const ReservationCalendar = createCollectionPart({
  "title": "Reservation calendar",
  "description": "Reservations distributed across dates and capacity.",
  "style": "calendar",
  "primaryField": "name",
  "secondaryField": "resource",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No reservation calendar records"
});
