"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Field, Input, Select } from "@/components/ui/field";
import { Wizard } from "@/components/ui/wizard";
import { BlueprintKeyValueGrid, BlueprintStatusPill } from "../core";
import type { BlueprintOption } from "../core";

export function UserOnboardingWizard({ open, onClose, roles, organizations, onInvite }: { open: boolean; onClose: () => void; roles: BlueprintOption[]; organizations: BlueprintOption[]; onInvite: (values: { email: string; name: string; role: string; organization: string; sendWelcome: boolean }) => Promise<void> }) {
  const [step, setStep] = useState(0);
  const [values, setValues] = useState({ email: "", name: "", role: roles[0]?.value ?? "", organization: organizations[0]?.value ?? "", sendWelcome: true });
  const [complete, setComplete] = useState(false);
  const steps = [{ key: "identity", label: "Identity" }, { key: "access", label: "Access" }, { key: "review", label: "Review" }, { key: "invite", label: "Invite" }];
  async function submit() { await onInvite(values); setComplete(true); setStep(3); }
  return <Wizard open={open} onClose={onClose} title="Onboard user" description="Create an invitation with explicit role and organization context." steps={steps} currentStep={step} footer={<><Button onClick={step === 0 ? onClose : () => setStep(step - 1)}>{step === 0 ? "Cancel" : "Back"}</Button><div className="ml-auto" />{step < 2 && <Button variant="primary" disabled={step === 0 && !values.email} onClick={() => setStep(step + 1)}>Continue</Button>}{step === 2 && <Button variant="primary" onClick={submit}>Send invitation</Button>}{step === 3 && <Button variant="primary" onClick={onClose}>Done</Button>}</>}>
    {step === 0 && <div className="mx-auto max-w-xl"><Field label="Email"><Input type="email" value={values.email} onChange={(event) => setValues({ ...values, email: event.target.value })} /></Field><Field label="Display name"><Input value={values.name} onChange={(event) => setValues({ ...values, name: event.target.value })} /></Field></div>}
    {step === 1 && <div className="mx-auto max-w-xl"><Field label="Organization"><Select value={values.organization} onChange={(event) => setValues({ ...values, organization: event.target.value })}>{organizations.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</Select></Field><Field label="Role"><Select value={values.role} onChange={(event) => setValues({ ...values, role: event.target.value })}>{roles.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</Select></Field><label className="flex items-center gap-2 text-xs text-muted"><input type="checkbox" checked={values.sendWelcome} onChange={(event) => setValues({ ...values, sendWelcome: event.target.checked })} /> Send welcome message</label></div>}
    {step === 2 && <div className="mx-auto max-w-2xl rounded-[15px] border border-line bg-panel p-5"><BlueprintKeyValueGrid fields={[{ key: "email", value: values.email }, { key: "name", value: values.name }, { key: "organization", value: values.organization }, { key: "role", value: values.role }, { key: "sendWelcome", value: values.sendWelcome }]} columns={2} /></div>}
    {step === 3 && <div className="mx-auto max-w-xl py-12 text-center"><BlueprintStatusPill value={complete ? "INVITED" : "SENDING"} /><h3 className="mt-4 text-xl font-black">Invitation created</h3><p className="mt-2 text-sm text-muted-soft">The user can now continue through the registered acceptance flow.</p></div>}
  </Wizard>;
}
