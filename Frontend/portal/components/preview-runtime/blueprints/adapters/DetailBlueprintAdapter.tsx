"use client";

import { PageLoader } from "@/components/ui/loader";
import type { PreviewCapability, PreviewRuntimeConfig } from "../../types";
import { renderDetailPart } from "./registry";
import { BlueprintFeedbackPart } from "./BlueprintPartHost";
import { useDetailRecord } from "./useResource";

// 상세 계열 Blueprint 파츠를 실제 API에 연결한다. 상세 응답을 봉투 언랩해 레지스트리 render로 넘긴다.
export function DetailBlueprintAdapter({
  componentId,
  capability,
  config,
  id,
  refreshKey,
  feedbackComponentId,
}: {
  componentId: string;
  capability: PreviewCapability;
  config: PreviewRuntimeConfig;
  id: string;
  refreshKey?: number;
  feedbackComponentId?: string;
}) {
  const { record, loading, error } = useDetailRecord(capability, config, id, refreshKey);

  if (loading) return feedbackComponentId ? <BlueprintFeedbackPart componentId={feedbackComponentId} details="상세 정보를 불러오는 중입니다." /> : <PageLoader label="불러오는 중" />;
  if (error) return feedbackComponentId ? <BlueprintFeedbackPart componentId={feedbackComponentId} details={error} /> : <p className="text-sm text-danger">{error}</p>;
  if (!record) return null;

  return <>{renderDetailPart(componentId, { record })}</>;
}
