import { ReactNode } from "react";
import { cn } from "./cn";

export function ListItem({
  title,
  subtitle,
  action,
  className,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex items-center justify-between gap-3 rounded-[11px] border border-line bg-white/[0.02] p-[13px]",
        className
      )}
    >
      <div>
        <strong className="block font-bold">{title}</strong>
        {subtitle && <span className="mt-0.5 block text-[11px] text-muted-soft">{subtitle}</span>}
      </div>
      {action}
    </div>
  );
}

export function ListStack({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("grid gap-[9px]", className)}>{children}</div>;
}
