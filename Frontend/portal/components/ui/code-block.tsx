import { HTMLAttributes } from "react";
import { cn } from "./cn";

export function CodeBlock({ className, ...props }: HTMLAttributes<HTMLPreElement>) {
  return (
    <pre
      className={cn(
        "overflow-auto whitespace-pre rounded-[11px] border border-line bg-[#f7f9f8] p-4 font-mono text-xs leading-[1.6]",
        className
      )}
      {...props}
    />
  );
}

export function InlineCode({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        "rounded-[10px] border border-line bg-[#f7f9f8] px-[14px] py-[13px] font-mono text-xs leading-[1.6]",
        className
      )}
      {...props}
    />
  );
}
