"use client";

import { createFormPart } from "../core";

export const SecretReferenceForm = createFormPart({
  "title": "Secret reference",
  "description": "Select secret references without exposing secret values.",
  "style": "preferences",
  "sections": [
    {
      "title": "Reference",
      "description": "Configure reference values.",
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
      "title": "Scope",
      "description": "Configure scope values.",
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
