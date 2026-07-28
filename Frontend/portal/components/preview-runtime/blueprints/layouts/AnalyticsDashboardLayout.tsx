import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";

export function AnalyticsDashboardLayout({
  title,
  description,
  controls,
  summary,
  primary,
  secondary,
  activity,
  className,
}: {
  title: ReactNode;
  description?: ReactNode;
  controls?: ReactNode;
  summary: ReactNode;
  primary: ReactNode;
  secondary?: ReactNode;
  activity?: ReactNode;
  className?: string;
}) {
  return (
    <section className={cn("space-y-4", className)}>
      <header className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-[10px] font-extrabold uppercase tracking-[0.18em] text-brand-strong">Analytics</p>
          <h2 className="mt-1 text-2xl font-black">{title}</h2>
          {description && <p className="mt-1 text-sm text-muted-soft">{description}</p>}
        </div>
        {controls}
      </header>
      {summary}
      <div className={cn("grid gap-4", Boolean(secondary) && "xl:grid-cols-[minmax(0,1.7fr)_minmax(280px,0.8fr)]")}>
        <div className="min-w-0">{primary}</div>
        {secondary && <div className="min-w-0">{secondary}</div>}
      </div>
      {activity}
    </section>
  );
}
