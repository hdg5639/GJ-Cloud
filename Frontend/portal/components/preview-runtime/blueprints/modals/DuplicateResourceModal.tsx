"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { BlueprintModalFrame } from "./BlueprintModalFrame";

export function DuplicateResourceModal({ open, onClose, sourceName, defaultName, onDuplicate }: { open: boolean; onClose: () => void; sourceName: string; defaultName?: string; onDuplicate: (options: { name: string; includeChildren: boolean }) => void | Promise<void> }) {
  const [name, setName] = useState(defaultName ?? `${sourceName} copy`);
  const [includeChildren, setIncludeChildren] = useState(false);
  return <BlueprintModalFrame open={open} onClose={onClose} title="Duplicate resource" description={`Create a new resource from ${sourceName}.`} eyebrow="Copy workflow" footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" disabled={!name.trim()} onClick={() => onDuplicate({ name, includeChildren })}>Create copy</Button></>}><label className="block text-xs font-bold text-muted">New name<Input value={name} onChange={(event) => setName(event.target.value)} className="mt-2" /></label><label className="mt-4 flex items-start gap-3 rounded-[12px] border border-line p-3"><input type="checkbox" checked={includeChildren} onChange={(event) => setIncludeChildren(event.target.checked)} className="mt-1" /><span><strong className="block text-sm">Include related child resources</strong><span className="mt-1 block text-xs leading-5 text-muted-soft">Copy nested configuration when supported by the target API.</span></span></label></BlueprintModalFrame>;
}
