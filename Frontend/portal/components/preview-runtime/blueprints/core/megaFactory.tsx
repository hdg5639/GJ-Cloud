"use client";

import { useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/panel";
import { cn } from "@/components/ui/cn";
import {
  BlueprintActionButtons,
  BlueprintEmptyState,
  BlueprintKeyValueGrid,
  BlueprintMetricGrid,
  BlueprintProgressBar,
  BlueprintSection,
  BlueprintSparkBars,
  BlueprintStatusPill,
  blueprintRecordId,
  blueprintRecordTitle,
  formatBlueprintValue,
  humanizeBlueprintKey,
} from "./helpers";
import type { BlueprintAction, BlueprintNavItem, BlueprintRecord, BlueprintTimelineEvent } from "./types";
import type {
  MegaActionConfig, MegaCollectionConfig, MegaCollectionProps, MegaDashboardConfig, MegaDashboardProps,
  MegaDetailConfig, MegaDetailProps, MegaFeedbackConfig, MegaFormConfig, MegaFormProps, MegaLayoutConfig,
  MegaLayoutProps, MegaModalConfig, MegaModalProps, MegaNavigationConfig, MegaThemeConfig, MegaWorkflowConfig,
  MegaWorkflowProps,
} from "./megaTypes";

function styleClass(style: string): string {
  return {
    glass: "border-white/15 bg-white/[0.045] backdrop-blur-xl",
    terminal: "rounded-none border-[color:var(--preview-blueprint-brand,#9ef01a)]/35 bg-[#061008] font-mono",
    editorial: "rounded-none border-line bg-[color:var(--preview-theme-paper,#f4efe6)] text-[color:var(--preview-theme-ink,#1d1b18)]",
    brutalist: "rounded-none border-2 border-foreground shadow-[5px_5px_0_var(--foreground)]",
    soft: "rounded-[24px] border-transparent bg-white/[0.035] shadow-[0_18px_60px_rgba(0,0,0,.12)]",
    commerce: "rounded-[20px] border-[color:var(--preview-theme-accent,#ff8b4d)]/30 bg-gradient-to-br from-[color:var(--preview-theme-accent,#ff8b4d)]/10 to-transparent",
    command: "border-[color:var(--preview-status-ok,#3fbf74)]/25 bg-[#07100c]",
    minimal: "rounded-[10px] border-line/70 bg-transparent",
    neon: "border-[color:var(--preview-theme-accent,#a855f7)]/40 bg-gradient-to-br from-purple-500/10 via-cyan-500/5 to-transparent shadow-[0_0_36px_rgba(168,85,247,.12)]",
    paper: "rounded-[6px] border-black/10 bg-[#f7f2e8] text-[#24211c] shadow-[0_8px_30px_rgba(24,20,12,.14)]",
    dense: "rounded-[8px] border-line bg-panel",
    enterprise: "rounded-[14px] border-line bg-panel",
  }[style] ?? "rounded-[14px] border-line bg-panel";
}

function recordFields(record: BlueprintRecord, keys?: string[]) {
  const entries = keys?.length
    ? keys.map((key) => [key, record[key]] as const)
    : Object.entries(record).filter(([key]) => !["id", "uid", "uuid"].includes(key)).slice(0, 8);
  return entries.map(([key, value]) => ({ key, value }));
}

function VisualBars({ values }: { values: number[] }) {
  return <BlueprintSparkBars values={values.length ? values : [18, 31, 24, 48, 42, 61, 55]} />;
}

function Ring({ value, label }: { value: number; label: string }) {
  const normalized = Math.max(0, Math.min(100, value));
  return <div className="grid place-items-center"><div className="grid h-24 w-24 place-items-center rounded-full" style={{ background: `conic-gradient(var(--preview-blueprint-brand,var(--brand,#9ef01a)) ${normalized * 3.6}deg, rgba(255,255,255,.07) 0deg)` }}><div className="grid h-[76px] w-[76px] place-items-center rounded-full bg-panel"><strong className="text-xl">{normalized}%</strong></div></div><span className="mt-2 text-xs text-muted-soft">{label}</span></div>;
}

function ActivityList({ activity = [] }: { activity?: BlueprintTimelineEvent[] }) {
  return <div className="space-y-3">{activity.slice(0, 6).map((event) => <div key={event.id} className="relative border-l border-line pl-4"><span className="absolute -left-1 top-1.5 h-2 w-2 rounded-full bg-brand" /><div className="flex items-start justify-between gap-3"><strong className="text-sm">{event.title}</strong>{event.status && <BlueprintStatusPill value={event.status} tone={event.tone} />}</div>{event.description && <p className="mt-1 text-xs leading-5 text-muted-soft">{event.description}</p>}<p className="mt-1 text-[10px] text-muted-soft">{event.timestamp ?? event.actor ?? "Recent"}</p></div>)}{activity.length === 0 && <BlueprintEmptyState title="No activity yet" description="Events will appear here as the workflow progresses." />}</div>;
}

export function createDashboardPart(config: MegaDashboardConfig) {
  function Dashboard({ metrics, records = [], activity = [], onSelect, className }: MegaDashboardProps) {
    const values = metrics.map((metric) => Number(metric.value)).filter(Number.isFinite);
    const lead = records[0];
    return <div className={cn("space-y-4", className)}>
      <section className={cn("border p-5", styleClass(config.style))}>
        <p className="text-[10px] font-black uppercase tracking-[0.18em] text-muted-soft">{config.eyebrow ?? "Overview"}</p>
        <div className="mt-2 flex flex-wrap items-end justify-between gap-4"><div><h2 className="text-2xl font-black">{config.title}</h2><p className="mt-1 max-w-2xl text-sm text-muted-soft">{config.description}</p></div><BlueprintStatusPill value="Live" tone={config.defaultTone ?? "ok"} /></div>
      </section>
      <BlueprintMetricGrid metrics={metrics} columns={Math.min(6, Math.max(2, metrics.length || 2)) as 2 | 3 | 4 | 5 | 6} />
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.45fr)_minmax(280px,.75fr)]">
        <BlueprintSection title={config.primaryLabel} description="Current movement and distribution across the selected scope.">
          {config.style === "command" || config.style === "terminal" ? <div className="grid gap-3 sm:grid-cols-2">{records.slice(0, 8).map((record) => <button key={blueprintRecordId(record)} type="button" onClick={() => onSelect?.(record)} className="rounded-[10px] border border-line bg-black/10 p-3 text-left font-mono"><div className="flex justify-between gap-2"><strong className="truncate text-xs">{blueprintRecordTitle(record)}</strong><BlueprintStatusPill value={record.status ?? record.state ?? "active"} /></div><p className="mt-2 truncate text-[10px] text-muted-soft">{formatBlueprintValue(record.description ?? record.type ?? record.id)}</p></button>)}</div> : <VisualBars values={values.length ? values : records.map((_, index) => 16 + index * 7)} />}
        </BlueprintSection>
        <BlueprintSection title={config.secondaryLabel} description="Priority signal for this workspace.">
          {config.style === "glass" || config.style === "neon" ? <div className="flex justify-center"><Ring value={values[0] ?? 74} label={config.secondaryLabel} /></div> : lead ? <button type="button" onClick={() => onSelect?.(lead)} className="w-full rounded-[12px] border border-line p-4 text-left"><strong>{blueprintRecordTitle(lead)}</strong><p className="mt-2 text-xs leading-5 text-muted-soft">{formatBlueprintValue(lead.description ?? lead.summary ?? "Open the leading record for more detail.")}</p><div className="mt-3"><BlueprintStatusPill value={lead.status ?? lead.state ?? "ready"} /></div></button> : <BlueprintEmptyState title="No primary signal" />}
        </BlueprintSection>
      </div>
      <BlueprintSection title={config.activityLabel ?? "Recent activity"}><ActivityList activity={activity} /></BlueprintSection>
    </div>;
  }
  Dashboard.displayName = config.title.replace(/\s+/g, "");
  return Dashboard;
}

