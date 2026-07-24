import { HTMLAttributes, TdHTMLAttributes, ThHTMLAttributes } from "react";
import { cn } from "./cn";
import { StatusDot } from "./badge";

export function Table({ className, ...props }: HTMLAttributes<HTMLTableElement>) {
  return (
    <div className="overflow-x-auto">
      <table className={cn("w-full min-w-[560px] border-collapse", className)} {...props} />
    </div>
  );
}

export function Th({ className, ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      className={cn(
        "whitespace-nowrap border-b border-line bg-white/[0.02] px-4 py-[15px] text-left text-[11px] font-medium text-muted",
        className
      )}
      {...props}
    />
  );
}

export function Td({ className, ...props }: TdHTMLAttributes<HTMLTableCellElement>) {
  return (
    <td
      className={cn(
        "whitespace-nowrap border-b border-line px-4 py-[15px] text-[13px] text-[#dfe5df]",
        className
      )}
      {...props}
    />
  );
}

export function RowLink({
  title,
  subtitle,
  dotOff,
  onClick,
}: {
  title: string;
  subtitle?: string;
  dotOff?: boolean;
  onClick?: () => void;
}) {
  return (
    <button type="button" onClick={onClick} className="flex items-center gap-2.5 border-0 bg-transparent text-left">
      <StatusDot off={dotOff} />
      <span>
        <strong className="block font-bold">{title}</strong>
        {subtitle && <small className="mt-0.5 block text-muted-soft">{subtitle}</small>}
      </span>
    </button>
  );
}
