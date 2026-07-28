"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { BlueprintModalFrame } from "./BlueprintModalFrame";

export function PayloadPreviewModal({ open, onClose, title = "Payload preview", request, response, onCopy }: { open: boolean; onClose: () => void; title?: string; request?: unknown; response?: unknown; onCopy?: (value: string) => void }) {
  const [tab, setTab] = useState<"request" | "response">(request !== undefined ? "request" : "response");
  const active = tab === "request" ? request : response;
  const serialized = JSON.stringify(active ?? null, null, 2);
  return <BlueprintModalFrame open={open} onClose={onClose} title={title} description="Inspect the structured request and response payloads." eyebrow="Technical preview" size="lg" footer={<><Button onClick={onClose}>Close</Button>{onCopy && <Button variant="primary" onClick={() => onCopy(serialized)}>Copy JSON</Button>}</>}><div className="mb-3 flex gap-2">{request !== undefined && <button type="button" onClick={() => setTab("request")} className={`rounded-[9px] px-3 py-2 text-xs font-bold ${tab === "request" ? "bg-brand text-[#0a0c08]" : "bg-white/[0.05] text-muted"}`}>Request</button>}{response !== undefined && <button type="button" onClick={() => setTab("response")} className={`rounded-[9px] px-3 py-2 text-xs font-bold ${tab === "response" ? "bg-brand text-[#0a0c08]" : "bg-white/[0.05] text-muted"}`}>Response</button>}</div><pre className="max-h-[520px] overflow-auto rounded-[13px] border border-line bg-[#080a08] p-4 text-xs leading-6 text-[#d7dfd5]">{serialized}</pre></BlueprintModalFrame>;
}
