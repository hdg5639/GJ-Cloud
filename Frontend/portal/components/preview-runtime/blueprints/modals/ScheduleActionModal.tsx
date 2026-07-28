"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input, Select, Textarea } from "@/components/ui/field";
import { BlueprintModalFrame } from "./BlueprintModalFrame";

export function ScheduleActionModal({ open, onClose, actionLabel, onSchedule }: { open: boolean; onClose: () => void; actionLabel: string; onSchedule: (schedule: { dateTime: string; timezone: string; note: string }) => void | Promise<void> }) {
  const [dateTime, setDateTime] = useState("");
  const [timezone, setTimezone] = useState("Asia/Seoul");
  const [note, setNote] = useState("");
  return <BlueprintModalFrame open={open} onClose={onClose} title={`Schedule ${actionLabel}`} description="Execute this action at a controlled future time." eyebrow="Scheduled operation" footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" disabled={!dateTime} onClick={() => onSchedule({ dateTime, timezone, note })}>Schedule</Button></>}><div className="grid gap-4 md:grid-cols-2"><label className="text-xs font-bold text-muted">Date and time<Input type="datetime-local" value={dateTime} onChange={(event) => setDateTime(event.target.value)} className="mt-2" /></label><label className="text-xs font-bold text-muted">Timezone<Select value={timezone} onChange={(event) => setTimezone(event.target.value)} className="mt-2"><option>Asia/Seoul</option><option>UTC</option><option>America/Los_Angeles</option><option>Europe/London</option></Select></label></div><label className="mt-4 block text-xs font-bold text-muted">Operator note<Textarea value={note} onChange={(event) => setNote(event.target.value)} className="mt-2" /></label></BlueprintModalFrame>;
}
