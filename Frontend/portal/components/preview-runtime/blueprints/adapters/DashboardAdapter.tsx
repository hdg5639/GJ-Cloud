"use client";

import { PageLoader } from "@/components/ui/loader";
import type { PreviewCapability, PreviewRuntimeConfig } from "../../types";
import { OperationsHealthDashboard } from "../dashboards";
import { useListRows } from "./useResource";

// 대시보드 계열 Blueprint 파츠를 실제 API에 연결한다(Phase A: operations-health-dashboard).
// 첫 LIST capability의 목록을 services로 넘긴다. metrics/incidents는 Phase A 범위 밖(빈 배열 안전).
export function DashboardAdapter({
  componentId,
  capabilities,
  config,
  refreshKey,
}: {
  componentId: string;
  capabilities: PreviewCapability[];
  config: PreviewRuntimeConfig;
  refreshKey?: number;
}) {
  const primary = capabilities[0];
  const { rows, loading, error } = useListRows(primary, config, refreshKey);

  void componentId;

  if (!primary) return null;
  if (loading) return <PageLoader label="불러오는 중" />;
  if (error) return <p className="text-sm text-danger">{error}</p>;

  return <OperationsHealthDashboard metrics={[]} services={rows} incidents={[]} />;
}
