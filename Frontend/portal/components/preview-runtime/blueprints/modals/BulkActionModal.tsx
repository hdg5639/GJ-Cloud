"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Select, Textarea } from "@/components/ui/field";
import type { BlueprintOption } from "../core";
import { BlueprintModalFrame } from "./BlueprintModalFrame";

export function BulkActionModal({ open, onClose, selectedCount, actions, onApply }: { open: boolean; onClose: () => void; selectedCount: number; actions: BlueprintOption[]; onApply: (action: string, note: string) => void | Promise<void> }) {
  const [action, setAction] = useState(actions[0]?.value ?? "");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  async function apply() { setBusy(true); try { await onApply(action, note); } finally { setBusy(false); } }
  return <BlueprintModalFrame open={open} onClose={onClose} title="Bulk action" description={`Apply one action to ${selectedCount} selected records.`} eyebrow="Batch operation" footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" disabled={!action || selectedCount === 0 || busy} onClick={apply}>{busy ? "Applying…" : `Apply to ${selectedCount}`}</Button></>}><label className="block text-xs font-bold text-muted">Action<Select value={action} onChange={(event) => setAction(event.target.value)} className="mt-2">{actions.map((option) => <option key={option.value} value={option.value} disabled={option.disabled}>{option.label}</option>)}</Select></label><label className="mt-4 block text-xs font-bold text-muted">Operator note<Textarea value={note} onChange={(event) => setNote(event.target.value)} placeholder="Optional reason or audit note" className="mt-2" /></label></BlueprintModalFrame>;
}
