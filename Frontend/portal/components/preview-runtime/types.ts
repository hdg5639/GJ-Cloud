// Auto Preview MVP(GamjaBox_2.0_Key_Features.md 1단계) Phase C — Blueprint Schema/Registry/Slot
// 시스템을 만들지 않고, 백엔드 Ops의 PreviewAnalysisResult(capabilities/pages)를 그대로 받아
// 고정된 소수 패턴으로만 렌더링하는 최소 Runtime Renderer. 필드 이름은 Backend/Ops의
// Capability/PageDraft record와 1:1로 맞춰뒀다(application/preview/analysis 패키지 참고).

export type CapabilityType = "LIST" | "DETAIL" | "CREATE" | "UPDATE" | "DELETE" | "LOGIN";
export type PageSkeletonType = "AUTH_PAGE" | "RESOURCE_LIST" | "LIST_DETAIL" | "DASHBOARD";

export interface PreviewCapability {
  id: string;
  resourceName: string;
  type: CapabilityType;
  operationId: string | null;
  path: string;
  method: string;
  hasSearch: boolean;
  hasSort: boolean;
  hasPagination: boolean;
  confidence: string;
  evidence: string[];
  fields: string[];
}

export interface PreviewPage {
  id: string;
  title: string;
  skeleton: PageSkeletonType;
  capabilityIds: string[];
}

export interface PreviewRuntimeConfig {
  // 분석된 OpenAPI 문서의 서버 URL — 렌더러가 실제로 fetch를 호출할 때 이 값 + capability.path를 합친다.
  apiBaseUrl: string;
  authToken: string | null;
  onAuthTokenChange: (token: string | null) => void;
}
