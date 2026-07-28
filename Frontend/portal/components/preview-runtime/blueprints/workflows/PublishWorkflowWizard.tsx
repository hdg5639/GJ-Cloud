"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Field, Input, Select, Textarea } from "@/components/ui/field";
import { Wizard } from "@/components/ui/wizard";
import { BlueprintKeyValueGrid, BlueprintStatusPill } from "../core";

export function PublishWorkflowWizard({ open, onClose, channels, onPublish }: { open: boolean; onClose: () => void; channels: Array<{ value: string; label: string }>; onPublish: (values: { channel: string; slug: string; publishAt: string; note: string }) => Promise<void> }) {
  const [step, setStep] = useState(0);
  const [values, setValues] = useState({ channel: channels[0]?.value ?? "", slug: "", publishAt: "", note: "" });
  const [complete, setComplete] = useState(false);
  const steps = [{ key: "destination", label: "Destination" }, { key: "schedule", label: "Schedule" }, { key: "review", label: "Review" }, { key: "publish", label: "Publish" }];
  async function submit() { await onPublish(values); setComplete(true); setStep(3); }
  return <Wizard open={open} onClose={onClose} title="Publish content" description="Prepare a controlled content release." steps={steps} currentStep={step} footer={<><Button onClick={step === 0 ? onClose : () => setStep(step - 1)}>{step === 0 ? "Cancel" : "Back"}</Button><div className="ml-auto" />{step < 2 && <Button variant="primary" disabled={step === 0 && !values.slug} onClick={() => setStep(step + 1)}>Continue</Button>}{step === 2 && <Button variant="primary" onClick={submit}>Publish</Button>}{step === 3 && <Button variant="primary" onClick={onClose}>Done</Button>}</>}>
    {step === 0 && <div className="mx-auto max-w-xl"><Field label="Channel"><Select value={values.channel} onChange={(event) => setValues({ ...values, channel: event.target.value })}>{channels.map((channel) => <option key={channel.value} value={channel.value}>{channel.label}</option>)}</Select></Field><Field label="Slug"><Input value={values.slug} onChange={(event) => setValues({ ...values, slug: event.target.value })} placeholder="article-slug" /></Field></div>}
    {step === 1 && <div className="mx-auto max-w-xl"><Field label="Publish time"><Input type="datetime-local" value={values.publishAt} onChange={(event) => setValues({ ...values, publishAt: event.target.value })} /></Field><Field label="Publishing note"><Textarea value={values.note} onChange={(event) => setValues({ ...values, note: event.target.value })} /></Field></div>}
    {step === 2 && <div className="mx-auto max-w-2xl rounded-[15px] border border-line bg-panel p-5"><BlueprintKeyValueGrid fields={[{ key: "channel", value: values.channel }, { key: "slug", value: values.slug }, { key: "publishAt", value: values.publishAt || "Immediately" }, { key: "note", value: values.note }]} columns={2} /></div>}
    {step === 3 && <div className="mx-auto max-w-xl py-10 text-center"><BlueprintStatusPill value={complete ? "PUBLISHED" : "PUBLISHING"} /><h3 className="mt-4 text-xl font-black">{complete ? "Content published" : "Publishing"}</h3><p className="mt-2 text-sm text-muted-soft">The generated frontend can now navigate to the published detail page.</p></div>}
  </Wizard>;
}
