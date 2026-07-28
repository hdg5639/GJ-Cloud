"use client";

import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Field, Input, Select, Textarea } from "@/components/ui/field";
import { Wizard } from "@/components/ui/wizard";
import { BlueprintKeyValueGrid, BlueprintProgressBar, BlueprintStatusPill } from "../core";
import type { BlueprintField, BlueprintOption } from "../core";

export interface ProvisioningValues {
  name: string;
  plan: string;
  region: string;
  description: string;
  labels: string;
}

export function ResourceProvisioningWizard({
  open,
  onClose,
  plans,
  regions,
  onSubmit,
  progress,
}: {
  open: boolean;
  onClose: () => void;
  plans: BlueprintOption[];
  regions: BlueprintOption[];
  onSubmit: (values: ProvisioningValues) => Promise<void>;
  progress?: { status: string; value: number; message?: string } | null;
}) {
  const [step, setStep] = useState(0);
  const [busy, setBusy] = useState(false);
  const [values, setValues] = useState<ProvisioningValues>({ name: "", plan: plans[0]?.value ?? "", region: regions[0]?.value ?? "", description: "", labels: "" });
  const summary = useMemo<BlueprintField[]>(() => [
    { key: "name", value: values.name },
    { key: "plan", value: plans.find((item) => item.value === values.plan)?.label ?? values.plan },
    { key: "region", value: regions.find((item) => item.value === values.region)?.label ?? values.region },
    { key: "description", value: values.description },
    { key: "labels", value: values.labels },
  ], [values, plans, regions]);
  const steps = [{ key: "identity", label: "Identity" }, { key: "capacity", label: "Capacity" }, { key: "review", label: "Review" }, { key: "progress", label: "Provision" }];
  async function submit() { setBusy(true); setStep(3); try { await onSubmit(values); } finally { setBusy(false); } }
  return <Wizard open={open} onClose={onClose} title="Provision resource" description="Create a configured resource through a guided workflow." steps={steps} currentStep={step} onStepClick={(index) => index < 3 && setStep(index)} footer={<><Button onClick={step === 0 ? onClose : () => setStep((current) => Math.max(0, current - 1))}>{step === 0 ? "Cancel" : "Back"}</Button><div className="ml-auto" />{step < 2 && <Button variant="primary" disabled={step === 0 && !values.name.trim()} onClick={() => setStep((current) => current + 1)}>Continue</Button>}{step === 2 && <Button variant="primary" disabled={busy} onClick={submit}>{busy ? "Submitting…" : "Provision"}</Button>}{step === 3 && !busy && <Button variant="primary" onClick={onClose}>Done</Button>}</>}>
    {step === 0 && <div className="mx-auto max-w-xl"><Field label="Resource name"><Input value={values.name} onChange={(event) => setValues({ ...values, name: event.target.value })} placeholder="example-resource" /></Field><Field label="Description"><Textarea value={values.description} onChange={(event) => setValues({ ...values, description: event.target.value })} /></Field><Field label="Labels"><Input value={values.labels} onChange={(event) => setValues({ ...values, labels: event.target.value })} placeholder="team=platform, env=dev" /></Field></div>}
    {step === 1 && <div className="mx-auto grid max-w-2xl gap-4 md:grid-cols-2"><Field label="Plan"><Select value={values.plan} onChange={(event) => setValues({ ...values, plan: event.target.value })}>{plans.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</Select></Field><Field label="Region"><Select value={values.region} onChange={(event) => setValues({ ...values, region: event.target.value })}>{regions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</Select></Field><div className="md:col-span-2 rounded-[14px] border border-line bg-panel p-4 text-xs leading-5 text-muted-soft">The selected plan and region are passed through registered API bindings. No arbitrary infrastructure values are invented by the Blueprint Part.</div></div>}
    {step === 2 && <div className="mx-auto max-w-2xl rounded-[15px] border border-line bg-panel p-5"><BlueprintKeyValueGrid fields={summary} columns={2} /></div>}
    {step === 3 && <div className="mx-auto max-w-xl text-center"><div className="mb-4 flex justify-center"><BlueprintStatusPill value={progress?.status ?? (busy ? "RUNNING" : "COMPLETED")} /></div><h3 className="text-xl font-black">{busy ? "Provisioning in progress" : "Provisioning complete"}</h3><p className="mt-2 text-sm text-muted-soft">{progress?.message ?? "The workflow is tracking the registered asynchronous operation."}</p><div className="mt-6"><BlueprintProgressBar value={progress?.value ?? (busy ? 45 : 100)} label="Progress" /></div></div>}
  </Wizard>;
}
