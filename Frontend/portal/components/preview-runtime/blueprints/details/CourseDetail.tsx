"use client";

import { createDetailPart } from "../core";

export const CourseDetail = createDetailPart({
  "title": "Course",
  "description": "Curriculum, instructor, enrollment, and learning outcomes.",
  "style": "document",
  "statusField": "status",
  "primaryFields": [
    "title",
    "level",
    "duration",
    "instructor"
  ],
  "secondaryFields": [
    "status",
    "category"
  ]
});
