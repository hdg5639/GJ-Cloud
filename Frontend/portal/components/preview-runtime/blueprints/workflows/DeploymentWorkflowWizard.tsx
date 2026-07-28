"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Field, Input, Select, Textarea } from "@/components/ui/field";
import { Wizard } from "@/components/ui/wizard";
import { BlueprintKeyValueGrid, BlueprintProgressBar, BlueprintStatusPill } from "../core";
import type { BlueprintOption } from "../core";

export function DeploymentWorkflowWizard({ open, onClose, environments, strategies, onDeploy, progress }: { open: boolean; onClose: () => void; environments: BlueprintOption[]; strategies: BlueprintOption[]; onDeploy: (values: { version: string; environment: string; strategy: string; notes: string }) => Promise<void>; progress?: { status: string; value: number; message?: string } | null }) {
  const [step, setStep] = useState(0);
  const [busy, setBusy] = useState(false);
  const [values, setValues] = useState({ version: "", environment: environments[0]?.value ?? "", strategy: strategies[0]?.value ?? "", notes: "" });
  const steps = [{ key: "artifact", label: "Artifact" }, { key: "strategy", label: "Strategy" }, { key: "review", label: "Review" }, { key: "deploy", label: "Deploy" }];
  async function submit() { setStep(3); setBusy(true); try { await onDeploy(values); } finally { setBusy(false); } }
  return <Wizard open={open} onClose={onClose} title="Deploy release" description="Promote an artifact through a controlled deployment workflow." steps={steps} currentStep={step} footer={<><Button onClick={step === 0 ? onClose : () => setStep(Math.max(0, step - 1))}>{step === 0 ? "Cancel" : "Back"}</Button><div className="ml-auto" />{step < 2 && <Button variant="primary" disabled={step === 0 && !values.version} onClick={() => setStep(step + 1)}>Continue</Button>}{step === 2 && <Button variant="primary" onClick={submit}>Start deployment</Button>}{step === 3 && !busy && <Button variant="primary" onClick={onClose}>Done</Button>}</>}>
    {step === 0 && <div className="mx-auto max-w-xl"><Field label="Artifact version"><Input value={values.version} onChange={(event) => setValues({ ...values, version: event.target.value })} placeholder="v1.4.0" /></Field><Field label="Environment"><Select value={values.environment} onChange={(event) => setValues({ ...values, environment: event.target.value })}>{environments.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</Select></Field></div>}
    {step === 1 && <div className="mx-auto max-w-xl"><Field label="Deployment strategy"><Select value={values.strategy} onChange={(event) => setValues({ ...values, strategy: event.target.value })}>{strategies.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</Select></Field><Field label="Release notes"><Textarea value={values.notes} onChange={(event) => setValues({ ...values, notes: event.target.value })} /></Field></div>}
    {step === 2 && <div className="mx-auto max-w-2xl rounded-[15px] border border-line bg-panel p-5"><BlueprintKeyValueGrid fields={[{ key: "version", value: values.version }, { key: "environment", value: values.environment }, { key: "strategy", value: values.strategy }, { key: "notes", value: values.notes }]} columns={2} /></div>}
    {step === 3 && <div className="mx-auto max-w-xl text-center"><BlueprintStatusPill value={progress?.status ?? (busy ? "DEPLOYING" : "SUCCEEDED")} /><h3 className="mt-4 text-xl font-black">{busy ? "Deployment in progress" : "Deployment finished"}</h3><p className="mt-2 text-sm text-muted-soft">{progress?.message ?? "The registered deployment status binding is being tracked."}</p><div className="mt-6"><BlueprintProgressBar value={progress?.value ?? (busy ? 55 : 100)} label="Deployment" /></div></div>}
  </Wizard>;
}
