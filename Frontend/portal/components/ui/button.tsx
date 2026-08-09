import { ButtonHTMLAttributes, forwardRef } from "react";
import { cn } from "./cn";

export type ButtonVariant = "primary" | "secondary" | "danger" | "danger-solid" | "ghost";
export type ButtonSize = "default" | "small";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
}

const variantClasses: Record<ButtonVariant, string> = {
  primary: "border border-brand bg-brand text-[var(--button-primary-ink,#0a0c08)] hover:bg-brand-strong",
  secondary: "border border-line-strong bg-panel text-foreground hover:bg-soft",
  danger: "border border-danger-soft bg-panel text-danger hover:bg-danger/10",
  "danger-solid": "border border-danger bg-danger text-[var(--button-danger-ink,#1a0a0a)] hover:bg-[var(--button-danger-hover,#ff8686)]",
  ghost: "border-0 bg-soft text-brand-strong hover:bg-brand/15",
};

const sizeClasses: Record<ButtonSize, string> = {
  default: "min-h-10 px-4 text-sm",
  small: "min-h-[34px] px-3 text-xs",
};

// Link 등 <button>이 아닌 요소에 동일한 버튼 스타일을 적용할 때 사용 (예: <Link className={buttonClass({variant:"primary"})}>)
export function buttonClass({
  variant = "secondary",
  size = "default",
  className,
}: {
  variant?: ButtonVariant;
  size?: ButtonSize;
  className?: string;
} = {}) {
  return cn(
    "inline-flex items-center justify-center gap-1.5 rounded-[10px] font-bold whitespace-nowrap transition-colors disabled:opacity-60 disabled:cursor-not-allowed",
    variantClasses[variant],
    sizeClasses[size],
    className
  );
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ variant = "secondary", size = "default", className, ...props }, ref) => (
    <button ref={ref} className={buttonClass({ variant, size, className })} {...props} />
  )
);
Button.displayName = "Button";
