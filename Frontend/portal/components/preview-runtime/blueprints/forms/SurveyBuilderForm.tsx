"use client";

import { createFormPart } from "../core";

export const SurveyBuilderForm = createFormPart({
  "title": "Survey builder",
  "description": "Compose questions, options, and collection behavior.",
  "style": "builder",
  "sections": [
    {
      "title": "Survey",
      "description": "Configure survey values.",
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
      "title": "Questions",
      "description": "Configure questions values.",
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
      "title": "Delivery",
      "description": "Configure delivery values.",
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
