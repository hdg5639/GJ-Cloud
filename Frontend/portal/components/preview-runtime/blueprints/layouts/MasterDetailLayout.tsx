import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";

export function MasterDetailLayout({
  title,
  toolbar,
  master,
  detail,
  detailOpen = true,
  className,
}: {
  title?: ReactNode;
  toolbar?: ReactNode;
  master: ReactNode;
  detail?: ReactNode;
  detailOpen?: boolean;
  className?: string;
}) {
  return (
    <section className={cn("overflow-hidden rounded-[18px] border border-line bg-panel", className)}>
      {(title || toolbar) && (
        <header className="flex flex-col gap-3 border-b border-line px-5 py-4 md:flex-row md:items-center md:justify-between">
          {title && <h2 className="text-lg font-extrabold">{title}</h2>}
          {toolbar}
        </header>
      )}
      <div className={cn("grid min-h-[560px]", detailOpen && detail ? "xl:grid-cols-[minmax(0,1fr)_minmax(320px,0.72fr)]" : "grid-cols-1")}>
        <main className="min-w-0 p-4">{master}</main>
        {detailOpen && detail && <aside className="border-t border-line bg-background p-4 xl:border-l xl:border-t-0">{detail}</aside>}
      </div>
    </section>
  );
}
