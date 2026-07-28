"use client";

import { PageLoader } from "@/components/ui/loader";
import type { PreviewCapability, PreviewRuntimeConfig } from "../../types";
import { renderDashboardPart } from "./registry";
import { BlueprintFeedbackPart } from "./BlueprintPartHost";
import { useListRows } from "./useResource";

// 대시보드 계열 Blueprint 파츠를 실제 API에 연결한다. 첫 LIST capability의 목록을 레지스트리 render로 넘긴다.
export function DashboardAdapter({
  componentId,
  capabilities,
  config,
  refreshKey,
  feedbackComponentId,
}: {
  componentId: string;
  capabilities: PreviewCapability[];
  config: PreviewRuntimeConfig;
  refreshKey?: number;
  feedbackComponentId?: string;
}) {
  const primary = capabilities[0];
  const { rows, loading, error } = useListRows(primary, config, refreshKey);

  if (!primary) return null;
  if (loading) return feedbackComponentId ? <BlueprintFeedbackPart componentId={feedbackComponentId} details="대시보드를 불러오는 중입니다." /> : <PageLoader label="불러오는 중" />;
  if (error) return feedbackComponentId ? <BlueprintFeedbackPart componentId={feedbackComponentId} details={error} /> : <p className="text-sm text-danger">{error}</p>;

  return <>{renderDashboardPart(componentId, { rows })}</>;
}
