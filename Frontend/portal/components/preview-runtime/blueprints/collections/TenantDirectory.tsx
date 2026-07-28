"use client";

import { createCollectionPart } from "../core";

export const TenantDirectory = createCollectionPart({
  "title": "Tenant directory",
  "description": "Tenants, units, lease dates, and account status.",
  "style": "table",
  "primaryField": "name",
  "secondaryField": "unit",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No tenant directory records"
});
