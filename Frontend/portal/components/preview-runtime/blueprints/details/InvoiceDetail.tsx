"use client";

import { createDetailPart } from "../core";

export const InvoiceDetail = createDetailPart({
  "title": "Invoice",
  "description": "Customer, line items, balance, collection, and payment status.",
  "style": "commerce",
  "statusField": "status",
  "primaryFields": [
    "number",
    "customer",
    "amount",
    "dueDate"
  ],
  "secondaryFields": [
    "status",
    "currency"
  ]
});
