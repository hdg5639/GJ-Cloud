"use client";

import { createCollectionPart } from "../core";

export const InvoiceCollection = createCollectionPart({
  "title": "Invoice collection",
  "description": "Invoices, due dates, balances, and collection state.",
  "style": "table",
  "primaryField": "number",
  "secondaryField": "customer",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No invoice collection records"
});
