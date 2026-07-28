"use client";

import { createCollectionPart } from "../core";

export const CourseCatalogGrid = createCollectionPart({
  "title": "Course catalog",
  "description": "Discover courses by subject, level, and availability.",
  "style": "cards",
  "primaryField": "title",
  "secondaryField": "summary",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No course catalog records"
});
