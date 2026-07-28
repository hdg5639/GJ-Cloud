"use client";

import { createCollectionPart } from "../core";

export const TransactionLedger = createCollectionPart({
  "title": "Transaction ledger",
  "description": "Detailed movement of money across accounts and categories.",
  "style": "table",
  "primaryField": "reference",
  "secondaryField": "description",
  "statusField": "status",
  "actionLabel": "Open",
  "emptyLabel": "No transaction ledger records"
});
