"use client";

import { useEffect, useState } from "react";
import { PageLoader } from "@/components/ui/loader";
import { callCapability, formatCellValue } from "./api";
import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

// Direction Recovery Change Request §9.2 "full-detail-page" — side-detail-panel(DetailPanel)과
// 데이터 요구조건(DETAIL capability)은 동일하고, 좁은 사이드 칼럼 대신 필드를 카드형 그리드로 넓게
// 펼쳐 보여준다. PreviewPageRenderer가 이 componentId일 때 목록을 감추고 전체 폭으로 마운트한다.
export function FullDetailPage({
  capability,
  config,
  id,
}: {
  capability: PreviewCapability;
  config: PreviewRuntimeConfig;
  id: string;
}) {
  const [data, setData] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.resolve().then(async () => {
      if (cancelled) {
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const result = await callCapability(config, capability, { pathParams: { id } });
        if (!cancelled) {
          setData(result as Record<string, unknown>);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "상세 정보를 불러오지 못했습니다");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    });
    return () => {
      cancelled = true;
    };
  }, [capability, config, id]);

  if (loading) {
    return <PageLoader label="불러오는 중" />;
  }
  if (error) {
    return <p className="text-sm text-danger">{error}</p>;
  }
  if (!data) {
    return null;
  }

  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
      {Object.entries(data).map(([key, value]) => (
        <div key={key} className="rounded-md border border-line-strong bg-white/[0.02] p-3">
          <p className="text-xs font-bold text-muted-soft">{key}</p>
          <p className="mt-1 break-all font-mono text-sm">{formatCellValue(value)}</p>
        </div>
      ))}
    </div>
  );
}
