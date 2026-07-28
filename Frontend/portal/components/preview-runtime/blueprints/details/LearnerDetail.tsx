"use client";

import { createDetailPart } from "../core";

export const LearnerDetail = createDetailPart({
  "title": "Learner profile",
  "description": "Progress, activity, assessments, and support context.",
  "style": "profile",
  "statusField": "status",
  "primaryFields": [
    "name",
    "cohort",
    "progress",
    "status"
  ],
  "secondaryFields": [
    "email",
    "lastActiveAt"
  ]
});
