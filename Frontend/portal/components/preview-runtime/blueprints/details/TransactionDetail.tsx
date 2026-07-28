"use client";

import { createDetailPart } from "../core";

export const TransactionDetail = createDetailPart({
  "title": "Transaction",
  "description": "Ledger attributes, counterparty, amount, and reconciliation.",
  "style": "document",
  "statusField": "status",
  "primaryFields": [
    "reference",
    "amount",
    "currency",
    "status"
  ],
  "secondaryFields": [
    "account",
    "category",
    "occurredAt"
  ]
});
