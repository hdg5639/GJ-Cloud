"use client";

import { createModalPart } from "../core";

export const EnrollLearnerModal = createModalPart({
  "title": "Enroll learner",
  "description": "Add the learner to a course or cohort.",
  "eyebrow": "Enrollment",
  "confirmLabel": "Enroll",
  "style": "form",
  "requireReason": false,
  "fields": [
    {
      "key": "course",
      "label": "Course",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    },
    {
      "key": "cohort",
      "label": "Cohort",
      "type": "select",
      "options": [
        "Option A",
        "Option B",
        "Option C"
      ]
    }
  ]
});
