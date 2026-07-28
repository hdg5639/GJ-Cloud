"use client";

import { createCollectionPart } from "../core";

export const ModelRegistryCollection = createCollectionPart({
  "title": "Model registry",
  "description": "Models, versions, stages, quality, and ownership.",
  "style": "table",
  "primaryField": "name",
  "secondaryField": "version",
  "statusField": "stage",
  "actionLabel": "Open",
  "emptyLabel": "No model registry records"
});
