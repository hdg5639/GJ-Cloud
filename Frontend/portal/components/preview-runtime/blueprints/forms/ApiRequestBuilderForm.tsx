"use client";

import { createFormPart } from "../core";

export const ApiRequestBuilderForm = createFormPart({
  "title": "API request builder",
  "description": "Build a controlled API request using declared parameters.",
  "style": "schema",
  "sections": [
    {
      "title": "Parameters",
      "description": "Configure parameters values.",
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
      "title": "Body",
      "description": "Configure body values.",
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
      "title": "Headers",
      "description": "Configure headers values.",
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
