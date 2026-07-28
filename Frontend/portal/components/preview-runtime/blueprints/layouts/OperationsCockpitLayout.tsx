import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";

export function OperationsCockpitLayout({
  title,
  environment,
  controls,
  health,
  topology,
  events,
  runbook,
  className,
}: {
  title: ReactNode;
  environment?: ReactNode;
  controls?: ReactNode;
  health: ReactNode;
  topology: ReactNode;
  events: ReactNode;
  runbook?: ReactNode;
  className?: string;
}) {
  return (
    <section className={cn("space-y-4", className)}>
      <header className="flex flex-col gap-3 rounded-[18px] border border-line bg-panel px-5 py-4 md:flex-row md:items-center md:justify-between">
        <div>
          <p className="text-[10px] font-extrabold uppercase tracking-[0.18em] text-brand-strong">Operations Cockpit</p>
          <div className="mt-1 flex items-center gap-3"><h2 className="text-xl font-black">{title}</h2>{environment}</div>
        </div>
        {controls}
      </header>
      {health}
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.45fr)_minmax(320px,0.8fr)]">
        <div className="min-w-0 space-y-4">{topology}{runbook}</div>
        <aside className="min-w-0">{events}</aside>
      </div>
    </section>
  );
}
