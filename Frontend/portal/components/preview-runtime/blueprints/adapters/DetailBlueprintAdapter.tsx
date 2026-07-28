"use client";

import { PageLoader } from "@/components/ui/loader";
import { statusFieldOf } from "../../status";
import type { PreviewCapability, PreviewRuntimeConfig } from "../../types";
import { InfrastructureResourceDetail } from "../details";
import { toFields } from "./map";
import { useDetailRecord } from "./useResource";

// 상세 계열 Blueprint 파츠를 실제 API에 연결한다(Phase A: infrastructure-resource-detail).
// 상세 응답을 봉투 언랩해 파츠에 넘긴다. 커맨드/메트릭/활동은 Phase A 범위 밖이라 비운다
// (파츠는 빈 배열에도 안전하게 렌더된다).
export function DetailBlueprintAdapter({
  componentId,
  capability,
  config,
  id,
  refreshKey,
}: {
  componentId: string;
  capability: PreviewCapability;
  config: PreviewRuntimeConfig;
  id: string;
  refreshKey?: number;
}) {
  const { record, loading, error } = useDetailRecord(capability, config, id, refreshKey);

  if (loading) return <PageLoader label="불러오는 중" />;
  if (error) return <p className="text-sm text-danger">{error}</p>;
  if (!record) return null;

  const statusKey = statusFieldOf(record);

  // componentId는 지금 infrastructure-resource-detail 하나지만, 이후 상세 파츠가 늘면 여기서 분기한다.
  void componentId;

  return (
    <InfrastructureResourceDetail
      resource={record}
      metrics={[]}
      fields={toFields(record, [statusKey ?? ""])}
      actions={[]}
    />
  );
}
