"use client";

import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { BlueprintModalFrame } from "./BlueprintModalFrame";

export function TypedDangerModal({ open, onClose, expectedText, title = "Confirm destructive action", description, impact, onConfirm, submitting = false }: { open: boolean; onClose: () => void; expectedText: string; title?: string; description?: string; impact?: string[]; onConfirm: () => void | Promise<void>; submitting?: boolean }) {
  const [value, setValue] = useState("");
  const valid = useMemo(() => value.trim() === expectedText, [value, expectedText]);
  return <BlueprintModalFrame open={open} onClose={onClose} title={title} description={description ?? "This action may be irreversible."} eyebrow="Danger zone" size="sm" footer={<><Button onClick={onClose}>Cancel</Button><Button variant="danger-solid" disabled={!valid || submitting} onClick={onConfirm}>{submitting ? "Processing…" : "Confirm"}</Button></>}><div className="rounded-[13px] border border-[color-mix(in_srgb,var(--preview-status-danger,#e0484d)_36%,var(--line))] bg-[color-mix(in_srgb,var(--preview-status-danger,#e0484d)_8%,transparent)] p-4"><strong className="text-sm text-[var(--preview-status-danger,#e0484d)]">Permanent impact</strong>{impact && <ul className="mt-2 list-disc space-y-1 pl-5 text-xs leading-5 text-muted">{impact.map((item) => <li key={item}>{item}</li>)}</ul>}</div><label className="mt-4 block text-xs font-bold text-muted">Type <code className="rounded bg-white/[0.05] px-1.5 py-0.5 text-foreground">{expectedText}</code> to continue<Input autoFocus value={value} onChange={(event) => setValue(event.target.value)} className="mt-2" /></label></BlueprintModalFrame>;
}
