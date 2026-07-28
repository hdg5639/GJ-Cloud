"use client";

import { createModalPart } from "../core";

export const PropertyInquiryModal = createModalPart({
  "title": "Property inquiry",
  "description": "Capture contact and viewing preferences.",
  "eyebrow": "Property inquiry",
  "confirmLabel": "Submit inquiry",
  "style": "form",
  "requireReason": false,
  "fields": [
    {
      "key": "name",
      "label": "Name",
      "type": "text"
    },
    {
      "key": "email",
      "label": "Email",
      "type": "text"
    },
    {
      "key": "message",
      "label": "Message",
      "type": "textarea"
    }
  ]
});
