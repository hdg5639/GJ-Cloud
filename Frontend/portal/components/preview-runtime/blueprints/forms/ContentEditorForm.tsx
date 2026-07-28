"use client";

import { createFormPart } from "../core";

export const ContentEditorForm = createFormPart({
  "title": "Content editor",
  "description": "Author structured content and publishing metadata.",
  "style": "composer",
  "sections": [
    {
      "title": "Content",
      "description": "Configure content values.",
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
      "title": "Publishing",
      "description": "Configure publishing values.",
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
