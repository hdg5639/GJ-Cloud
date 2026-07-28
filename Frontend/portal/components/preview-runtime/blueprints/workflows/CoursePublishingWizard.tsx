"use client";

import { createWorkflowPart } from "../core";

export const CoursePublishingWizard = createWorkflowPart({
  "title": "Course publishing",
  "description": "Build course structure, review content, and publish enrollment.",
  "style": "wizard",
  "steps": [
    {
      "id": "course details",
      "label": "Course details",
      "description": "Complete the course details stage with validated inputs."
    },
    {
      "id": "curriculum",
      "label": "Curriculum",
      "description": "Complete the curriculum stage with validated inputs."
    },
    {
      "id": "assessments",
      "label": "Assessments",
      "description": "Complete the assessments stage with validated inputs."
    },
    {
      "id": "access policy",
      "label": "Access policy",
      "description": "Complete the access policy stage with validated inputs."
    },
    {
      "id": "publish",
      "label": "Publish",
      "description": "Complete the publish stage with validated inputs."
    }
  ],
  "completeLabel": "Publish course"
});
