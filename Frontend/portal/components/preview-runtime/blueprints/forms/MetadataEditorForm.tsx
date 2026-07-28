"use client";

import { createFormPart } from "../core";

export const MetadataEditorForm = createFormPart({
  "title": "Metadata editor",
  "description": "Edit descriptive, technical, and rights metadata.",
  "style": "sectioned",
  "sections": [
    {
      "title": "Descriptive",
      "description": "Configure descriptive values.",
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
      "title": "Technical",
      "description": "Configure technical values.",
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
      "title": "Rights",
      "description": "Configure rights values.",
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
