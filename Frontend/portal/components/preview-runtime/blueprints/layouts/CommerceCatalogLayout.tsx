import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";

export function CommerceCatalogLayout({
  title,
  description,
  categoryRail,
  toolbar,
  catalog,
  cartSummary,
  className,
}: {
  title: ReactNode;
  description?: ReactNode;
  categoryRail?: ReactNode;
  toolbar?: ReactNode;
  catalog: ReactNode;
  cartSummary?: ReactNode;
  className?: string;
}) {
  return (
    <section className={cn("space-y-4", className)}>
      <header className="rounded-[18px] border border-line bg-panel px-5 py-5">
        <p className="text-[10px] font-extrabold uppercase tracking-[0.18em] text-brand-strong">Commerce</p>
        <div className="mt-2 flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
          <div>
            <h2 className="text-2xl font-black">{title}</h2>
            {description && <p className="mt-1 text-sm text-muted-soft">{description}</p>}
          </div>
          {toolbar}
        </div>
        {categoryRail && <div className="mt-4 overflow-x-auto">{categoryRail}</div>}
      </header>
      <div className={cn("grid gap-4", Boolean(cartSummary) && "xl:grid-cols-[minmax(0,1fr)_320px]")}>
        <main className="min-w-0">{catalog}</main>
        {cartSummary && <aside className="min-w-0">{cartSummary}</aside>}
      </div>
    </section>
  );
}
