import { useEffect, useState } from "react";
import { callCapability, extractArray, unwrapEnvelope } from "../../api";
import type { PreviewCapability, PreviewRuntimeConfig } from "../../types";

// Blueprint 어댑터 공용 fetch 훅 — 기존 ResourceCardGrid/DetailPanel과 같은 규칙(무한요청 방지를 위해
// config 전체가 아니라 apiBaseUrl/authToken만 의존)으로 목록/상세를 불러온다.

export function useListRows(
  capability: PreviewCapability,
  config: PreviewRuntimeConfig,
  refreshKey?: number,
  search?: string
): { rows: Record<string, unknown>[]; loading: boolean; error: string | null } {
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.resolve().then(async () => {
      if (cancelled) return;
      setLoading(true);
      setError(null);
      const query: Record<string, string> = {};
      if (capability.hasSearch && search) query[capability.searchParam ?? "search"] = search;
      try {
        const result = await callCapability(config, capability, { query });
        if (!cancelled) setRows(extractArray(result, capability.collectionPath));
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : "목록을 불러오지 못했습니다");
      } finally {
        if (!cancelled) setLoading(false);
      }
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [capability, config.apiBaseUrl, config.authToken, search, refreshKey]);

  return { rows, loading, error };
}

export function useDetailRecord(
  capability: PreviewCapability,
  config: PreviewRuntimeConfig,
  id: string,
  refreshKey?: number
): { record: Record<string, unknown> | null; loading: boolean; error: string | null } {
  const [record, setRecord] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.resolve().then(async () => {
      if (cancelled) return;
      setLoading(true);
      setError(null);
      try {
        const result = await callCapability(config, capability, { pathParams: { id } });
        if (!cancelled) setRecord(unwrapEnvelope(result));
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : "상세 정보를 불러오지 못했습니다");
      } finally {
        if (!cancelled) setLoading(false);
      }
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [capability, config.apiBaseUrl, config.authToken, id, refreshKey]);

  return { record, loading, error };
}