export function createCollectionPart(config: MegaCollectionConfig) {
  function Collection({ records, selectedId, onSelect, onAction, className }: MegaCollectionProps) {
    if (!records.length) return <BlueprintEmptyState title={config.emptyLabel ?? `No ${config.title.toLowerCase()}`} description={config.description} />;
    const primary = config.primaryField ?? "name";
    const secondary = config.secondaryField ?? "description";
    const status = config.statusField ?? "status";
    if (config.style === "table" || config.style === "matrix") return <div className={cn("overflow-hidden rounded-[14px] border border-line", className)}><div className="flex items-center justify-between border-b border-line bg-white/[0.02] px-4 py-3"><div><strong>{config.title}</strong><p className="text-xs text-muted-soft">{config.description}</p></div><span className="text-xs text-muted-soft">{records.length} records</span></div><div className="overflow-x-auto"><table className="w-full min-w-[680px] text-left text-sm"><thead className="bg-white/[0.02] text-[10px] uppercase tracking-wide text-muted-soft"><tr><th className="px-4 py-3">{humanizeBlueprintKey(primary)}</th><th className="px-4 py-3">{humanizeBlueprintKey(secondary)}</th><th className="px-4 py-3">Status</th><th className="px-4 py-3 text-right">Action</th></tr></thead><tbody>{records.map((record, index) => { const id = blueprintRecordId(record) || String(index); return <tr key={id} className={cn("border-t border-line/70", selectedId === id && "bg-brand/[0.06]")}><td className="px-4 py-3 font-bold"><button type="button" onClick={() => onSelect?.(record)}>{formatBlueprintValue(record[primary] ?? blueprintRecordTitle(record))}</button></td><td className="max-w-[320px] truncate px-4 py-3 text-muted-soft">{formatBlueprintValue(record[secondary])}</td><td className="px-4 py-3"><BlueprintStatusPill value={record[status] ?? "active"} /></td><td className="px-4 py-3 text-right"><Button size="small" onClick={() => onAction?.(record)}>{config.actionLabel ?? "Open"}</Button></td></tr>; })}</tbody></table></div></div>;
    if (config.style === "timeline") return <BlueprintSection title={config.title} description={config.description}><div className="space-y-4">{records.map((record, index) => <button key={blueprintRecordId(record) || index} type="button" onClick={() => onSelect?.(record)} className="relative block w-full border-l border-line pl-5 text-left"><span className="absolute -left-[5px] top-1 h-2.5 w-2.5 rounded-full bg-brand" /><div className="flex justify-between gap-3"><strong>{formatBlueprintValue(record[primary] ?? blueprintRecordTitle(record))}</strong><span className="text-xs text-muted-soft">{formatBlueprintValue(record.timestamp ?? record.date ?? record.createdAt)}</span></div><p className="mt-1 text-xs text-muted-soft">{formatBlueprintValue(record[secondary])}</p></button>)}</div></BlueprintSection>;
    if (config.style === "inbox") return <div className={cn("divide-y divide-line overflow-hidden rounded-[14px] border border-line", className)}>{records.map((record, index) => <button key={blueprintRecordId(record) || index} type="button" onClick={() => onSelect?.(record)} className="flex w-full items-start gap-3 p-4 text-left hover:bg-white/[0.025]"><span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-brand" /><div className="min-w-0 flex-1"><div className="flex justify-between gap-3"><strong className="truncate">{formatBlueprintValue(record[primary] ?? blueprintRecordTitle(record))}</strong><span className="text-[10px] text-muted-soft">{formatBlueprintValue(record.timestamp ?? record.updatedAt)}</span></div><p className="mt-1 truncate text-xs text-muted-soft">{formatBlueprintValue(record[secondary])}</p></div><BlueprintStatusPill value={record[status] ?? "new"} /></button>)}</div>;
    if (config.style === "board") { const groups = new Map<string, BlueprintRecord[]>(); records.forEach((record) => { const key = formatBlueprintValue(record[status] ?? "Unassigned"); groups.set(key, [...(groups.get(key) ?? []), record]); }); return <div className={cn("grid gap-4 xl:grid-cols-3", className)}>{[...groups.entries()].map(([group, items]) => <section key={group} className="rounded-[14px] border border-line bg-white/[0.015] p-3"><div className="mb-3 flex justify-between"><strong className="text-sm">{group}</strong><span className="text-xs text-muted-soft">{items.length}</span></div><div className="space-y-2">{items.map((record) => <button key={blueprintRecordId(record)} type="button" onClick={() => onSelect?.(record)} className="block w-full rounded-[12px] border border-line bg-panel p-3 text-left"><strong className="block truncate text-sm">{formatBlueprintValue(record[primary] ?? blueprintRecordTitle(record))}</strong><p className="mt-1 line-clamp-2 text-xs text-muted-soft">{formatBlueprintValue(record[secondary])}</p></button>)}</div></section>)}</div>; }
    if (config.style === "calendar") return <BlueprintSection title={config.title} description={config.description}><div className="grid grid-cols-7 gap-1">{Array.from({ length: 35 }, (_, day) => { const record = records[day % records.length]; return <button key={day} type="button" onClick={() => onSelect?.(record)} className="min-h-20 rounded-[8px] border border-line p-2 text-left"><span className="text-[10px] text-muted-soft">{day + 1}</span>{day < records.length && <><strong className="mt-2 block truncate text-xs">{formatBlueprintValue(record[primary] ?? blueprintRecordTitle(record))}</strong><BlueprintStatusPill value={record[status] ?? "scheduled"} /></>}</button>; })}</div></BlueprintSection>;
    if (config.style === "tree") return <BlueprintSection title={config.title} description={config.description}><div className="space-y-1">{records.map((record, index) => <button key={blueprintRecordId(record) || index} type="button" onClick={() => onSelect?.(record)} className="flex w-full items-center gap-2 rounded-[8px] px-3 py-2 text-left hover:bg-white/[0.025]" style={{ paddingLeft: `${12 + Number(record.depth ?? index % 3) * 18}px` }}><span className="text-muted-soft">{Number(record.depth ?? 0) > 0 ? "└" : "▾"}</span><strong className="text-sm">{formatBlueprintValue(record[primary] ?? blueprintRecordTitle(record))}</strong><span className="ml-auto"><BlueprintStatusPill value={record[status] ?? "active"} /></span></button>)}</div></BlueprintSection>;
    if (config.style === "map") return <div className={cn("relative min-h-[420px] overflow-hidden rounded-[18px] border border-line bg-[radial-gradient(circle_at_30%_25%,rgba(158,240,26,.08),transparent_26%),linear-gradient(135deg,rgba(255,255,255,.025),transparent)]", className)}><div className="absolute inset-0 opacity-30" style={{ backgroundImage: "linear-gradient(rgba(255,255,255,.06) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.06) 1px, transparent 1px)", backgroundSize: "32px 32px" }} />{records.slice(0, 16).map((record, index) => <button key={blueprintRecordId(record) || index} type="button" onClick={() => onSelect?.(record)} className="absolute -translate-x-1/2 -translate-y-1/2 rounded-full border border-brand bg-panel px-2 py-1 text-[10px] font-bold shadow-lg" style={{ left: `${12 + ((index * 17) % 78)}%`, top: `${15 + ((index * 29) % 70)}%` }}>{formatBlueprintValue(record[primary] ?? index + 1)}</button>)}</div>;
    return <div className={cn(config.style === "gallery" ? "grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-4" : "grid gap-3 sm:grid-cols-2 xl:grid-cols-3", className)}>{records.map((record, index) => <article key={blueprintRecordId(record) || index} className="overflow-hidden rounded-[16px] border border-line bg-panel"><button type="button" onClick={() => onSelect?.(record)} className="block w-full text-left">{config.style === "gallery" && <div className="aspect-video bg-gradient-to-br from-brand/15 to-transparent">{typeof record.imageUrl === "string" && <img src={record.imageUrl} alt="" className="h-full w-full object-cover" />}</div>}<div className="p-4"><div className="flex items-start justify-between gap-3"><strong className="truncate">{formatBlueprintValue(record[primary] ?? blueprintRecordTitle(record))}</strong><BlueprintStatusPill value={record[status] ?? "active"} /></div><p className="mt-2 line-clamp-2 text-xs leading-5 text-muted-soft">{formatBlueprintValue(record[secondary])}</p></div></button>{onAction && <div className="border-t border-line p-3"><Button size="small" className="w-full" onClick={() => onAction(record)}>{config.actionLabel ?? "Open"}</Button></div>}</article>)}</div>;
  }
  Collection.displayName = config.title.replace(/\s+/g, "");
  return Collection;
}

export function createDetailPart(config: MegaDetailConfig) {
  function Detail({ record, activity = [], actions = [], onAction, className }: MegaDetailProps) {
    const statusValue = record[config.statusField ?? "status"] ?? record.state ?? "active";
    const primary = recordFields(record, config.primaryFields);
    const secondary = recordFields(record, config.secondaryFields);
    const title = blueprintRecordTitle(record);
    const header = <div className="flex flex-wrap items-start justify-between gap-4"><div><p className="text-[10px] font-black uppercase tracking-[.18em] text-muted-soft">{config.title}</p><h2 className="mt-1 text-2xl font-black">{title}</h2><p className="mt-1 max-w-2xl text-sm text-muted-soft">{config.description}</p></div><div className="flex items-center gap-2"><BlueprintStatusPill value={statusValue} />{actions.length > 0 && <BlueprintActionButtons actions={actions} onAction={onAction} />}</div></div>;
    if (config.style === "document") return <article className={cn("mx-auto max-w-4xl rounded-[8px] border border-black/10 bg-[#f7f2e8] p-8 text-[#24211c] shadow-xl", className)}>{header}<div className="my-8 h-px bg-black/10" /><BlueprintKeyValueGrid fields={primary} columns={2} /><div className="prose prose-sm mt-8 max-w-none"><p>{formatBlueprintValue(record.body ?? record.content ?? record.description)}</p></div></article>;
    if (config.style === "technical") return <div className={cn("space-y-4 font-mono", className)}><section className="rounded-none border border-brand/35 bg-[#061008] p-5">{header}</section><div className="grid gap-4 xl:grid-cols-[1.2fr_.8fr]"><Card className="rounded-none border-brand/20 bg-black/20"><BlueprintKeyValueGrid fields={primary} columns={1} /></Card><Card className="rounded-none border-brand/20 bg-black/20"><pre className="overflow-auto text-xs text-brand-strong">{JSON.stringify(record, null, 2)}</pre></Card></div></div>;
    if (config.style === "profile") return <div className={cn("space-y-4", className)}><section className="rounded-[24px] border border-line bg-gradient-to-br from-brand/10 to-transparent p-6"><div className="flex flex-wrap items-center gap-5"><div className="grid h-20 w-20 place-items-center rounded-full border border-brand/30 bg-brand/10 text-2xl font-black">{title.slice(0, 2).toUpperCase()}</div><div className="min-w-0 flex-1">{header}</div></div></section><div className="grid gap-4 xl:grid-cols-[1fr_360px]"><BlueprintSection title="Profile"><BlueprintKeyValueGrid fields={primary} columns={2} /></BlueprintSection><BlueprintSection title="Activity"><ActivityList activity={activity} /></BlueprintSection></div></div>;
    if (config.style === "commerce") return <div className={cn("grid gap-5 xl:grid-cols-[minmax(320px,.8fr)_1.2fr]", className)}><div className="aspect-square rounded-[24px] border border-line bg-gradient-to-br from-orange-500/10 to-transparent">{typeof record.imageUrl === "string" && <img src={record.imageUrl} alt="" className="h-full w-full rounded-[24px] object-cover" />}</div><div className="space-y-5">{header}<strong className="block text-3xl tabular-nums">{formatBlueprintValue(record.price ?? record.amount)}</strong><BlueprintKeyValueGrid fields={primary} columns={2} />{actions.length > 0 && <BlueprintActionButtons actions={actions} onAction={onAction} />}</div></div>;
    if (config.style === "casefile") return <div className={cn("grid gap-4 xl:grid-cols-[280px_1fr]", className)}><aside className="rounded-[14px] border border-line bg-white/[.02] p-5">{header}<div className="mt-5"><BlueprintKeyValueGrid fields={secondary.length ? secondary : primary.slice(0, 4)} columns={1} /></div></aside><main className="space-y-4"><BlueprintSection title="Case summary"><BlueprintKeyValueGrid fields={primary} columns={2} /></BlueprintSection><BlueprintSection title="Timeline"><ActivityList activity={activity} /></BlueprintSection></main></div>;
    if (config.style === "timeline") return <div className={cn("space-y-4", className)}><section className="rounded-[14px] border border-line p-5">{header}</section><BlueprintSection title="Lifecycle"><ActivityList activity={activity} /></BlueprintSection><BlueprintSection title="Attributes"><BlueprintKeyValueGrid fields={primary} columns={3} /></BlueprintSection></div>;
    return <div className={cn("space-y-4", className)}><section className="rounded-[18px] border border-line bg-gradient-to-br from-white/[.035] to-transparent p-6">{header}</section><div className="grid gap-4 xl:grid-cols-[1.2fr_.8fr]"><BlueprintSection title="Overview"><BlueprintKeyValueGrid fields={primary} columns={2} /></BlueprintSection><BlueprintSection title="Additional context"><BlueprintKeyValueGrid fields={secondary.length ? secondary : primary.slice(-4)} columns={1} /></BlueprintSection></div>{activity.length > 0 && <BlueprintSection title="Recent activity"><ActivityList activity={activity} /></BlueprintSection>}</div>;
  }
  Detail.displayName = config.title.replace(/\s+/g, "");
  return Detail;
}

export function createModalPart(config: MegaModalConfig) {
  function Modal({ open, context = {}, options = [], busy = false, onClose, onConfirm }: MegaModalProps) {
    const [values, setValues] = useState<BlueprintRecord>({});
    const fields = config.fields ?? [];
    if (!open) return null;
    const requiredTextMatches = !config.requireText || String(values.confirmation ?? "") === config.requireText;
    const submit = () => { if (requiredTextMatches && !busy) void onConfirm(values); };
    return <div className="fixed inset-0 z-[120] grid place-items-center bg-black/70 p-4" role="dialog" aria-modal="true"><div className={cn("max-h-[90vh] w-full max-w-xl overflow-auto border p-0 shadow-2xl", styleClass(config.style === "danger" ? "brutalist" : config.style === "command" ? "terminal" : config.style === "review" ? "paper" : "enterprise"))}><header className="border-b border-line px-5 py-4"><p className="text-[10px] font-black uppercase tracking-[.18em] text-muted-soft">{config.eyebrow}</p><h2 className="mt-1 text-xl font-black">{config.title}</h2><p className="mt-1 text-sm text-muted-soft">{config.description}</p></header><div className="space-y-4 p-5">{Object.keys(context).length > 0 && <div className="rounded-[12px] border border-line bg-white/[.02] p-3"><BlueprintKeyValueGrid fields={recordFields(context).slice(0, 4)} columns={2} /></div>}{fields.map((field) => <label key={field.key} className="block text-xs font-bold text-muted-soft">{field.label}{field.type === "textarea" ? <textarea className="mt-2 min-h-24 w-full rounded-[10px] border border-line bg-soft px-3 py-2 text-sm text-foreground" value={String(values[field.key] ?? "")} onChange={(event) => setValues((current) => ({ ...current, [field.key]: event.target.value }))} /> : field.type === "select" ? <select className="mt-2 min-h-10 w-full rounded-[10px] border border-line bg-soft px-3 text-sm text-foreground" value={String(values[field.key] ?? "")} onChange={(event) => setValues((current) => ({ ...current, [field.key]: event.target.value }))}><option value="">Select</option>{(field.options ?? options.map((option) => option.value)).map((option) => <option key={option} value={option}>{option}</option>)}</select> : <input type={field.type === "number" ? "number" : field.type === "date" ? "date" : "text"} className="mt-2 min-h-10 w-full rounded-[10px] border border-line bg-soft px-3 text-sm text-foreground" value={String(values[field.key] ?? "")} onChange={(event) => setValues((current) => ({ ...current, [field.key]: event.target.value }))} />}</label>)}{config.requireReason && <label className="block text-xs font-bold text-muted-soft">Reason<textarea className="mt-2 min-h-24 w-full rounded-[10px] border border-line bg-soft px-3 py-2 text-sm text-foreground" value={String(values.reason ?? "")} onChange={(event) => setValues((current) => ({ ...current, reason: event.target.value }))} /></label>}{config.requireText && <label className="block text-xs font-bold text-muted-soft">Type <strong>{config.requireText}</strong> to confirm<input className="mt-2 min-h-10 w-full rounded-[10px] border border-line bg-soft px-3 text-sm text-foreground" value={String(values.confirmation ?? "")} onChange={(event) => setValues((current) => ({ ...current, confirmation: event.target.value }))} /></label>}</div><footer className="flex justify-end gap-2 border-t border-line px-5 py-4"><Button onClick={onClose}>Cancel</Button><Button variant={config.style === "danger" ? "danger-solid" : "primary"} disabled={!requiredTextMatches || busy} onClick={submit}>{busy ? "Working…" : config.confirmLabel}</Button></footer></div></div>;
  }
  Modal.displayName = config.title.replace(/\s+/g, "");
  return Modal;
}

export function createWorkflowPart(config: MegaWorkflowConfig) {
  function Workflow({ initialValues = {}, busy = false, onCancel, onComplete, className }: MegaWorkflowProps) {
    const [step, setStep] = useState(0);
    const [values, setValues] = useState<BlueprintRecord>(initialValues);
    const active = config.steps[step];
    const progress = Math.round(((step + 1) / config.steps.length) * 100);
    return <div className={cn("overflow-hidden rounded-[18px] border border-line bg-panel", className)}><header className="border-b border-line p-5"><p className="text-[10px] font-black uppercase tracking-[.18em] text-muted-soft">{config.style}</p><h2 className="mt-1 text-xl font-black">{config.title}</h2><p className="mt-1 text-sm text-muted-soft">{config.description}</p><div className="mt-4"><BlueprintProgressBar value={progress} label={`Step ${step + 1} of ${config.steps.length}`} /></div></header><div className={cn("grid min-h-[360px]", config.style === "timeline" || config.style === "incident" ? "md:grid-cols-[240px_1fr]" : "grid-cols-1")}><aside className="border-b border-line p-4 md:border-b-0 md:border-r">{config.steps.map((item, index) => <button key={item.id} type="button" onClick={() => index <= step && setStep(index)} className={cn("mb-2 flex w-full gap-3 rounded-[10px] p-2 text-left", index === step && "bg-brand/10", index < step && "opacity-70")}><span className="grid h-6 w-6 shrink-0 place-items-center rounded-full border border-line text-[10px] font-black">{index < step ? "✓" : index + 1}</span><span><strong className="block text-xs">{item.label}</strong><span className="mt-1 block text-[10px] text-muted-soft">{item.description}</span></span></button>)}</aside><main className="p-6"><p className="text-[10px] font-black uppercase tracking-[.18em] text-muted-soft">Current step</p><h3 className="mt-1 text-lg font-black">{active.label}</h3><p className="mt-1 text-sm text-muted-soft">{active.description}</p><div className="mt-6 grid gap-4 sm:grid-cols-2"><label className="text-xs font-bold text-muted-soft">Name<input className="mt-2 min-h-10 w-full rounded-[10px] border border-line bg-soft px-3 text-sm text-foreground" value={String(values.name ?? "")} onChange={(event) => setValues((current) => ({ ...current, name: event.target.value }))} /></label><label className="text-xs font-bold text-muted-soft">Owner<input className="mt-2 min-h-10 w-full rounded-[10px] border border-line bg-soft px-3 text-sm text-foreground" value={String(values.owner ?? "")} onChange={(event) => setValues((current) => ({ ...current, owner: event.target.value }))} /></label></div><label className="mt-4 block text-xs font-bold text-muted-soft">Notes<textarea className="mt-2 min-h-28 w-full rounded-[10px] border border-line bg-soft px-3 py-2 text-sm text-foreground" value={String(values.notes ?? "")} onChange={(event) => setValues((current) => ({ ...current, notes: event.target.value }))} /></label></main></div><footer className="flex items-center justify-between border-t border-line p-4"><Button onClick={step === 0 ? onCancel : () => setStep((current) => Math.max(0, current - 1))}>{step === 0 ? "Cancel" : "Back"}</Button>{step < config.steps.length - 1 ? <Button variant="primary" onClick={() => setStep((current) => current + 1)}>Continue</Button> : <Button variant="primary" disabled={busy} onClick={() => void onComplete(values)}>{busy ? "Working…" : config.completeLabel}</Button>}</footer></div>;
  }
  Workflow.displayName = config.title.replace(/\s+/g, "");
  return Workflow;
}

export function createFormPart(config: MegaFormConfig) {
  function Form({ initialValues = {}, busy = false, onSubmit, className }: MegaFormProps) {
    const [values, setValues] = useState<BlueprintRecord>(initialValues);
    return <form className={cn("space-y-4", className)} onSubmit={(event) => { event.preventDefault(); void onSubmit(values); }}>{config.sections.map((section) => <BlueprintSection key={section.title} title={section.title} description={section.description}><div className={cn("grid gap-4", config.style === "inline" ? "md:grid-cols-3" : "md:grid-cols-2")}>{section.fields.map((field) => <label key={field.key} className={cn("text-xs font-bold text-muted-soft", field.type === "textarea" && "md:col-span-2")}>{field.label}{field.type === "textarea" ? <textarea className="mt-2 min-h-28 w-full rounded-[10px] border border-line bg-soft px-3 py-2 text-sm text-foreground" placeholder={field.placeholder} value={String(values[field.key] ?? "")} onChange={(event) => setValues((current) => ({ ...current, [field.key]: event.target.value }))} /> : field.type === "select" ? <select className="mt-2 min-h-10 w-full rounded-[10px] border border-line bg-soft px-3 text-sm text-foreground" value={String(values[field.key] ?? "")} onChange={(event) => setValues((current) => ({ ...current, [field.key]: event.target.value }))}><option value="">Select</option>{(field.options ?? []).map((option) => <option key={option}>{option}</option>)}</select> : field.type === "toggle" ? <button type="button" onClick={() => setValues((current) => ({ ...current, [field.key]: !current[field.key] }))} className={cn("mt-2 flex min-h-10 w-full items-center justify-between rounded-[10px] border px-3", values[field.key] ? "border-brand bg-brand/10" : "border-line bg-soft")}><span>{values[field.key] ? "Enabled" : "Disabled"}</span><span className={cn("h-5 w-9 rounded-full p-0.5", values[field.key] ? "bg-brand" : "bg-white/10")}><span className={cn("block h-4 w-4 rounded-full bg-white transition-transform", Boolean(values[field.key]) && "translate-x-4")} /></span></button> : <input type={field.type === "number" ? "number" : field.type === "date" ? "date" : "text"} className="mt-2 min-h-10 w-full rounded-[10px] border border-line bg-soft px-3 text-sm text-foreground" placeholder={field.placeholder} value={String(values[field.key] ?? "")} onChange={(event) => setValues((current) => ({ ...current, [field.key]: event.target.value }))} />}</label>)}</div></BlueprintSection>)}<div className="flex justify-end"><Button type="submit" variant="primary" disabled={busy}>{busy ? "Saving…" : "Save changes"}</Button></div></form>;
  }
  Form.displayName = config.title.replace(/\s+/g, "");
  return Form;
}

export function createActionPart(config: MegaActionConfig) {
  function Actions({ actions = config.actions, onAction, selectedCount = 0, className }: { actions?: BlueprintAction[]; onAction?: (action: BlueprintAction) => void; selectedCount?: number; className?: string }) {
    if (config.style === "floating") return <div className={cn("fixed bottom-6 right-6 z-40 flex flex-col items-end gap-2", className)}>{actions.map((action) => <Button key={action.id} variant={action.tone === "danger" ? "danger-solid" : "primary"} onClick={() => onAction?.(action)}>{action.label}</Button>)}</div>;
    if (config.style === "chips") return <div className={cn("flex flex-wrap gap-2", className)}>{actions.map((action) => <button key={action.id} type="button" onClick={() => onAction?.(action)} className="rounded-full border border-line px-3 py-1.5 text-xs font-bold hover:border-brand">{action.label}</button>)}</div>;
    if (config.style === "segmented") return <div className={cn("inline-flex rounded-[10px] border border-line bg-soft p-1", className)}>{actions.map((action, index) => <button key={action.id} type="button" onClick={() => onAction?.(action)} className={cn("rounded-[7px] px-3 py-2 text-xs font-bold", index === 0 && "bg-panel shadow")}>{action.label}</button>)}</div>;
    return <div className={cn("flex flex-wrap items-center justify-between gap-3 rounded-[12px] border border-line p-3", config.style === "danger" ? "border-danger/30 bg-danger/5" : config.style === "commandbar" ? "bg-[#061008] font-mono" : "bg-panel", className)}><div><strong className="text-sm">{config.title ?? (selectedCount ? `${selectedCount} selected` : "Actions")}</strong>{config.style === "bulk" && <p className="text-xs text-muted-soft">Apply an action to selected records.</p>}</div><BlueprintActionButtons actions={actions} onAction={onAction} /></div>;
  }
  Actions.displayName = (config.title ?? config.style).replace(/\s+/g, "");
  return Actions;
}

export function createNavigationPart(config: MegaNavigationConfig) {
  function Navigation({ items, activeId, onNavigate, className }: { items: BlueprintNavItem[]; activeId?: string; onNavigate?: (item: BlueprintNavItem) => void; className?: string }) {
    if (config.style === "breadcrumbs") return <nav className={cn("flex flex-wrap items-center gap-2 text-xs", className)}>{items.map((item, index) => <span key={item.id} className="flex items-center gap-2"><button type="button" onClick={() => onNavigate?.(item)} className={cn("font-bold", (activeId ?? items.at(-1)?.id) === item.id ? "text-foreground" : "text-muted-soft")}>{item.label}</button>{index < items.length - 1 && <span className="text-muted-soft">/</span>}</span>)}</nav>;
    if (config.style === "tabs") return <nav className={cn("flex gap-1 overflow-x-auto border-b border-line", className)}>{items.map((item) => <button key={item.id} type="button" onClick={() => onNavigate?.(item)} className={cn("border-b-2 px-4 py-3 text-sm font-bold", (activeId ?? items[0]?.id) === item.id ? "border-brand text-foreground" : "border-transparent text-muted-soft")}>{item.label}{item.badge !== undefined && <span className="ml-2 rounded-full bg-white/10 px-2 py-0.5 text-[10px]">{item.badge}</span>}</button>)}</nav>;
    if (config.style === "stepper") return <nav className={cn("flex items-start", className)}>{items.map((item, index) => <div key={item.id} className="flex min-w-0 flex-1 items-center"><button type="button" onClick={() => onNavigate?.(item)} className="min-w-0 text-left"><span className={cn("grid h-8 w-8 place-items-center rounded-full border text-xs font-black", (activeId ?? items[0]?.id) === item.id ? "border-brand bg-brand text-black" : "border-line")}>{index + 1}</span><strong className="mt-2 block truncate text-xs">{item.label}</strong></button>{index < items.length - 1 && <span className="mx-2 mt-4 h-px flex-1 bg-line" />}</div>)}</nav>;
    if (config.style === "rail" || config.style === "bottom") return <nav className={cn(config.style === "bottom" ? "flex justify-around border-t border-line bg-panel p-2" : "flex w-20 flex-col gap-2 rounded-[16px] border border-line bg-panel p-2", className)}>{items.map((item) => <button key={item.id} type="button" title={item.label} onClick={() => onNavigate?.(item)} className={cn("grid min-h-12 place-items-center rounded-[10px] px-2 text-[10px] font-bold", (activeId ?? items[0]?.id) === item.id ? "bg-brand text-black" : "text-muted-soft hover:bg-white/[.04]")}><span className="text-base">{item.label.slice(0, 1)}</span><span className="truncate">{item.label}</span></button>)}</nav>;
    if (config.style === "palette") return <div className={cn("rounded-[14px] border border-line bg-panel p-3 shadow-2xl", className)}><input aria-label="Search commands" placeholder="Type a command…" className="min-h-11 w-full rounded-[10px] border border-line bg-soft px-3 text-sm" /><div className="mt-2 space-y-1">{items.map((item) => <button key={item.id} type="button" onClick={() => onNavigate?.(item)} className="flex w-full justify-between rounded-[8px] px-3 py-2 text-left text-sm hover:bg-white/[.04]"><span>{item.label}</span><span className="text-[10px] text-muted-soft">{item.badge ?? "↵"}</span></button>)}</div></div>;
    if (config.style === "tree") return <nav className={cn("space-y-1", className)}>{items.map((item, index) => <button key={item.id} type="button" onClick={() => onNavigate?.(item)} className={cn("flex w-full items-center gap-2 rounded-[8px] px-3 py-2 text-left text-sm", (activeId ?? items[0]?.id) === item.id ? "bg-brand/10 text-brand-strong" : "text-muted-soft")} style={{ paddingLeft: `${12 + (index % 3) * 12}px` }}><span>{index % 3 ? "└" : "▾"}</span>{item.label}</button>)}</nav>;
    return <nav className={cn(config.style === "top" || config.style === "mega" ? "flex flex-wrap items-center gap-2 rounded-[14px] border border-line bg-panel p-2" : "w-64 space-y-1 rounded-[16px] border border-line bg-panel p-3", className)}>{config.title && <p className="px-2 py-2 text-[10px] font-black uppercase tracking-[.18em] text-muted-soft">{config.title}</p>}{items.map((item) => <button key={item.id} type="button" onClick={() => onNavigate?.(item)} className={cn("rounded-[9px] px-3 py-2 text-left text-sm font-bold", config.style === "top" || config.style === "mega" ? "inline-flex" : "flex w-full justify-between", (activeId ?? items[0]?.id) === item.id ? "bg-brand text-black" : "text-muted-soft hover:bg-white/[.04]")}>{item.label}{item.badge !== undefined && <span className="ml-2 text-[10px]">{item.badge}</span>}</button>)}</nav>;
  }
  Navigation.displayName = (config.title ?? config.style).replace(/\s+/g, "");
  return Navigation;
}

export function createFeedbackPart(config: MegaFeedbackConfig) {
  function Feedback({ onAction, details, className }: { onAction?: () => void; details?: string; className?: string }) {
    const symbol = { empty: "◇", error: "!", warning: "△", success: "✓", loading: "…", offline: "⌁", permission: "◈", maintenance: "⚙", onboarding: "→" }[config.style];
    const tone = config.style === "error" ? "danger" : config.style === "warning" ? "warn" : config.style === "success" ? "ok" : "idle";
    return <div className={cn("grid min-h-64 place-items-center rounded-[18px] border border-dashed border-line bg-white/[.015] p-8 text-center", className)}><div className="max-w-md"><span className="mx-auto grid h-16 w-16 place-items-center rounded-full border text-2xl font-black" style={{ color: `var(--preview-status-${tone}, #9ef01a)`, borderColor: `color-mix(in srgb, var(--preview-status-${tone}, #9ef01a) 35%, transparent)`, background: `color-mix(in srgb, var(--preview-status-${tone}, #9ef01a) 8%, transparent)` }}>{symbol}</span><h3 className="mt-5 text-lg font-black">{config.title}</h3><p className="mt-2 text-sm leading-6 text-muted-soft">{details ?? config.description}</p>{config.style === "loading" && <div className="mx-auto mt-5 max-w-xs"><BlueprintProgressBar value={62} label="Loading" /></div>}{config.actionLabel && <Button className="mt-5" variant="primary" onClick={onAction}>{config.actionLabel}</Button>}</div></div>;
  }
  Feedback.displayName = config.title.replace(/\s+/g, "");
  return Feedback;
}

export function createLayoutPart(config: MegaLayoutConfig) {
  function Layout({ navigation, header, summary, toolbar, main, aside, secondary, footer, overlay, className }: MegaLayoutProps) {
    if (config.style === "sidebar") return <div className={cn("grid min-h-[620px] overflow-hidden rounded-[18px] border border-line bg-panel lg:grid-cols-[260px_1fr]", className)}><aside className="border-b border-line bg-black/10 p-4 lg:border-b-0 lg:border-r">{navigation}</aside><div className="min-w-0"><header className="border-b border-line p-5">{header ?? <><h2 className="text-xl font-black">{config.title}</h2><p className="text-sm text-muted-soft">{config.description}</p></>}</header>{toolbar && <div className="border-b border-line p-3">{toolbar}</div>}<main className="space-y-4 p-5">{summary}{main}{secondary}{footer}</main></div>{overlay}</div>;
    if (config.style === "cockpit" || config.style === "console") return <div className={cn("space-y-3 rounded-[12px] border border-line bg-[#061008] p-3 font-mono", className)}><header className="flex flex-wrap items-center justify-between gap-3 border-b border-brand/20 pb-3">{header ?? <div><strong>{config.title}</strong><p className="text-xs text-muted-soft">{config.description}</p></div>}{toolbar}</header><div className="grid gap-3 xl:grid-cols-[1fr_320px]"><main className="space-y-3">{summary}{main}{secondary}</main>{aside && <aside className="space-y-3">{aside}</aside>}</div>{footer}{overlay}</div>;
    if (config.style === "studio" || config.style === "canvas") return <div className={cn("grid min-h-[640px] overflow-hidden rounded-[18px] border border-line lg:grid-cols-[220px_1fr_300px]", className)}><aside className="border-b border-line p-4 lg:border-b-0 lg:border-r">{navigation}</aside><main className="min-w-0 bg-white/[.015]"><header className="border-b border-line p-4">{header}</header><div className="p-5">{summary}{main}{secondary}</div></main><aside className="border-t border-line p-4 lg:border-l lg:border-t-0">{aside}</aside>{overlay}</div>;
    if (config.style === "planner" || config.style === "map") return <div className={cn("space-y-4", className)}><header className="rounded-[16px] border border-line p-5">{header ?? <><h2 className="text-xl font-black">{config.title}</h2><p className="text-sm text-muted-soft">{config.description}</p></>}</header><div className="grid gap-4 xl:grid-cols-[320px_1fr]">{aside && <aside>{aside}</aside>}<main className="space-y-4">{toolbar}{summary}{main}{secondary}</main></div>{footer}{overlay}</div>;
    if (config.style === "ledger") return <div className={cn("space-y-4 rounded-[6px] border border-black/10 bg-[#f7f2e8] p-6 text-[#24211c]", className)}><header>{header ?? <><h2 className="font-serif text-2xl font-black">{config.title}</h2><p className="text-sm opacity-60">{config.description}</p></>}</header>{summary}<div className="grid gap-4 xl:grid-cols-[1fr_320px]"><main>{main}{secondary}</main>{aside && <aside>{aside}</aside>}</div>{footer}{overlay}</div>;
    return <div className={cn("space-y-4", className)}><header className="rounded-[16px] border border-line p-5">{header ?? <><h2 className="text-xl font-black">{config.title}</h2><p className="text-sm text-muted-soft">{config.description}</p></>}</header>{toolbar}{summary}<div className={cn("grid gap-4", Boolean(aside) && "xl:grid-cols-[1fr_360px]")}><main>{main}{secondary}</main>{aside && <aside>{aside}</aside>}</div>{footer}{overlay}</div>;
  }
  Layout.displayName = config.title.replace(/\s+/g, "");
  return Layout;
}

export function createThemePart(config: MegaThemeConfig) {
  function Theme({ children, className }: { children: ReactNode; className?: string }) {
    return <div data-blueprint-theme={config.id} className={cn(config.className, "min-h-full", className)}>{children}</div>;
  }
  Theme.displayName = config.label.replace(/\s+/g, "");
  return Theme;
}
