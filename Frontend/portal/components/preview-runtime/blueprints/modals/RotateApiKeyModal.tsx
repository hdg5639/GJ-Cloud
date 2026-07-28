"use client";

import { createModalPart } from "../core";

export const RotateApiKeyModal = createModalPart({
  "title": "Rotate API key",
  "description": "Create a new key and revoke the previous credential.",
  "eyebrow": "Developer security",
  "confirmLabel": "Rotate key",
  "style": "danger",
  "requireReason": true,
  "fields": [],
  "requireText": "ROTATE"
});
