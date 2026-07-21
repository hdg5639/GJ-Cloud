"use client";

import { ReactNode } from "react";
import { cn } from "./cn";
import { Modal } from "./modal";

export interface WizardStep {
  key: string;
  label: string;
}

export function Wizard({
  open,
  onClose,
  title,
  description,
  steps,
  currentStep,
  onStepClick,
  footer,
  children,
}: {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  description?: ReactNode;
  steps: WizardStep[];
  currentStep: number;
  onStepClick?: (index: number) => void;
  footer: ReactNode;
  children: ReactNode;
}) {
  return (
    <Modal open={open} onClose={onClose}>
      <div className="grid h-[min(760px,92vh)] w-[min(1040px,96vw)] grid-rows-[auto_auto_1fr_auto] overflow-hidden rounded-[20px] bg-background">
        <div className="flex items-center justify-between border-b border-line bg-panel px-6 py-[21px]">
          <div>
            <h2 className="m-0 mb-[5px] text-xl font-bold">{title}</h2>
            {description && <p className="m-0 text-sm text-muted">{description}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="h-[34px] w-[34px] rounded-[9px] border-0 bg-transparent text-lg hover:bg-white/[0.06]"
          >
            ×
          </button>
        </div>

        <div className="flex flex-wrap justify-center gap-0 border-b border-line bg-panel p-[18px]">
          {steps.map((step, i) => (
            <div key={step.key} className="flex items-center">
              <button
                type="button"
                onClick={() => onStepClick?.(i)}
                className={cn(
                  "flex items-center gap-2 border-0 bg-transparent text-sm",
                  i <= currentStep ? "text-brand-strong" : "text-muted-soft"
                )}
              >
                <span
                  className={cn(
                    "grid h-[27px] w-[27px] place-items-center rounded-full text-xs font-extrabold",
                    i <= currentStep ? "bg-brand text-[#0a0c08]" : "bg-white/[0.06] text-muted-soft"
                  )}
                >
                  {i + 1}
                </span>
                {step.label}
              </button>
              {i < steps.length - 1 && <i className="mx-2.5 h-px w-[70px] bg-line-strong" />}
            </div>
          ))}
        </div>

        <div className="overflow-auto p-6">
          <div className="mx-auto max-w-[1040px]">{children}</div>
        </div>

        <div className="flex items-center gap-2 border-t border-line bg-panel px-[22px] py-[15px]">{footer}</div>
      </div>
    </Modal>
  );
}
