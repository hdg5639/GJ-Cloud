"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input, Select } from "@/components/ui/field";
import { BlueprintProgressBar } from "../core";
import { BlueprintModalFrame } from "./BlueprintModalFrame";

export function ImportDataModal({ open, onClose, formats = ["CSV", "JSON"], onImport }: { open: boolean; onClose: () => void; formats?: string[]; onImport: (file: File, options: { format: string; hasHeader: boolean; dryRun: boolean }) => Promise<void> }) {
  const [file, setFile] = useState<File | null>(null);
  const [format, setFormat] = useState(formats[0] ?? "CSV");
  const [hasHeader, setHasHeader] = useState(true);
  const [dryRun, setDryRun] = useState(true);
  const [progress, setProgress] = useState<number | null>(null);
  async function submit() { if (!file) return; setProgress(15); try { await onImport(file, { format, hasHeader, dryRun }); setProgress(100); } finally { setTimeout(() => setProgress(null), 500); } }
  return <BlueprintModalFrame open={open} onClose={onClose} title="Import data" description="Validate and import structured records." eyebrow="Data operation" footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" disabled={!file || progress !== null} onClick={submit}>Start import</Button></>}><div className="rounded-[13px] border border-dashed border-line-strong bg-white/[0.015] p-5 text-center"><Input type="file" accept=".csv,.json,application/json,text/csv" onChange={(event) => setFile(event.target.files?.[0] ?? null)} /><p className="mt-2 text-xs text-muted-soft">{file ? `${file.name} · ${Math.ceil(file.size / 1024)} KB` : "Choose a source file"}</p></div><div className="mt-4 grid gap-4 md:grid-cols-2"><label className="text-xs font-bold text-muted">Format<Select value={format} onChange={(event) => setFormat(event.target.value)} className="mt-2">{formats.map((item) => <option key={item}>{item}</option>)}</Select></label><div className="space-y-3 pt-1 text-xs text-muted"><label className="flex items-center gap-2"><input type="checkbox" checked={hasHeader} onChange={(event) => setHasHeader(event.target.checked)} /> First row contains headers</label><label className="flex items-center gap-2"><input type="checkbox" checked={dryRun} onChange={(event) => setDryRun(event.target.checked)} /> Validate without committing first</label></div></div>{progress !== null && <div className="mt-5"><BlueprintProgressBar value={progress} label="Import progress" /></div>}</BlueprintModalFrame>;
}
