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
} from "@/lib/types";

export interface PreviewRuntimeConfig {
  // 분석된 OpenAPI 문서의 서버 URL — 렌더러가 실제로 fetch를 호출할 때 이 값 + capability.path를 합친다.
  apiBaseUrl: string;
  authToken: string | null;
  onAuthTokenChange: (token: string | null) => void;
}
