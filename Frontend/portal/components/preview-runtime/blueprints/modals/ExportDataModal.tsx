"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input, Select } from "@/components/ui/field";
import { BlueprintModalFrame } from "./BlueprintModalFrame";

export function ExportDataModal({ open, onClose, availableFields, onExport }: { open: boolean; onClose: () => void; availableFields: string[]; onExport: (options: { format: string; fields: string[]; filename: string }) => void | Promise<void> }) {
  const [format, setFormat] = useState("CSV");
  const [filename, setFilename] = useState("export");
  const [fields, setFields] = useState(availableFields);
  function toggle(field: string) { setFields((current) => current.includes(field) ? current.filter((item) => item !== field) : [...current, field]); }
  return <BlueprintModalFrame open={open} onClose={onClose} title="Export data" description="Choose a file format and fields to include." eyebrow="Data operation" footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" disabled={fields.length === 0} onClick={() => onExport({ format, fields, filename })}>Export</Button></>}><div className="grid gap-4 md:grid-cols-2"><label className="text-xs font-bold text-muted">Filename<Input value={filename} onChange={(event) => setFilename(event.target.value)} className="mt-2" /></label><label className="text-xs font-bold text-muted">Format<Select value={format} onChange={(event) => setFormat(event.target.value)} className="mt-2"><option>CSV</option><option>JSON</option><option>XLSX</option></Select></label></div><div className="mt-5"><div className="flex items-center justify-between"><strong className="text-sm">Fields</strong><button type="button" onClick={() => setFields(fields.length === availableFields.length ? [] : availableFields)} className="text-xs font-bold text-brand-strong">{fields.length === availableFields.length ? "Clear all" : "Select all"}</button></div><div className="mt-3 grid gap-2 sm:grid-cols-2">{availableFields.map((field) => <label key={field} className="flex items-center gap-2 rounded-[10px] border border-line px-3 py-2 text-xs"><input type="checkbox" checked={fields.includes(field)} onChange={() => toggle(field)} />{field}</label>)}</div></div></BlueprintModalFrame>;
}
