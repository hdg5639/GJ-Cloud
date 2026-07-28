"use client";

import { createFormPart } from "../core";

export const NotificationPreferenceForm = createFormPart({
  "title": "Notification preferences",
  "description": "Choose channels, frequency, and event categories.",
  "style": "preferences",
  "sections": [
    {
      "title": "Channels",
      "description": "Configure channels values.",
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
      "title": "Events",
      "description": "Configure events values.",
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
      "title": "Quiet hours",
      "description": "Configure quiet hours values.",
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
