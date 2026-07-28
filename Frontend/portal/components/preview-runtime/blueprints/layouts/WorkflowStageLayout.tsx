import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";
import type { BlueprintWorkflowStep } from "../core";
import { BlueprintStatusPill } from "../core";

export function WorkflowStageLayout({
  title,
  description,
  steps,
  currentStepId,
  stage,
  summary,
  footer,
  className,
}: {
  title: ReactNode;
  description?: ReactNode;
  steps: BlueprintWorkflowStep[];
  currentStepId?: string;
  stage: ReactNode;
  summary?: ReactNode;
  footer?: ReactNode;
  className?: string;
}) {
  return (
    <section className={cn("overflow-hidden rounded-[18px] border border-line bg-background", className)}>
      <header className="border-b border-line bg-panel px-5 py-5">
        <h2 className="text-xl font-black">{title}</h2>
        {description && <p className="mt-1 text-sm text-muted-soft">{description}</p>}
      </header>
      <div className="grid lg:grid-cols-[260px_minmax(0,1fr)]">
        <aside className="border-b border-line bg-panel p-4 lg:border-b-0 lg:border-r">
          <div className="space-y-2">
            {steps.map((step, index) => {
              const active = step.id === currentStepId || step.status === "ACTIVE";
              return (
                <div key={step.id} className={cn("rounded-[12px] border px-3 py-3", active ? "border-brand/40 bg-brand/8" : "border-transparent") }>
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-extrabold">{index + 1}. {step.label}</span>
                    {step.status && <BlueprintStatusPill value={step.status} />}
                  </div>
                  {step.description && <p className="mt-1 text-[11px] leading-4 text-muted-soft">{step.description}</p>}
                </div>
              );
            })}
          </div>
          {summary && <div className="mt-4 border-t border-line pt-4">{summary}</div>}
        </aside>
        <main className="min-w-0 p-5">{stage}</main>
      </div>
      {footer && <footer className="border-t border-line bg-panel px-5 py-4">{footer}</footer>}
    </section>
  );
}
