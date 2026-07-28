"use client";

import { createFormPart } from "../core";

export const SupportReplyComposer = createFormPart({
  "title": "Support reply composer",
  "description": "Compose a structured customer response with channel controls.",
  "style": "composer",
  "sections": [
    {
      "title": "Reply",
      "description": "Configure reply values.",
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
    },
    {
      "title": "Delivery",
      "description": "Configure delivery values.",
      "fields": [
        {
          "key": "description",
          "label": "Description",
          "type": "textarea",
          "placeholder": "Describe this configuration"
        },
        {
          "key": "enabled",
          "label": "Enabled",
          "type": "toggle"
        }
      ]
    }
  ]
});
