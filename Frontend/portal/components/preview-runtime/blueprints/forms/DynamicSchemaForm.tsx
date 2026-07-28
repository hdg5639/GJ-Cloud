"use client";

import { createFormPart } from "../core";

export const DynamicSchemaForm = createFormPart({
  "title": "Dynamic schema form",
  "description": "Render a flexible configuration form from semantic fields.",
  "style": "schema",
  "sections": [
    {
      "title": "General",
      "description": "Configure general values.",
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
      "title": "Advanced",
      "description": "Configure advanced values.",
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
