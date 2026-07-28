"use client";

import { createDetailPart } from "../core";

export const CommunityMemberDetail = createDetailPart({
  "title": "Community member",
  "description": "Profile, roles, activity, reputation, and moderation context.",
  "style": "profile",
  "statusField": "status",
  "primaryFields": [
    "name",
    "role",
    "reputation",
    "status"
  ],
  "secondaryFields": [
    "joinedAt",
    "lastActiveAt"
  ]
});
