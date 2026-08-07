import { CSSProperties, ReactNode } from "react";
import { cn } from "./cn";

export function StatGrid({
  cols = 4,
  className,
  children,
}: {
  cols?: number;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div
      className={cn(
        "mb-4 grid grid-cols-2 gap-3.5 lg:grid-cols-[repeat(var(--stat-cols),minmax(0,1fr))]",
        className
      )}
      style={{ "--stat-cols": cols } as CSSProperties}
    >
      {children}
    </div>
  );
}

export function StatCard({
  label,
  value,
  hint,
  compact = false,
  className,
}: {
  label: ReactNode;
  value: ReactNode;
  hint?: ReactNode;
  compact?: boolean;
  className?: string;
}) {
  return (
    <article
      className={cn(
        "rounded-[15px] border border-line bg-panel bg-[linear-gradient(180deg,rgba(255,255,255,0.03),rgba(255,255,255,0))]",
        compact ? "min-h-[92px] p-4" : "min-h-[120px] p-[19px]",
        className
      )}
    >
      <span className="block text-[13px] text-muted">{label}</span>
      <strong className={cn("block", compact ? "my-2 text-lg" : "my-3 text-[29px]")}>{value}</strong>
      {hint && <small className="block text-xs text-muted-soft">{hint}</small>}
    </article>
  );
}
