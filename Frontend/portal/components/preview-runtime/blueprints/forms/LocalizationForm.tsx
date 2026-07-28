"use client";

import { createFormPart } from "../core";

export const LocalizationForm = createFormPart({
  "title": "Localization editor",
  "description": "Manage translated strings and locale metadata.",
  "style": "sectioned",
  "sections": [
    {
      "title": "Locale",
      "description": "Configure locale values.",
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
      "title": "Strings",
      "description": "Configure strings values.",
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
