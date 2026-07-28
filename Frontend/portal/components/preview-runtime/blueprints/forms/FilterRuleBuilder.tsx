"use client";

import { createFormPart } from "../core";

export const FilterRuleBuilder = createFormPart({
  "title": "Filter rule builder",
  "description": "Create reusable condition groups without arbitrary code.",
  "style": "builder",
  "sections": [
    {
      "title": "Conditions",
      "description": "Configure conditions values.",
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
      "title": "Actions",
      "description": "Configure actions values.",
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
