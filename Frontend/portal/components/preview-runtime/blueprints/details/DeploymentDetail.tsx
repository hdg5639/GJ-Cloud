"use client";

import { createDetailPart } from "../core";

export const DeploymentDetail = createDetailPart({
  "title": "Deployment",
  "description": "Version, environment, changes, rollout, and health.",
  "style": "technical",
  "statusField": "status",
  "primaryFields": [
    "service",
    "version",
    "environment",
    "status"
  ],
  "secondaryFields": [
    "startedAt",
    "finishedAt"
  ]
});
