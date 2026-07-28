"use client";

import { createCollectionPart } from "../core";

export const ApiEndpointCatalog = createCollectionPart({
  "title": "API endpoint catalog",
  "description": "Operations grouped for discoverability and testing.",
  "style": "tree",
  "primaryField": "operationId",
  "secondaryField": "path",
  "statusField": "method",
  "actionLabel": "Open",
  "emptyLabel": "No api endpoint catalog records"
});
