"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Field, Input, Select } from "@/components/ui/field";
import { Wizard } from "@/components/ui/wizard";
import { BlueprintProgressBar, BlueprintStatusPill } from "../core";

export function DataImportWizard({ open, onClose, targetOptions, onValidate, onCommit }: { open: boolean; onClose: () => void; targetOptions: Array<{ value: string; label: string }>; onValidate: (file: File, target: string) => Promise<{ valid: number; invalid: number }>; onCommit: () => Promise<void> }) {
  const [step, setStep] = useState(0);
  const [file, setFile] = useState<File | null>(null);
  const [target, setTarget] = useState(targetOptions[0]?.value ?? "");
  const [result, setResult] = useState<{ valid: number; invalid: number } | null>(null);
  const [busy, setBusy] = useState(false);
  const steps = [{ key: "source", label: "Source" }, { key: "validate", label: "Validate" }, { key: "review", label: "Review" }, { key: "commit", label: "Commit" }];
  async function validate() { if (!file) return; setBusy(true); try { setResult(await onValidate(file, target)); setStep(2); } finally { setBusy(false); } }
  async function commit() { setStep(3); setBusy(true); try { await onCommit(); } finally { setBusy(false); } }
  return <Wizard open={open} onClose={onClose} title="Import data" description="Validate, review, and commit an import in separate safe stages." steps={steps} currentStep={step} footer={<><Button onClick={step === 0 ? onClose : () => setStep(Math.max(0, step - 1))}>{step === 0 ? "Cancel" : "Back"}</Button><div className="ml-auto" />{step === 0 && <Button variant="primary" disabled={!file} onClick={() => setStep(1)}>Continue</Button>}{step === 1 && <Button variant="primary" disabled={!file || busy} onClick={validate}>{busy ? "Validating…" : "Validate"}</Button>}{step === 2 && <Button variant="primary" disabled={(result?.valid ?? 0) === 0} onClick={commit}>Commit valid rows</Button>}{step === 3 && !busy && <Button variant="primary" onClick={onClose}>Done</Button>}</>}>
    {step === 0 && <div className="mx-auto max-w-xl"><Field label="Target resource"><Select value={target} onChange={(event) => setTarget(event.target.value)}>{targetOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</Select></Field><Field label="Source file"><Input type="file" onChange={(event) => setFile(event.target.files?.[0] ?? null)} /></Field></div>}
    {step === 1 && <div className="mx-auto max-w-xl py-12 text-center"><BlueprintStatusPill value={busy ? "VALIDATING" : "READY"} tone={busy ? "warn" : "neutral"} /><h3 className="mt-4 text-xl font-black">Schema and row validation</h3><p className="mt-2 text-sm text-muted-soft">Validation uses the selected target binding and does not commit data.</p>{busy && <div className="mt-6"><BlueprintProgressBar value={55} label="Validation" /></div>}</div>}
    {step === 2 && result && <div className="mx-auto grid max-w-xl grid-cols-2 gap-4"><div className="rounded-[14px] border border-[color-mix(in_srgb,var(--preview-status-ok,#3fbf74)_30%,var(--line))] bg-[color-mix(in_srgb,var(--preview-status-ok,#3fbf74)_8%,transparent)] p-5 text-center"><span className="text-xs text-muted-soft">Valid rows</span><strong className="mt-2 block text-3xl tabular-nums">{result.valid}</strong></div><div className="rounded-[14px] border border-[color-mix(in_srgb,var(--preview-status-danger,#e0484d)_30%,var(--line))] bg-[color-mix(in_srgb,var(--preview-status-danger,#e0484d)_8%,transparent)] p-5 text-center"><span className="text-xs text-muted-soft">Invalid rows</span><strong className="mt-2 block text-3xl tabular-nums">{result.invalid}</strong></div></div>}
    {step === 3 && <div className="mx-auto max-w-xl py-12 text-center"><BlueprintStatusPill value={busy ? "IMPORTING" : "COMPLETED"} /><h3 className="mt-4 text-xl font-black">{busy ? "Import in progress" : "Import complete"}</h3><div className="mt-6"><BlueprintProgressBar value={busy ? 65 : 100} label="Commit" /></div></div>}
  </Wizard>;
}
