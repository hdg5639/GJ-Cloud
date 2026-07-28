"use client";

import { createModalPart } from "../core";

export const GradeSubmissionModal = createModalPart({
  "title": "Submit grade",
  "description": "Review the score and feedback before publishing.",
  "eyebrow": "Assessment",
  "confirmLabel": "Publish grade",
  "style": "review",
  "requireReason": false,
  "fields": [
    {
      "key": "score",
      "label": "Score",
      "type": "number"
    },
    {
      "key": "feedback",
      "label": "Feedback",
      "type": "textarea"
    }
  ]
});
