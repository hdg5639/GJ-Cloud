"use client";

import { createCollectionPart } from "../core";

export const LearnerRoster = createCollectionPart({
  "title": "Learner roster",
  "description": "Learners, progress, cohort, and engagement.",
  "style": "table",
  "primaryField": "name",
  "secondaryField": "cohort",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No learner roster records"
});
