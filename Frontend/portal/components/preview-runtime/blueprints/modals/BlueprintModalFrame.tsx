"use client";

import type { CSSProperties, ReactNode } from "react";
import { Modal } from "@/components/ui/modal";
import { cn } from "@/components/ui/cn";

export function BlueprintModalFrame({
  open,
  onClose,
  title,
  description,
  eyebrow,
  size = "md",
  footer,
  style,
  themeId,
  children,
}: {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  description?: ReactNode;
  eyebrow?: ReactNode;
  size?: "sm" | "md" | "lg" | "xl";
  footer?: ReactNode;
  style?: CSSProperties;
  themeId?: string;
  children: ReactNode;
}) {
  const widthClass = size === "sm" ? "max-w-md" : size === "lg" ? "max-w-3xl" : size === "xl" ? "max-w-5xl" : "max-w-xl";
  return (
    <Modal open={open} onClose={onClose}>
      <section
        className={cn("mx-auto max-h-[92vh] w-[min(96vw,1200px)] overflow-hidden rounded-[18px] border border-line bg-background text-foreground shadow-2xl", widthClass)}
        style={style}
        data-blueprint-theme={themeId}
      >
        <header className="flex items-start justify-between gap-4 border-b border-line bg-panel px-5 py-4">
          <div>
            {eyebrow && <p className="text-[10px] font-extrabold uppercase tracking-[0.18em] text-brand-strong">{eyebrow}</p>}
            <h2 className="mt-1 text-lg font-extrabold">{title}</h2>
            {description && <p className="mt-1 text-xs leading-5 text-muted-soft">{description}</p>}
          </div>
          <button type="button" onClick={onClose} className="grid h-8 w-8 shrink-0 place-items-center rounded-[9px] border border-line bg-white/[0.02] text-lg text-muted hover:text-foreground" aria-label="Close">×</button>
        </header>
        <div className="max-h-[calc(92vh-150px)] overflow-auto p-5">{children}</div>
        {footer && <footer className="flex flex-wrap items-center justify-end gap-2 border-t border-line bg-panel px-5 py-4">{footer}</footer>}
      </section>
    </Modal>
  );
}
