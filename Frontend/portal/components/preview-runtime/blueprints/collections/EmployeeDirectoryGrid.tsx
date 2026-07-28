"use client";

import { createCollectionPart } from "../core";

export const EmployeeDirectoryGrid = createCollectionPart({
  "title": "Employee directory",
  "description": "People, teams, roles, and availability.",
  "style": "cards",
  "primaryField": "name",
  "secondaryField": "role",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No employee directory records"
});
