"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Field, Select, Textarea } from "@/components/ui/field";
import { Wizard } from "@/components/ui/wizard";
import { BlueprintKeyValueGrid, BlueprintStatusPill } from "../core";
import type { BlueprintDirectoryEntry } from "../core";

export function ApprovalWorkflowWizard({ open, onClose, approvers, onSubmit }: { open: boolean; onClose: () => void; approvers: BlueprintDirectoryEntry[]; onSubmit: (values: { approverId: string; message: string; urgency: string }) => Promise<void> }) {
  const [step, setStep] = useState(0);
  const [values, setValues] = useState({ approverId: approvers[0]?.id ?? "", message: "", urgency: "NORMAL" });
  const [submitted, setSubmitted] = useState(false);
  const steps = [{ key: "approver", label: "Approver" }, { key: "context", label: "Context" }, { key: "review", label: "Review" }, { key: "submitted", label: "Submitted" }];
  async function submit() { await onSubmit(values); setSubmitted(true); setStep(3); }
  return <Wizard open={open} onClose={onClose} title="Request approval" description="Route a decision to an authorized reviewer." steps={steps} currentStep={step} footer={<><Button onClick={step === 0 ? onClose : () => setStep(step - 1)}>{step === 0 ? "Cancel" : "Back"}</Button><div className="ml-auto" />{step < 2 && <Button variant="primary" onClick={() => setStep(step + 1)}>Continue</Button>}{step === 2 && <Button variant="primary" onClick={submit}>Submit request</Button>}{step === 3 && <Button variant="primary" onClick={onClose}>Done</Button>}</>}>
    {step === 0 && <div className="mx-auto max-w-xl"><Field label="Approver"><Select value={values.approverId} onChange={(event) => setValues({ ...values, approverId: event.target.value })}>{approvers.map((person) => <option key={person.id} value={person.id}>{person.title}{person.role ? ` — ${person.role}` : ""}</option>)}</Select></Field><Field label="Urgency"><Select value={values.urgency} onChange={(event) => setValues({ ...values, urgency: event.target.value })}><option>NORMAL</option><option>HIGH</option><option>CRITICAL</option></Select></Field></div>}
    {step === 1 && <div className="mx-auto max-w-xl"><Field label="Decision context"><Textarea value={values.message} onChange={(event) => setValues({ ...values, message: event.target.value })} placeholder="Explain the requested decision and its impact." /></Field></div>}
    {step === 2 && <div className="mx-auto max-w-2xl rounded-[15px] border border-line bg-panel p-5"><BlueprintKeyValueGrid fields={[{ key: "approver", value: approvers.find((person) => person.id === values.approverId)?.title }, { key: "urgency", value: values.urgency }, { key: "message", value: values.message }]} columns={1} /></div>}
    {step === 3 && <div className="mx-auto max-w-xl py-10 text-center"><BlueprintStatusPill value={submitted ? "PENDING APPROVAL" : "SUBMITTING"} tone="warn" /><h3 className="mt-4 text-xl font-black">Approval request created</h3><p className="mt-2 text-sm text-muted-soft">The request can now be tracked through its workflow status binding.</p></div>}
  </Wizard>;
}
