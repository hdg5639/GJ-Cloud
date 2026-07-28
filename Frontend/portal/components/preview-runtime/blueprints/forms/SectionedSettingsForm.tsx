"use client";

import { createFormPart } from "../core";

export const SectionedSettingsForm = createFormPart({
  "title": "Sectioned settings",
  "description": "Organize project settings into stable sections.",
  "style": "sectioned",
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
      "title": "Behavior",
      "description": "Configure behavior values.",
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
    },
    {
      "title": "Security",
      "description": "Configure security values.",
      "fields": [
        {
          "key": "owner",
          "label": "Owner",
          "type": "text"
        },
        {
          "key": "effectiveDate",
          "label": "Effective date",
          "type": "date"
        }
      ]
    }
  ]
});
