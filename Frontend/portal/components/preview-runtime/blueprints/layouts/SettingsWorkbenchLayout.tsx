import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";
import type { BlueprintNavItem } from "../core";

export function SettingsWorkbenchLayout({
  title,
  description,
  sections,
  children,
  actions,
  dirty = false,
  className,
  onSelect,
}: {
  title: ReactNode;
  description?: ReactNode;
  sections: BlueprintNavItem[];
  children: ReactNode;
  actions?: ReactNode;
  dirty?: boolean;
  className?: string;
  onSelect?: (item: BlueprintNavItem) => void;
}) {
  return (
    <div className={cn("overflow-hidden rounded-[18px] border border-line bg-panel", className)}>
      <header className="flex flex-col gap-3 border-b border-line px-5 py-4 md:flex-row md:items-center md:justify-between">
        <div>
          <div className="flex items-center gap-2"><h2 className="text-lg font-extrabold">{title}</h2>{dirty && <span className="rounded-full bg-[color-mix(in_srgb,var(--preview-status-warn,#d98c12)_14%,transparent)] px-2 py-1 text-[10px] font-bold text-[var(--preview-status-warn,#d98c12)]">Unsaved</span>}</div>
          {description && <p className="mt-1 text-xs text-muted-soft">{description}</p>}
        </div>
        {actions}
      </header>
      <div className="grid min-h-[560px] lg:grid-cols-[220px_minmax(0,1fr)]">
        <nav className="border-b border-line bg-background p-3 lg:border-b-0 lg:border-r">
          {sections.map((item) => (
            <button key={item.id} type="button" onClick={() => onSelect?.(item)} className={cn("mb-1 w-full rounded-[10px] px-3 py-2.5 text-left text-sm font-semibold", item.active ? "bg-brand/12 text-brand-strong" : "text-muted hover:bg-white/[0.04]")}>{item.label}</button>
          ))}
        </nav>
        <main className="min-w-0 p-5">{children}</main>
      </div>
    </div>
  );
}
