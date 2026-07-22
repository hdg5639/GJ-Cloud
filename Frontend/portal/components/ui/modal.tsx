"use client";

import { ReactNode, useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { cn } from "./cn";

const TRANSITION_MS = 200;

export function Modal({ open, onClose, children }: { open: boolean; onClose: () => void; children: ReactNode }) {
  const [mounted, setMounted] = useState(open);
  const [visible, setVisible] = useState(false);

  // open이 꺼져도 즉시 언마운트하지 않고 닫힘 트랜지션이 끝난 뒤 제거 — 그래야 등장뿐 아니라 퇴장도 자연스럽게 보임
  useEffect(() => {
    if (open) {
      // 열림 트랜지션 시작 전 DOM에 먼저 마운트해야 하므로(퇴장 애니메이션과 대칭) 렌더 중 계산으로 대체 불가
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setMounted(true);
      const raf = requestAnimationFrame(() => setVisible(true));
      return () => cancelAnimationFrame(raf);
    }
    setVisible(false);
    const timeout = setTimeout(() => setMounted(false), TRANSITION_MS);
    return () => clearTimeout(timeout);
  }, [open]);

  useEffect(() => {
    if (!mounted) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [mounted, onClose]);

  if (!mounted || typeof document === "undefined") return null;

  return createPortal(
    <div
      className={cn(
        "fixed inset-0 z-[100] flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm transition-opacity duration-200 ease-out",
        visible ? "opacity-100" : "opacity-0"
      )}
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className={cn(
          "w-full transition-[transform,opacity] duration-200 ease-out",
          visible ? "translate-y-0 scale-100 opacity-100" : "translate-y-2 scale-[0.97] opacity-0"
        )}
      >
        {children}
      </div>
    </div>,
    document.body
  );
}
