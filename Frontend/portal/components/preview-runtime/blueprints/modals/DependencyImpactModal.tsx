"use client";

import { Button } from "@/components/ui/button";
import { BlueprintStatusPill } from "../core";
import type { BlueprintRecord } from "../core";
import { formatBlueprintValue } from "../core";
import { BlueprintModalFrame } from "./BlueprintModalFrame";

export function DependencyImpactModal({ open, onClose, targetName, dependencies, onContinue }: { open: boolean; onClose: () => void; targetName: string; dependencies: BlueprintRecord[]; onContinue: () => void | Promise<void> }) {
  return <BlueprintModalFrame open={open} onClose={onClose} title="Review dependency impact" description={`The action on ${targetName} may affect related resources.`} eyebrow="Impact analysis" size="lg" footer={<><Button onClick={onClose}>Cancel</Button><Button variant="danger" onClick={onContinue}>Continue with impact</Button></>}><div className="space-y-3">{dependencies.map((dependency, index) => <article key={String(dependency.id ?? index)} className="rounded-[13px] border border-line bg-panel p-4"><div className="flex items-start justify-between gap-3"><div><strong className="text-sm">{formatBlueprintValue(dependency.name ?? dependency.title ?? dependency.id)}</strong><p className="mt-1 text-xs text-muted-soft">{formatBlueprintValue(dependency.type ?? dependency.relationship)}</p></div><BlueprintStatusPill value={dependency.impact ?? dependency.status ?? "affected"} tone={dependency.impact === "BLOCKING" ? "danger" : "warn"} /></div>{Boolean(dependency.description) && <p className="mt-3 text-xs leading-5 text-muted-soft">{formatBlueprintValue(dependency.description)}</p>}</article>)}</div></BlueprintModalFrame>;
}
