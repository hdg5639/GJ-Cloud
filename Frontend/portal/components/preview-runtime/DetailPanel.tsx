"use client";

import { useEffect, useState } from "react";
import { PageLoader } from "@/components/ui/loader";
import { callCapability, formatCellValue } from "./api";
import type { PreviewCapability, PreviewRuntimeConfig } from "./types";

export function DetailPanel({
  capability,
  config,
  id,
  refreshKey,
}: {
  capability: PreviewCapability;
  config: PreviewRuntimeConfig;
  id: string;
  // Workflow Composition Phase 2 Change Request §9 "refresh related list or detail bindings after
  // success" — 생성/수정/삭제/커맨드 성공 후 목록(ResourceTable 등)만 refreshKey로 다시 불러오고
  // 이 패널은 그대로였다. 같은 refreshKey를 받아 상세도 같이 다시 불러온다.
  refreshKey?: number;
}) {
  const [data, setData] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    // setState를 effect 본문에서 동기 호출하지 않고 마이크로태스크로 미룸(react-hooks/set-state-in-effect).
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
    // config 객체 전체를 넣으면 부모가 매 렌더마다 새 config를 만들 때(onApiCall 로그 갱신 등)
    // effect가 무한 재발화한다 — 실제 데이터 소스인 apiBaseUrl/authToken만 의존한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [capability, config.apiBaseUrl, config.authToken, id, refreshKey]);

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
    <div className="grid grid-cols-[140px_1fr] gap-y-2 text-sm">
      {Object.entries(data).flatMap(([key, value]) => [
        <div key={`${key}-k`} className="text-muted-soft">
          {key}
        </div>,
        <div key={`${key}-v`} className="break-all font-mono">
          {formatCellValue(value)}
        </div>,
      ])}
    </div>
  );
}
