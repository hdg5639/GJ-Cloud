"use client";

import { useEffect, useState } from "react";
import { callCapability, extractCount } from "./api";
import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

interface CountState {
  loading: boolean;
  value: number | null;
  error: string | null;
}

// GamjaBox_2.0_Key_Features.md 41절 MVP Skeleton "Dashboard" — 어떤 리소스가 중요한지 판단하지 않고,
// PageDraftGenerator가 모아준 LIST capability마다 개수 카드만 기계적으로 보여준다.
export function DashboardView({
  capabilities,
  config,
}: {
  capabilities: PreviewCapability[];
  config: PreviewRuntimeConfig;
}) {
  const [counts, setCounts] = useState<Record<string, CountState>>({});

  useEffect(() => {
    let cancelled = false;
    capabilities.forEach((capability) => {
      callCapability(config, capability)
        .then((result) => {
          if (cancelled) return;
          setCounts((prev) => ({
            ...prev,
            [capability.id]: {
              loading: false,
              value: extractCount(result, capability.totalCountPath, capability.collectionPath),
              error: null,
            },
          }));
        })
        .catch((err) => {
          if (cancelled) return;
          setCounts((prev) => ({
            ...prev,
            [capability.id]: { loading: false, value: null, error: err instanceof Error ? err.message : "불러오기 실패" },
          }));
        });
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [capabilities, config.apiBaseUrl, config.authToken]);

  if (capabilities.length === 0) {
    return <p className="text-sm text-danger">이 페이지에 표시할 목록 capability가 없습니다.</p>;
  }

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      {capabilities.map((capability) => {
        const state = counts[capability.id] ?? { loading: true, value: null, error: null };
        return (
          <div key={capability.id} className="rounded-md border border-line-strong bg-white/[0.02] p-4">
            <p className="text-xs font-bold text-muted-soft">{capability.resourceName}</p>
            <p className="mt-1 text-2xl font-extrabold">
              {state.loading ? "…" : state.error ? "—" : (state.value ?? "—")}
            </p>
            {state.error && <p className="mt-1 text-[11px] text-danger">{state.error}</p>}
          </div>
        );
      })}
    </div>
  );
}
