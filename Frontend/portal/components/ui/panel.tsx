import { HTMLAttributes, ReactNode } from "react";
import { cn } from "./cn";

export function Panel({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("motion-panel rounded-panel border border-line bg-panel", className)} {...props} />;
}

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <Panel className={cn("p-5", className)} {...props} />;
}

export function PanelHeader({
  title,
  description,
  action,
  className,
}: {
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("mb-[17px] flex items-center justify-between gap-4", className)}>
      <div>
        <h2 className="m-0 mb-[5px] text-lg font-bold">{title}</h2>
        {description && <p className="m-0 text-sm text-muted">{description}</p>}
      </div>
      {action}
    </div>
  );
}
