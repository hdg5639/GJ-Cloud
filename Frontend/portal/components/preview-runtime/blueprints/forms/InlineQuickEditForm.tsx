"use client";

import { createFormPart } from "../core";

export const InlineQuickEditForm = createFormPart({
  "title": "Inline quick edit",
  "description": "Compact form for fast resource updates.",
  "style": "inline",
  "sections": [
    {
      "title": "Quick edit",
      "description": "Configure quick edit values.",
      "fields": [
        {
          "key": "name",
          "label": "Name",
          "type": "text",
          "placeholder": "Enter a name"
        },
        {
          "key": "status",
          "label": "Status",
          "type": "select",
          "options": [
            "Active",
            "Draft",
            "Disabled"
          ]
        }
      ]
    }
  ]
});
