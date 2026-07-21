import { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";
import { cn } from "./cn";

const controlClass =
  "w-full min-h-[42px] rounded-[9px] border border-line-strong bg-panel px-3 text-sm text-foreground outline-none placeholder:text-muted-soft focus:border-brand focus:ring-2 focus:ring-brand/20";

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

export function Select({ className, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className={cn(controlClass, "bg-panel", className)} {...props} />;
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
