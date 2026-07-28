import type { ReactNode } from "react";
import { Button, type ButtonVariant } from "@/components/ui/button";
import { Card } from "@/components/ui/panel";
import { cn } from "@/components/ui/cn";
import { statusTone, toneStyle } from "../../status";
import type {
  BlueprintAction,
  BlueprintField,
  BlueprintMetric,
  BlueprintRecord,
  BlueprintTone,
} from "./types";

export function humanizeBlueprintKey(value: string): string {
  return value
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/[_-]+/g, " ")
    .replace(/^./, (char) => char.toUpperCase());
}

export function formatBlueprintValue(value: unknown): string {
  if (value === null || value === undefined || value === "") return "—";
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (typeof value === "number") return new Intl.NumberFormat().format(value);
  if (typeof value === "string") return value;
  if (Array.isArray(value)) return value.map(formatBlueprintValue).join(", ");
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

export function blueprintRecordId(record: BlueprintRecord): string {
  const candidate = record.id ?? record.uid ?? record.uuid ?? record.key ?? record.code;
  return formatBlueprintValue(candidate);
}

export function blueprintRecordTitle(record: BlueprintRecord): string {
  const candidate = record.name ?? record.title ?? record.label ?? record.displayName ?? record.email ?? blueprintRecordId(record);
  return formatBlueprintValue(candidate);
}

export function toneFromBlueprintValue(value: unknown): BlueprintTone {
  if (typeof value !== "string") return "neutral";
  return statusTone(value);
}

export function BlueprintStatusPill({ value, tone }: { value: unknown; tone?: BlueprintTone }) {
  const resolved = tone ?? toneFromBlueprintValue(value);
  const style = toneStyle(resolved === "brand" ? "ok" : resolved);
  const brandStyle = resolved === "brand"
    ? {
        color: "var(--preview-blueprint-brand, var(--brand, #9ef01a))",
        background: "color-mix(in srgb, var(--preview-blueprint-brand, var(--brand, #9ef01a)) 13%, transparent)",
        borderColor: "color-mix(in srgb, var(--preview-blueprint-brand, var(--brand, #9ef01a)) 30%, transparent)",
      }
    : style;
  return (
    <span
      className="inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-extrabold"
      style={brandStyle}
    >
      <span className="h-1.5 w-1.5 rounded-full" style={{ background: brandStyle.color }} />
      {formatBlueprintValue(value)}
    </span>
  );
}

export function BlueprintSection({
  title,
  description,
  action,
  children,
  className,
}: {
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <Card className={cn("p-0", className)}>
      <div className="flex items-start justify-between gap-4 border-b border-line px-5 py-4">
        <div>
          <h3 className="text-sm font-extrabold">{title}</h3>
          {description && <p className="mt-1 text-xs text-muted-soft">{description}</p>}
        </div>
        {action}
      </div>
      <div className="p-5">{children}</div>
    </Card>
  );
}

export function BlueprintMetricCard({ metric, compact = false }: { metric: BlueprintMetric; compact?: boolean }) {
  const tone = metric.tone ?? "neutral";
  const style = tone === "brand"
    ? {
        color: "var(--preview-blueprint-brand, var(--brand, #9ef01a))",
        background: "color-mix(in srgb, var(--preview-blueprint-brand, var(--brand, #9ef01a)) 8%, transparent)",
        borderColor: "color-mix(in srgb, var(--preview-blueprint-brand, var(--brand, #9ef01a)) 22%, transparent)",
      }
    : toneStyle(tone);
  return (
    <article
      className={cn("rounded-[14px] border bg-panel", compact ? "p-4" : "p-5")}
      style={{ borderColor: style.borderColor, background: style.background }}
    >
      <p className="text-xs font-semibold text-muted-soft">{metric.label}</p>
      <div className="mt-2 flex items-end justify-between gap-3">
        <strong className={cn("tabular-nums", compact ? "text-xl" : "text-3xl")}>{formatBlueprintValue(metric.value)}</strong>
        {typeof metric.delta === "number" && (
          <span className="text-xs font-bold" style={{ color: metric.delta >= 0 ? "var(--preview-status-ok, #3fbf74)" : "var(--preview-status-danger, #e0484d)" }}>
            {metric.delta >= 0 ? "+" : ""}{metric.delta}%
          </span>
        )}
      </div>
      {metric.hint && <p className="mt-2 text-xs text-muted-soft">{metric.hint}</p>}
    </article>
  );
}

export function BlueprintMetricGrid({ metrics, columns = 4 }: { metrics: BlueprintMetric[]; columns?: 2 | 3 | 4 | 5 | 6 }) {
  const columnClass = {
    2: "lg:grid-cols-2",
    3: "lg:grid-cols-3",
    4: "lg:grid-cols-4",
    5: "lg:grid-cols-5",
    6: "lg:grid-cols-6",
  }[columns];
  return (
    <div className={cn("grid grid-cols-1 gap-3 sm:grid-cols-2", columnClass)}>
      {metrics.map((metric) => <BlueprintMetricCard key={metric.id} metric={metric} />)}
    </div>
  );
}

export function BlueprintKeyValueGrid({ fields, columns = 2 }: { fields: BlueprintField[]; columns?: 1 | 2 | 3 }) {
  const columnClass = columns === 3 ? "md:grid-cols-3" : columns === 2 ? "md:grid-cols-2" : "grid-cols-1";
  return (
    <dl className={cn("grid gap-x-6 gap-y-4", columnClass)}>
      {fields.map((field) => (
        <div key={field.key} className="min-w-0 border-b border-line/70 pb-3">
          <dt className="text-[11px] font-bold uppercase tracking-wide text-muted-soft">{field.label ?? humanizeBlueprintKey(field.key)}</dt>
          <dd className={cn("mt-1 break-words text-sm", field.emphasize && "font-extrabold text-foreground")}>
            {formatBlueprintValue(field.value)}
          </dd>
        </div>
      ))}
    </dl>
  );
}

export function BlueprintEmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="grid min-h-40 place-items-center rounded-[14px] border border-dashed border-line-strong bg-white/[0.015] px-5 py-10 text-center">
      <div>
        <p className="text-sm font-extrabold">{title}</p>
        {description && <p className="mx-auto mt-2 max-w-md text-xs leading-5 text-muted-soft">{description}</p>}
        {action && <div className="mt-4">{action}</div>}
      </div>
    </div>
  );
}

