"use client";

import { createCollectionPart } from "../core";

export const DeploymentEnvironmentMatrix = createCollectionPart({
  "title": "Environment matrix",
  "description": "Services and versions across deployment environments.",
  "style": "matrix",
  "primaryField": "service",
  "secondaryField": "version",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No environment matrix records"
});
