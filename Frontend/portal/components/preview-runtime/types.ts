// Auto Preview MVP(GamjaBox_2.0_Key_Features.md 1단계) Phase C — Blueprint Schema/Registry/Slot
// 시스템을 만들지 않고, 백엔드 Ops의 PreviewAnalysisResult(capabilities/pages)를 그대로 받아
// 고정된 소수 패턴으로만 렌더링하는 최소 Runtime Renderer.
//
// Capability/PageDraft 타입은 lib/types.ts가 정본(마법사 페이지의 analyze/review/deploy 응답과
// 같은 타입을 공유해야 하므로) — 여기서는 재선언하지 않고 그대로 재수출만 한다.
export type {
  PreviewCapabilityType as CapabilityType,
  PreviewPageSkeletonType as PageSkeletonType,
  PreviewCapability,
  PreviewPageDraft as PreviewPage,
  PreviewAuthStrategy,
  PreviewGenerationPurpose as Purpose,
} from "@/lib/types";

import type { PreviewAuthStrategy, PreviewGenerationPurpose } from "@/lib/types";

export interface PreviewRuntimeConfig {
  // 분석된 OpenAPI 문서의 서버 URL — 렌더러가 실제로 fetch를 호출할 때 이 값 + capability.path를 합친다.
  apiBaseUrl: string;
  authToken: string | null;
  onAuthTokenChange: (token: string | null) => void;
  // 로그인으로 받은 토큰을 나머지 모든 보호된 요청에 어떻게 실어 보낼지(§9) — 분석 결과 전체에 하나만
  // 있다. Bearer만 가정하던 예전 방식은 API Key 인증 API에서 요청이 항상 실패했다.
  authStrategy: PreviewAuthStrategy;
  // Direction Recovery Change Request Increment 4 — compileBlocks가 목적별 Component Variant를
  // 고르는 데 쓴다. 없으면 기본 Variant(resource-table)로 컴파일된다.
  purpose: PreviewGenerationPurpose | null;
  // GamjaBox_2.0_Key_Features.md 41절 MVP 출력 "요청·응답 확인" — callCapability가 호출마다 한 번씩
  // 호출해준다. 없으면(예: preview-demo) 그냥 기록하지 않는다.
  onApiCall?: (entry: ApiCallLogEntry) => void;
}

export interface ApiCallLogEntry {
  id: string;
  method: string;
  url: string;
  status: number | null;
  requestBody: unknown;
  responseBody: unknown;
  error: string | null;
  timestamp: number;
}
