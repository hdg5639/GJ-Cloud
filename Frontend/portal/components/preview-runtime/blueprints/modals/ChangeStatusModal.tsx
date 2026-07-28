"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/field";
import { BlueprintStatusPill } from "../core";
import type { BlueprintOption } from "../core";
import { BlueprintModalFrame } from "./BlueprintModalFrame";

export function ChangeStatusModal({ open, onClose, currentStatus, statuses, onChange }: { open: boolean; onClose: () => void; currentStatus: string; statuses: BlueprintOption[]; onChange: (status: string, note: string) => void | Promise<void> }) {
  const [selected, setSelected] = useState(currentStatus);
  const [note, setNote] = useState("");
  return <BlueprintModalFrame open={open} onClose={onClose} title="Change status" description="Transition the resource to a new lifecycle state." eyebrow="Workflow" footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" disabled={selected === currentStatus} onClick={() => onChange(selected, note)}>Apply status</Button></>}><div className="grid gap-2 sm:grid-cols-2">{statuses.map((status) => <button key={status.value} type="button" disabled={status.disabled} onClick={() => setSelected(status.value)} className={`rounded-[12px] border p-3 text-left ${selected === status.value ? "border-brand bg-brand/8" : "border-line hover:border-line-strong"}`}><BlueprintStatusPill value={status.label} /><p className="mt-2 text-xs leading-5 text-muted-soft">{status.description}</p></button>)}</div><label className="mt-4 block text-xs font-bold text-muted">Transition note<Textarea value={note} onChange={(event) => setNote(event.target.value)} className="mt-2" /></label></BlueprintModalFrame>;
}
