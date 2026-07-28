import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";

export function ContentStudioLayout({
  title,
  status,
  navigation,
  editor,
  inspector,
  preview,
  actions,
  className,
}: {
  title: ReactNode;
  status?: ReactNode;
  navigation?: ReactNode;
  editor: ReactNode;
  inspector?: ReactNode;
  preview?: ReactNode;
  actions?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("overflow-hidden rounded-[18px] border border-line bg-background", className)}>
      <header className="flex flex-col gap-3 border-b border-line bg-panel px-5 py-4 md:flex-row md:items-center md:justify-between">
        <div className="flex min-w-0 items-center gap-3">
          <div className="min-w-0">
            <p className="text-[10px] font-extrabold uppercase tracking-[0.18em] text-brand-strong">Content Studio</p>
            <h2 className="mt-1 truncate text-lg font-extrabold">{title}</h2>
          </div>
          {status}
        </div>
        {actions}
      </header>
      {navigation && <div className="border-b border-line bg-white/[0.015] px-5 py-3">{navigation}</div>}
      <div className={cn("grid min-h-[620px]", inspector || preview ? "xl:grid-cols-[minmax(0,1fr)_360px]" : "grid-cols-1")}>
        <main className="min-w-0 p-5">{editor}</main>
        {(inspector || preview) && (
          <aside className="border-t border-line bg-panel p-4 xl:border-l xl:border-t-0">
            <div className="space-y-4">{inspector}{preview}</div>
          </aside>
        )}
      </div>
    </div>
  );
}
