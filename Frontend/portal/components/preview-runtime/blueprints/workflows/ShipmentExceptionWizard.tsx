"use client";

import { createWorkflowPart } from "../core";

export const ShipmentExceptionWizard = createWorkflowPart({
  "title": "Shipment exception",
  "description": "Resolve a delivery disruption and communicate the next plan.",
  "style": "incident",
  "steps": [
    {
      "id": "identify issue",
      "label": "Identify issue",
      "description": "Complete the identify issue stage with validated inputs."
    },
    {
      "id": "assess impact",
      "label": "Assess impact",
      "description": "Complete the assess impact stage with validated inputs."
    },
    {
      "id": "choose resolution",
      "label": "Choose resolution",
      "description": "Complete the choose resolution stage with validated inputs."
    },
    {
      "id": "reschedule route",
      "label": "Reschedule route",
      "description": "Complete the reschedule route stage with validated inputs."
    },
    {
      "id": "notify parties",
      "label": "Notify parties",
      "description": "Complete the notify parties stage with validated inputs."
    }
  ],
  "completeLabel": "Resolve exception"
});
