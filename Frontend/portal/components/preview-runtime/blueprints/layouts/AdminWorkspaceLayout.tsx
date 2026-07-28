import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";
import type { BlueprintNavItem } from "../core";

export function AdminWorkspaceLayout({
  title,
  description,
  navigation,
  headerActions,
  toolbar,
  aside,
  children,
  footer,
  className,
  onNavigate,
}: {
  title: ReactNode;
  description?: ReactNode;
  navigation: BlueprintNavItem[];
  headerActions?: ReactNode;
  toolbar?: ReactNode;
  aside?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  className?: string;
  onNavigate?: (item: BlueprintNavItem) => void;
}) {
  return (
    <div className={cn("grid min-h-[640px] overflow-hidden rounded-[18px] border border-line bg-background lg:grid-cols-[230px_minmax(0,1fr)]", className)}>
      <aside className="border-b border-line bg-panel lg:border-b-0 lg:border-r">
        <div className="border-b border-line px-5 py-5">
          <p className="text-[10px] font-extrabold uppercase tracking-[0.2em] text-brand-strong">Administration</p>
          <h2 className="mt-2 text-base font-extrabold">{title}</h2>
          {description && <p className="mt-1 text-xs leading-5 text-muted-soft">{description}</p>}
        </div>
        <nav className="grid gap-1 p-3">
          {navigation.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => onNavigate?.(item)}
              className={cn(
                "flex min-h-10 items-center justify-between gap-3 rounded-[10px] px-3 text-left text-sm font-semibold transition-colors",
                item.active ? "bg-brand/12 text-brand-strong" : "text-muted hover:bg-white/[0.04] hover:text-foreground"
              )}
            >
              <span className="min-w-0 truncate">{item.label}</span>
              {item.badge !== undefined && <span className="rounded-full bg-white/[0.07] px-2 py-0.5 text-[10px] tabular-nums">{item.badge}</span>}
            </button>
          ))}
        </nav>
      </aside>
      <section className="min-w-0">
        <header className="flex flex-col gap-3 border-b border-line bg-panel px-5 py-4 md:flex-row md:items-center md:justify-between">
          <div>
            <h1 className="text-lg font-extrabold">{title}</h1>
            {description && <p className="mt-1 text-xs text-muted-soft">{description}</p>}
          </div>
          {headerActions}
        </header>
        {toolbar && <div className="border-b border-line bg-white/[0.015] px-5 py-3">{toolbar}</div>}
        <div className={cn("grid gap-4 p-5", Boolean(aside) && "xl:grid-cols-[minmax(0,1fr)_320px]")}>
          <main className="min-w-0">{children}</main>
          {aside && <aside className="min-w-0">{aside}</aside>}
        </div>
        {footer && <footer className="border-t border-line px-5 py-4 text-xs text-muted-soft">{footer}</footer>}
      </section>
    </div>
  );
}
