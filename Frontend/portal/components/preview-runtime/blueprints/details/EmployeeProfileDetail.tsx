"use client";

import { createDetailPart } from "../core";

export const EmployeeProfileDetail = createDetailPart({
  "title": "Employee profile",
  "description": "Role, organization, lifecycle, requests, and activity.",
  "style": "profile",
  "statusField": "status",
  "primaryFields": [
    "name",
    "role",
    "team",
    "status"
  ],
  "secondaryFields": [
    "manager",
    "startDate"
  ]
});
