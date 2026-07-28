"use client";

import { createFormPart } from "../core";

export const CheckoutAddressForm = createFormPart({
  "title": "Checkout address",
  "description": "Capture delivery and billing contact details.",
  "style": "sectioned",
  "sections": [
    {
      "title": "Contact",
      "description": "Configure contact values.",
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
      "title": "Address",
      "description": "Configure address values.",
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