export function BlueprintActionButtons({ actions, onAction }: { actions: BlueprintAction[]; onAction?: (action: BlueprintAction) => void }) {
  return (
    <div className="flex flex-wrap gap-2">
      {actions.map((action) => {
        const variant: ButtonVariant = action.tone === "primary"
          ? "primary"
          : action.tone === "danger"
            ? "danger"
            : action.tone === "ghost"
              ? "ghost"
              : "secondary";
        return (
          <Button key={action.id} size="small" variant={variant} disabled={action.disabled} onClick={() => onAction?.(action)}>
            {action.label}
          </Button>
        );
      })}
    </div>
  );
}

export function BlueprintProgressBar({ value, label }: { value: number; label?: string }) {
  const normalized = Math.max(0, Math.min(100, value));
  return (
    <div>
      {label && <div className="mb-1.5 flex justify-between text-xs text-muted-soft"><span>{label}</span><span>{normalized}%</span></div>}
      <div className="h-2 overflow-hidden rounded-full bg-white/[0.07]">
        <div className="h-full rounded-full bg-brand transition-[width]" style={{ width: `${normalized}%` }} />
      </div>
    </div>
  );
}

export function BlueprintSparkBars({ values, tone = "brand" }: { values: number[]; tone?: BlueprintTone }) {
  const max = Math.max(...values, 1);
  const color = tone === "danger"
    ? "var(--preview-status-danger, #e0484d)"
    : tone === "warn"
      ? "var(--preview-status-warn, #d98c12)"
      : tone === "ok"
        ? "var(--preview-status-ok, #3fbf74)"
        : "var(--preview-blueprint-brand, var(--brand, #9ef01a))";
  return (
    <div className="flex h-14 items-end gap-1" aria-hidden>
      {values.map((value, index) => (
        <span
          key={`${index}-${value}`}
          className="min-w-1 flex-1 rounded-t-sm opacity-80"
          style={{ height: `${Math.max(6, (value / max) * 100)}%`, background: color }}
        />
      ))}
    </div>
  );
}
