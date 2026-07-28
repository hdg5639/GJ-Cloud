"use client";

import { createFormPart } from "../core";

export const ScheduleRuleForm = createFormPart({
  "title": "Schedule rule",
  "description": "Configure date, recurrence, timezone, and activation.",
  "style": "sectioned",
  "sections": [
    {
      "title": "Schedule",
      "description": "Configure schedule values.",
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
      "title": "Recurrence",
      "description": "Configure recurrence values.",
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
