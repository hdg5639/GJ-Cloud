// Backend FlowExpression.java(WP-2)의 TS 미러 — §6/§17 "Arbitrary JavaScript expressions must not
// be supported. Use a restricted expression language". 같은 화이트리스트 정규식을 그대로 재사용해
// 스코프(form/route/context/steps/currentUser) + 점경로 세그먼트만 허용한다. Backend
// FlowBlueprintValidator/ApiBindingValidator가 이미 이 문법으로 Blueprint를 검증했다는 전제이므로,
// 여기서 parse가 실패하면 검증을 안 거친 Blueprint가 들어온 것 — 실행기 쪽 버그로 취급한다.

export type FlowExpressionScope = "FORM" | "ROUTE" | "CONTEXT" | "STEPS" | "CURRENT_USER" | "ROW";

export interface FlowExpression {
  raw: string;
  scope: FlowExpressionScope;
  path: string[];
}

const PATTERN = /^\$(form|route|context|steps|currentUser|row)((?:\.[A-Za-z0-9_-]+)*)$/;

const SCOPE_BY_TOKEN: Record<string, FlowExpressionScope> = {
  form: "FORM",
  route: "ROUTE",
  context: "CONTEXT",
  steps: "STEPS",
  currentUser: "CURRENT_USER",
  row: "ROW",
};

// "$"로 시작하는 값만 표현식으로 취급한다 — 그 외 문자열(메시지 텍스트 등)은 리터럴로 그대로 둔다.
export function isExpressionLike(value: string | null | undefined): value is string {
  return typeof value === "string" && value.startsWith("$");
}

export function parseFlowExpression(raw: string): FlowExpression | null {
  const match = PATTERN.exec(raw);
  if (!match) {
    return null;
  }
  const scope = SCOPE_BY_TOKEN[match[1]];
  const rest = match[2];
  const path = rest.length === 0 ? [] : rest.slice(1).split(".");
  return { raw, scope, path };
}
