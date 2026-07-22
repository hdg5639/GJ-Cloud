import { CSSProperties, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";
import { cn } from "./cn";

const controlClass =
  "w-full min-h-[42px] rounded-[9px] border border-line-strong bg-panel px-3 text-sm text-foreground outline-none placeholder:text-muted-soft focus:border-brand focus:ring-2 focus:ring-brand/20";

const selectChevronStyle: CSSProperties = {
  backgroundImage:
    "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 20 20' fill='none'%3E%3Cpath d='m5 7.5 5 5 5-5' stroke='%239aa39a' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E\")",
  backgroundPosition: "right 12px center",
  backgroundRepeat: "no-repeat",
  backgroundSize: "14px 14px",
};

export function Field({
  label,
  htmlFor,
  className,
  children,
}: {
  label: ReactNode;
  htmlFor?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <label htmlFor={htmlFor} className={cn("mb-3.5 grid gap-[7px] text-xs font-bold text-muted", className)}>
      {label}
      {children}
    </label>
  );
}

export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={cn(controlClass, className)} {...props} />;
}

export function Select({ className, style, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      className={cn(controlClass, "appearance-none bg-panel pr-10", className)}
      style={{ ...selectChevronStyle, ...style }}
      {...props}
    />
  );
}

export function Textarea({ className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={cn(controlClass, "min-h-[100px] py-3", className)} {...props} />;
}

export function SearchInput({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div className={cn("flex h-10 w-full items-center rounded-[10px] border border-line-strong px-3", className)}>
      <span aria-hidden className="text-muted-soft">
        ⌕
      </span>
      <input className="w-full border-0 pl-2 text-sm outline-none" {...props} />
    </div>
  );
}
