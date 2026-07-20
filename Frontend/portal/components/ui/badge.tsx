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
        tone === "ok" ? "bg-soft text-brand-strong" : "bg-[#eef1ef] text-[#667169]",
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
        off ? "bg-[#a9b1ac] shadow-[0_0_0_4px_#f0f2f1]" : "bg-brand shadow-[0_0_0_4px_var(--soft)]",
        className
      )}
    />
  );
}
