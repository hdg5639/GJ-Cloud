"use client";

import { createWorkflowPart } from "../core";

export const PropertyListingWizard = createWorkflowPart({
  "title": "Property listing",
  "description": "Prepare media, pricing, availability, and publishing.",
  "style": "wizard",
  "steps": [
    {
      "id": "property details",
      "label": "Property details",
      "description": "Complete the property details stage with validated inputs."
    },
    {
      "id": "media",
      "label": "Media",
      "description": "Complete the media stage with validated inputs."
    },
    {
      "id": "pricing",
      "label": "Pricing",
      "description": "Complete the pricing stage with validated inputs."
    },
    {
      "id": "availability",
      "label": "Availability",
      "description": "Complete the availability stage with validated inputs."
    },
    {
      "id": "publish",
      "label": "Publish",
      "description": "Complete the publish stage with validated inputs."
    }
  ],
  "completeLabel": "Publish listing"
});
