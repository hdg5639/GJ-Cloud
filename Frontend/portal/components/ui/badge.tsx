import { HTMLAttributes } from "react";
import { cn } from "./cn";

export function Badge({ className, ...props }: HTMLAttributes<HTMLSpanElement>) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-[7px] bg-accent-soft px-2 py-[5px] text-[11px] font-extrabold text-accent",
        className
      )}
      {...props}
    />
  );
}

type StatusTone = "ok" | "off";

export function StatusBadge({
  tone,
  className,
  ...props
}: HTMLAttributes<HTMLSpanElement> & { tone: StatusTone }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-[7px] px-2 py-[5px] text-[11px] font-extrabold",
        tone === "ok" ? "bg-success-soft text-success" : "bg-white/[0.05] text-muted-soft",
        className
      )}
      {...props}
    />
  );
}

export function StatusDot({ off = false, className }: { off?: boolean; className?: string }) {
  return (
    <span
      className={cn(
        "inline-block h-[9px] w-[9px] shrink-0 rounded-full",
        off ? "bg-white/25 shadow-[0_0_0_4px_rgba(255,255,255,0.06)]" : "bg-success shadow-[0_0_0_4px_var(--success-soft)]",
        className
      )}
    />
  );
}
