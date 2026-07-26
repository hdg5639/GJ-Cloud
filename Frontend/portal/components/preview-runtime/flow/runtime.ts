import { callCapability } from "../api";
import { findCapabilityById } from "../utils";
import type { PreviewCapability, PreviewRuntimeConfig } from "../types";
import type { ApiBinding } from "./types";
import type { BindingRequest } from "./flowExecutor";

// §14 WP-8 "BindingRuntime" — FlowExecutor가 결정한 "무엇을, 어떤 요청으로 호출할지"(BindingRequest)와
// 실제 HTTP 호출 사이를 잇는다. 기존 callCapability(api.ts)를 그대로 재사용한다("Existing API utility
// logic should be reused where possible", §19 WP-8).
//
// callCapability의 buildUrl은 경로 파라미터 이름과 무관하게 항상 "경로의 마지막 {...}"만 치환한다
// (DetailPanel 등 기존 컴포넌트 전부가 따르는 규칙, api.ts 주석 참고) — request.path는 여러 키를 가질
// 수 있지만(ApiBinding.InputMapping.target이 실제 파라미터 이름을 그대로 씀) 지금 컴포넌트 전체가
// 중첩 리소스를 지원하지 않는 것과 동일하게, 여기서도 값 하나만(사실상 하나만 있다고 가정) "id"로
// 넘긴다 — 여러 값이 있으면 그중 하나만 실제로 쓰인다는 뜻으로, RuleBasedFlowGenerator가 만드는
// capability.path()도 항상 마지막 세그먼트 하나만 파라미터라 지금은 문제되지 않는다.
export function createCapabilityBindingCaller(capabilities: PreviewCapability[], config: PreviewRuntimeConfig) {
  return async function callBinding(binding: ApiBinding, request: BindingRequest): Promise<unknown> {
    const capability = findCapabilityById(capabilities, binding.capabilityId);
    if (!capability) {
      throw new Error(`알 수 없는 capabilityId: ${binding.capabilityId}`);
    }
    const pathValue = Object.values(request.path)[0];
    return callCapability(config, capability, {
      pathParams: pathValue !== undefined ? { id: pathValue } : {},
      query: request.query,
      body: Object.keys(request.body).length > 0 ? request.body : undefined,
    });
  };
}
