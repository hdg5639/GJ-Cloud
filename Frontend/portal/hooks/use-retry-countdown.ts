"use client";

import { useCallback, useEffect, useState } from "react";

export function useRetryCountdown() {
  const [deadline, setDeadline] = useState<number | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState(0);

  useEffect(() => {
    if (deadline === null) return;
    const timer = window.setInterval(() => {
      const next = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
      setRemainingSeconds(next);
      if (next === 0) window.clearInterval(timer);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [deadline]);

  const start = useCallback((seconds: number) => {
    const normalized = Math.max(1, Math.ceil(seconds));
    setDeadline(Date.now() + normalized * 1000);
    setRemainingSeconds(normalized);
  }, []);

  const clear = useCallback(() => {
    setDeadline(null);
    setRemainingSeconds(0);
  }, []);

  return { remainingSeconds, start, clear };
}

export function formatRetryCountdown(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;
  return `${minutes}:${String(remainder).padStart(2, "0")}`;
}
