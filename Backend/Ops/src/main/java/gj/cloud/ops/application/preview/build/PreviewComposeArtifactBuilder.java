package gj.cloud.ops.application.preview.build;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import gj.cloud.ops.application.deployment.dto.ExposedRoute;
import gj.cloud.ops.application.deployment.dto.HealthCheck;
import gj.cloud.ops.application.deployment.dto.UploadedFile;
import gj.cloud.ops.application.preview.analysis.AuthStrategy;
import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.PreviewBlockResolver;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.blueprint.BlueprintCompiler;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.flow.RuleBasedFlowGenerator;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import gj.cloud.ops.application.preview.planning.model.PagePlanMapper;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Auto Preview(GamjaBox_2.0_Key_Features.md 1단계) Phase D — 확정된 capability/페이지 초안을 실제
// Vite+React 프로젝트 소스로 직렬화해 ComposeArtifact로 포장한다. 이 target에는 Git 저장소가 없으므로
// (DeploymentExecutor.runPipeline의 "저장소 없음" 분기, DeploymentTargetService.create()의 검증 완화와
// 짝을 이룸) 여기서 만든 파일 전체가 uploadedFiles로만 전달되고 그대로 릴리스 디렉토리에 업로드된다.
// 이후의 빌드·헬스체크·라우팅은 다른 배포 방식과 완전히 동일한 코드 경로(DeploymentExecutor)를 그대로 탄다.
@Component
@RequiredArgsConstructor
public class PreviewComposeArtifactBuilder {

    private static final int CONTAINER_PORT = 80;

    private final ObjectMapper objectMapper;
    private final PreviewBlockResolver blockResolver;
    private final RuleBasedFlowGenerator ruleBasedFlowGenerator;

    // 테스트/단순 호출용 편의 오버로드 — pagePlans/flows/bindings를 pages+capabilities에서 결정론적으로
    // 파생한다. 배포 경로(PreviewDeployController)는 patch state의 정본 flows/bindings를 그대로 넘기는
    // 아래 오버로드를 직접 쓴다(AI가 수정한 flow/binding이 재파생으로 덮여 사라지지 않도록).
    public ComposeArtifact build(
            String apiBaseUrl, List<Capability> capabilities, List<PageDraft> pages, AuthStrategy authStrategy,
            Purpose purpose
    ) {
        List<PagePlan> pagePlans = PagePlanMapper.from(pages, capabilities);
        RuleBasedFlowGenerator.ValidatedResult generated =
                ruleBasedFlowGenerator.generateValidated(pagePlans, capabilities);
        return build(apiBaseUrl, capabilities, pages, generated.result().flows(), generated.result().bindings(),
                authStrategy, purpose);
    }

    public ComposeArtifact build(
            String apiBaseUrl, List<Capability> capabilities, List<PageDraft> pages, List<FlowBlueprint> flows,
            List<ApiBinding> bindings, AuthStrategy authStrategy, Purpose purpose
    ) {
        String appTsx = renderAppTsx(apiBaseUrl, capabilities, pages, flows, bindings, authStrategy, purpose);

        List<UploadedFile> uploadedFiles = List.of(
                file("package.json", PACKAGE_JSON),
                file("tsconfig.json", TSCONFIG_JSON),
                file("vite.config.ts", VITE_CONFIG_TS),
                file("index.html", INDEX_HTML),
                file("Dockerfile", DOCKERFILE),
                file("src/main.tsx", MAIN_TSX),
                file("src/index.css", INDEX_CSS),
                // Workflow Composition Phase 2 Change Request §14 WP-8 — FlowBlueprint 모델+실행기를
                // App.tsx 안에 인라인하면 단일 문자열 상수가 JVM 상수 풀의 UTF8 항목 크기 제한
                // (65535바이트)을 넘어 컴파일이 깨진다(실제로 겪음) — 별도 파일로 분리한 이유.
                file("src/flow.ts", FLOW_TS),
                file("src/App.tsx", appTsx)
        );

        // 닉네임은 target 생성 전에 확정해야 해서 targetId 대신 무작위 접미사를 쓴다 — VM 안에서
        // nickname은 전역으로 유일해야 하므로(vm_ports), 같은 VM에 Preview를 여러 번 만들어도 충돌하지 않는다.
        String nickname = "preview-" + UUID.randomUUID().toString().substring(0, 8);
        ExposedRoute route = new ExposedRoute("web", CONTAINER_PORT, "HTTP", "PUBLIC", nickname, null);
        HealthCheck healthCheck = new HealthCheck("web", "/", CONTAINER_PORT, CONTAINER_PORT);

        return new ComposeArtifact(
                COMPOSE_CONTENT,
                List.of(),
                uploadedFiles,
                List.of(route),
                List.of(healthCheck),
                SourceType.AUTO_PREVIEW
        );
    }

    private UploadedFile file(String vmPath, String content) {
        return new UploadedFile(vmPath, content.getBytes(StandardCharsets.UTF_8));
    }

    private String renderAppTsx(
            String apiBaseUrl, List<Capability> capabilities, List<PageDraft> pages, List<FlowBlueprint> flows,
            List<ApiBinding> bindings, AuthStrategy authStrategy, Purpose purpose
    ) {
        // Workflow Composition Phase 2 Change Request §10 — flows/bindings는 호출측이 넘겨준 값을
        // 그대로 쓴다(여기서 재파생하면 AI가 수정한 flow/binding이 배포 산출물에서 사라진다).
        // pageBlocks는 pages(=toDrafts(pagePlans))로 계산 — compilePagePlanBlocks와 동일 경로다.
        Map<String, List<Block>> pageBlocks =
                BlueprintCompiler.compile(blockResolver.resolveAll(pages, capabilities), purpose);
        return appTsxTemplate()
                .replace("__API_BASE_URL_JSON__", toJson(apiBaseUrl))
                .replace("__CAPABILITIES_JSON__", toJson(capabilities))
                .replace("__PAGES_JSON__", toJson(pages))
                .replace("__AUTH_STRATEGY_JSON__", toJson(authStrategy))
                .replace("__PURPOSE_JSON__", toJson(purpose != null ? purpose.name() : null))
                .replace("__PAGE_BLOCKS_JSON__", toJson(pageBlocks))
                .replace("__FLOWS_JSON__", toJson(flows))
                .replace("__BINDINGS_JSON__", toJson(bindings));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Preview 소스 생성 중 JSON 직렬화 실패", e);
        }
    }

    private static final String COMPOSE_CONTENT = """
            services:
              web:
                build: .
                ports:
                  - "80:80"
            """;

    private static final String PACKAGE_JSON = """
            {
              "name": "gamjabox-preview",
              "private": true,
              "version": "0.0.1",
              "type": "module",
              "scripts": {
                "dev": "vite",
                "build": "vite build",
                "preview": "vite preview"
              },
              "dependencies": {
                "react": "^18.3.1",
                "react-dom": "^18.3.1"
              },
              "devDependencies": {
                "@types/react": "^18.3.3",
                "@types/react-dom": "^18.3.0",
                "@vitejs/plugin-react": "^4.3.1",
                "typescript": "^5.5.3",
                "vite": "^5.4.1"
              }
            }
            """;

    // 자동 생성 소스는 사람이 리뷰하지 않고 바로 이미지 빌드로 이어지므로, 사소한 타입 문제로 배포 전체가
    // 막히지 않도록 빌드 스크립트에서 tsc 타입 검사는 의도적으로 생략한다(vite build는 esbuild로 트랜스파일만 함).
    private static final String TSCONFIG_JSON = """
            {
              "compilerOptions": {
                "target": "ES2021",
                "useDefineForClassFields": true,
                "lib": ["ES2021", "DOM", "DOM.Iterable"],
                "module": "ESNext",
                "skipLibCheck": true,
                "moduleResolution": "bundler",
                "allowImportingTsExtensions": true,
                "resolveJsonModule": true,
                "isolatedModules": true,
                "noEmit": true,
                "jsx": "react-jsx",
                "strict": true
              },
              "include": ["src"]
            }
            """;

    private static final String VITE_CONFIG_TS = """
            import { defineConfig } from "vite";
            import react from "@vitejs/plugin-react";

            export default defineConfig({
              plugins: [react()],
            });
            """;

    private static final String INDEX_HTML = """
            <!doctype html>
            <html lang="ko">
              <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>GamjaBox Auto Preview</title>
              </head>
              <body>
                <div id="root"></div>
                <script type="module" src="/src/main.tsx"></script>
              </body>
            </html>
            """;

    private static final String DOCKERFILE = """
            FROM node:20-alpine AS build
            WORKDIR /app
            COPY package.json ./
            RUN npm install
            COPY . .
            RUN npm run build

            FROM nginx:alpine
            COPY --from=build /app/dist /usr/share/nginx/html
            EXPOSE 80
            """;

    private static final String MAIN_TSX = """
            import { StrictMode } from "react";
            import { createRoot } from "react-dom/client";
            import App from "./App";
            import "./index.css";

            createRoot(document.getElementById("root")!).render(
              <StrictMode>
                <App />
              </StrictMode>
            );
            """;

    // Workflow Composition Phase 2 Change Request §6(FlowBlueprint)/§8(ApiBinding)/§14(FlowExecutor) —
    // Frontend components/preview-runtime/flow/{types,expression,flowExecutor}.ts와 동일한 로직을
    // 이식한 것. App.tsx가 이 파일에서 executeFlow/createFlowContext/타입을 import한다(App.tsx 자체
    // 인라인은 JVM 상수 풀 UTF8 항목 크기 제한 때문에 불가능했음, build() 주석 참고).
    private static final String FLOW_TS = """
            type FlowStepType =
              | "API_CALL" | "SET_CONTEXT" | "NAVIGATE" | "POLL" | "WAIT" | "CONDITION"
              | "SHOW_SUCCESS" | "SHOW_ERROR" | "REFRESH_BINDING"
              | "EVENT_STREAM" | "UPLOAD" | "DOWNLOAD" | "PARALLEL";

            export interface PollCondition {
              path: string;
              equalsValue: string | null;
              in: string[] | null;
            }

            export interface FlowStep {
              id: string;
              type: FlowStepType;
              bindingRef: string | null;
              input: Record<string, string> | null;
              values: Record<string, string> | null;
              pageId: string | null;
              parameters: Record<string, string> | null;
              until: PollCondition[] | null;
              intervalMs: number | null;
              timeoutSeconds: number | null;
              condition: string | null;
              message: string | null;
            }

            export interface FlowTrigger {
              pageId: string | null;
              actionId: string | null;
            }

            export interface FlowBlueprint {
              id: string;
              trigger: FlowTrigger | null;
              steps: FlowStep[];
            }

            type InputTarget = "PATH" | "QUERY" | "BODY" | "HEADER";

            export interface InputMapping {
              target: string;
              targetKind: InputTarget;
              from: string;
            }

            export interface OutputMapping {
              from: string;
              to: string;
            }

            export interface ApiBinding {
              id: string;
              capabilityId: string;
              inputMappings: InputMapping[];
              outputMappings: OutputMapping[];
              refreshBindingIds: string[];
            }

            // §6/§17 "Arbitrary JavaScript expressions must not be supported" — 화이트리스트 정규식
            // 하나로 스코프(form/route/context/steps/currentUser) + 점경로 세그먼트만 허용한다.
            type FlowExpressionScope = "FORM" | "ROUTE" | "CONTEXT" | "STEPS" | "CURRENT_USER";

            interface FlowExpression {
              raw: string;
              scope: FlowExpressionScope;
              path: string[];
            }

            const FLOW_EXPRESSION_PATTERN = /^\\$(form|route|context|steps|currentUser)((?:\\.[A-Za-z0-9_-]+)*)$/;

            const FLOW_EXPRESSION_SCOPE_BY_TOKEN: Record<string, FlowExpressionScope> = {
              form: "FORM",
              route: "ROUTE",
              context: "CONTEXT",
              steps: "STEPS",
              currentUser: "CURRENT_USER",
            };

            function isFlowExpressionLike(value: string | null | undefined): value is string {
              return typeof value === "string" && value.startsWith("$");
            }

            function parseFlowExpression(raw: string): FlowExpression | null {
              const match = FLOW_EXPRESSION_PATTERN.exec(raw);
              if (!match) {
                return null;
              }
              const scope = FLOW_EXPRESSION_SCOPE_BY_TOKEN[match[1]];
              const rest = match[2];
              const path = rest.length === 0 ? [] : rest.slice(1).split(".");
              return { raw, scope, path };
            }

            export interface FlowContext {
              form: Record<string, unknown>;
              route: Record<string, unknown>;
              context: Record<string, unknown>;
              steps: Record<string, { response: unknown }>;
              currentUser: Record<string, unknown> | null;
            }

            export function createFlowContext(seed: Partial<FlowContext> = {}): FlowContext {
              return {
                form: seed.form ?? {},
                route: seed.route ?? {},
                context: seed.context ?? {},
                steps: seed.steps ?? {},
                currentUser: seed.currentUser ?? null,
              };
            }

            export interface BindingRequest {
              path: Record<string, string>;
              query: Record<string, string>;
              body: Record<string, unknown>;
              headers: Record<string, string>;
            }

            export interface FlowExecutorDeps {
              callBinding: (binding: ApiBinding, request: BindingRequest) => Promise<unknown>;
              navigate?: (pageId: string, parameters: Record<string, unknown>) => void;
              onMessage?: (kind: "SUCCESS" | "ERROR", message: string) => void;
              onRefreshBindingError?: (bindingId: string, error: unknown) => void;
              signal?: AbortSignal;
              now?: () => number;
              sleep?: (ms: number) => Promise<void>;
            }

            const defaultFlowSleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

            export class FlowExecutionError extends Error {
              readonly stepId: string;
              readonly cause: unknown;

              constructor(stepId: string, cause: unknown) {
                super(`step "${stepId}" 실행 실패: ${cause instanceof Error ? cause.message : String(cause)}`);
                this.stepId = stepId;
                this.cause = cause;
              }
            }

            function findFlowBinding(bindings: ApiBinding[], id: string): ApiBinding {
              const binding = bindings.find((b) => b.id === id);
              if (!binding) {
                throw new Error(`알 수 없는 bindingRef: ${id}`);
              }
              return binding;
            }

            function readByFlowPath(root: unknown, path: string[]): unknown {
              let current = root;
              for (const segment of path) {
                if (current === null || typeof current !== "object") {
                  return undefined;
                }
                current = (current as Record<string, unknown>)[segment];
              }
              return current;
            }

            function resolveFlowExpressionScopeRoot(ctx: FlowContext, expr: FlowExpression): unknown {
              switch (expr.scope) {
                case "FORM":
                  return ctx.form;
                case "ROUTE":
                  return ctx.route;
                case "CONTEXT":
                  return ctx.context;
                case "STEPS":
                  return ctx.steps;
                case "CURRENT_USER":
                  return ctx.currentUser;
              }
            }

            function resolveFlowValue(raw: string, ctx: FlowContext): unknown {
              if (!isFlowExpressionLike(raw)) {
                return raw;
              }
              const expr = parseFlowExpression(raw);
              if (!expr) {
                throw new Error(`허용되지 않는 표현식: ${raw}`);
              }
              return readByFlowPath(resolveFlowExpressionScopeRoot(ctx, expr), expr.path);
            }

            function resolveFlowMap(map: Record<string, string> | null, ctx: FlowContext): Record<string, unknown> {
              if (!map) {
                return {};
              }
              const result: Record<string, unknown> = {};
              for (const [key, value] of Object.entries(map)) {
                result[key] = resolveFlowValue(value, ctx);
              }
              return result;
            }

            // ApiBinding.inputMappings가 요청 조립의 정본이다 — FlowStep.input/parameters는 안 쓴다
            // (라이브 프리뷰 flowExecutor.ts와 동일한 알려진 단순화).
            function buildFlowBindingRequest(binding: ApiBinding, ctx: FlowContext): BindingRequest {
              const request: BindingRequest = { path: {}, query: {}, body: {}, headers: {} };
              for (const mapping of binding.inputMappings) {
                const value = resolveFlowValue(mapping.from, ctx);
                switch (mapping.targetKind) {
                  case "PATH":
                    request.path[mapping.target] = String(value);
                    break;
                  case "QUERY":
                    request.query[mapping.target] = String(value);
                    break;
                  case "HEADER":
                    request.headers[mapping.target] = String(value);
                    break;
                  case "BODY":
                    request.body[mapping.target] = value;
                    break;
                }
              }
              return request;
            }

            function applyFlowOutputMappings(binding: ApiBinding, response: unknown, ctx: FlowContext): void {
              for (const mapping of binding.outputMappings) {
                // 같은 context key에 data.id/result.id/payload.id/id 후보를 순서대로 매핑할 수 있다.
                // 존재하지 않는 후보(undefined)가 앞에서 찾은 값을 덮어쓰면 안 된다(예: 봉투 응답이면
                // data.id는 값이 있지만 최상위 id는 undefined라 createdId를 지워버린다).
                const value = readByFlowPath(response, mapping.from.split("."));
                if (value !== undefined && value !== null) {
                  ctx.context[mapping.to] = value;
                }
              }
            }

            async function callFlowBindingAndApply(
              binding: ApiBinding,
              ctx: FlowContext,
              deps: FlowExecutorDeps,
              recordStepId?: string
            ): Promise<unknown> {
              const request = buildFlowBindingRequest(binding, ctx);
              const response = await deps.callBinding(binding, request);
              applyFlowOutputMappings(binding, response, ctx);
              if (recordStepId) {
                ctx.steps[recordStepId] = { response };
              }
              return response;
            }

            async function refreshRelatedFlowBindings(
              binding: ApiBinding,
              bindings: ApiBinding[],
              ctx: FlowContext,
              deps: FlowExecutorDeps
            ): Promise<void> {
              for (const refreshId of binding.refreshBindingIds) {
                try {
                  const target = findFlowBinding(bindings, refreshId);
                  await callFlowBindingAndApply(target, ctx, deps);
                } catch (error) {
                  deps.onRefreshBindingError?.(refreshId, error);
                }
              }
            }

            function matchesFlowCondition(condition: PollCondition, response: unknown): boolean {
              const value = readByFlowPath(response, condition.path.split("."));
              if (condition.equalsValue !== null) {
                return String(value) === condition.equalsValue;
              }
              if (condition.in) {
                return condition.in.includes(String(value));
              }
              return false;
            }

            function matchesFlowUntil(until: PollCondition[], response: unknown): boolean {
              return until.some((condition) => matchesFlowCondition(condition, response));
            }

            async function executeFlowPoll(
              step: FlowStep,
              bindings: ApiBinding[],
              ctx: FlowContext,
              deps: FlowExecutorDeps
            ): Promise<void> {
              const binding = findFlowBinding(bindings, step.bindingRef!);
              const sleep = deps.sleep ?? defaultFlowSleep;
              const now = deps.now ?? Date.now;
              const deadline = now() + step.timeoutSeconds! * 1000;

              for (;;) {
                if (deps.signal?.aborted) {
                  return;
                }

                let response: unknown;
                try {
                  response = await deps.callBinding(binding, buildFlowBindingRequest(binding, ctx));
                } catch (error) {
                  throw new FlowExecutionError(step.id, error);
                }

                if (matchesFlowUntil(step.until!, response)) {
                  applyFlowOutputMappings(binding, response, ctx);
                  ctx.steps[step.id] = { response };
                  await refreshRelatedFlowBindings(binding, bindings, ctx, deps);
                  return;
                }

                if (now() >= deadline) {
                  return;
                }

                await sleep(step.intervalMs!);
              }
            }

            async function executeFlowStep(
              step: FlowStep,
              bindings: ApiBinding[],
              ctx: FlowContext,
              deps: FlowExecutorDeps
            ): Promise<"continue" | "stop"> {
              switch (step.type) {
                case "API_CALL":
                case "REFRESH_BINDING": {
                  const binding = findFlowBinding(bindings, step.bindingRef!);
                  try {
                    await callFlowBindingAndApply(binding, ctx, deps, step.id);
                  } catch (error) {
                    throw new FlowExecutionError(step.id, error);
                  }
                  return "continue";
                }
                case "SET_CONTEXT":
                  Object.assign(ctx.context, resolveFlowMap(step.values, ctx));
                  return "continue";
                case "NAVIGATE":
                  deps.navigate?.(step.pageId!, resolveFlowMap(step.parameters, ctx));
                  return "continue";
                case "POLL":
                  await executeFlowPoll(step, bindings, ctx, deps);
                  return "continue";
                case "WAIT":
                  await (deps.sleep ?? defaultFlowSleep)(step.timeoutSeconds! * 1000);
                  return "continue";
                case "CONDITION":
                  return resolveFlowValue(step.condition!, ctx) ? "continue" : "stop";
                case "SHOW_SUCCESS":
                  deps.onMessage?.("SUCCESS", step.message!);
                  return "continue";
                case "SHOW_ERROR":
                  deps.onMessage?.("ERROR", step.message!);
                  return "continue";
                case "EVENT_STREAM":
                case "UPLOAD":
                case "DOWNLOAD":
                case "PARALLEL":
                  throw new FlowExecutionError(step.id, new Error(`아직 지원하지 않는 step 타입: ${step.type}`));
              }
            }

            export async function executeFlow(
              flow: FlowBlueprint,
              bindings: ApiBinding[],
              ctx: FlowContext,
              deps: FlowExecutorDeps
            ): Promise<void> {
              for (const step of flow.steps) {
                if (deps.signal?.aborted) {
                  return;
                }
                const outcome = await executeFlowStep(step, bindings, ctx, deps);
                if (outcome === "stop") {
                  return;
                }
              }
            }
            """;

    private static final String INDEX_CSS = """
            :root {
              font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
              background: #0b0d0f;
              color: #e5e7eb;
              /* Auto Preview 상태 배지 팔레트 — 이 4개 hex만 바꾸면 배지/요약/카드 색이 전부 갈린다.
                 배경/테두리는 color-mix로 파생하므로 tone당 색 하나만 관리한다. */
              --preview-status-ok: #46d17f;
              --preview-status-warn: #f5a623;
              --preview-status-idle: #8b93a0;
              --preview-status-danger: #f2555a;
            }
            * { box-sizing: border-box; }
            body { margin: 0; }
            .container { max-width: 1120px; margin: 0 auto; padding: 32px; }
            .shell { overflow: hidden; border: 1px solid #262b26; border-radius: 14px; background: #14171a; }
            .shell-main { min-width: 0; flex: 1; padding: 20px; }
            .shell-header { padding: 18px 20px 0; border-bottom: 1px solid #262b26; background: linear-gradient(90deg, rgba(34,197,94,0.08), transparent); }
            .shell-brand { display: flex; align-items: center; gap: 10px; }
            .shell-logo { width: 30px; height: 30px; border-radius: 9px; background: #22c55e; color: #07130a; display: grid; place-items: center; font-weight: 900; }
            .shell-product-nav { display: flex; gap: 20px; overflow-x: auto; margin-top: 18px; }
            .shell-product-nav button { border: 0; border-bottom: 2px solid transparent; border-radius: 0; padding: 0 0 12px; background: transparent; color: #9ca3af; white-space: nowrap; font-weight: 800; }
            .shell-product-nav button.active { color: #e5e7eb; border-bottom-color: #22c55e; }
            .shell-admin { display: flex; min-height: 540px; }
            .shell-sidebar { width: 210px; flex: none; border-right: 1px solid #262b26; padding: 14px; background: rgba(0,0,0,0.12); }
            .shell-sidebar button { width: 100%; display: block; border: 0; background: transparent; color: #9ca3af; text-align: left; padding: 9px 10px; margin-bottom: 4px; }
            .shell-sidebar button.active { background: rgba(34,197,94,0.10); color: #22c55e; }
            .shell-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding-bottom: 12px; margin-bottom: 16px; border-bottom: 1px solid #262b26; }
            .shell-badge { border: 1px solid #333; border-radius: 999px; padding: 4px 9px; color: #9ca3af; font-size: 10px; font-weight: 700; }
            .shell-api-head { display: flex; justify-content: space-between; gap: 12px; padding: 13px 16px; border-bottom: 1px solid #262b26; background: #0d1012; }
            .shell-api-nav { display: flex; gap: 4px; overflow-x: auto; padding: 8px 12px; border-bottom: 1px solid #262b26; background: rgba(0,0,0,0.18); }
            .shell-api-nav button { border: 0; padding: 6px 9px; background: transparent; color: #9ca3af; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 11px; }
            .shell-api-nav button.active { background: rgba(34,197,94,0.12); color: #22c55e; }
            .tabs { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
            .tab { padding: 6px 14px; border-radius: 6px; border: 1px solid #333; background: transparent; color: #aaa; cursor: pointer; font-size: 13px; font-weight: 700; }
            .tab.active { border-color: #22c55e; color: #22c55e; background: rgba(34,197,94,0.1); }
            .panel { border: 1px solid #262b26; border-radius: 12px; background: #14171a; padding: 20px; }
            .field { display: block; margin-bottom: 14px; font-size: 12px; font-weight: 700; color: #9ca3af; }
            input {
              display: block; width: 100%; margin-top: 6px; padding: 10px 12px; border-radius: 8px;
              border: 1px solid #333; background: #0b0d0f; color: #e5e7eb; font-size: 14px;
            }
            button { font-family: inherit; font-size: 13px; border-radius: 8px; padding: 10px 16px; cursor: pointer; }
            button.primary { background: #22c55e; color: #08130b; border: none; font-weight: 700; }
            button.plain { background: transparent; border: 1px solid #333; color: #ddd; }
            button.danger { background: #ef4444; color: white; border: none; font-weight: 700; }
            table { width: 100%; border-collapse: collapse; font-size: 13px; }
            th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #262b26; }
            .error { color: #f87171; font-size: 12px; }
            .muted { color: #9ca3af; font-size: 12px; }
            .modal-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 50; }
            .modal { width: 380px; background: #14171a; border-radius: 12px; padding: 20px; border: 1px solid #262b26; }
            .drawer-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; justify-content: flex-end; z-index: 50; }
            .drawer { width: 420px; max-width: 100%; height: 100%; overflow-y: auto; background: #14171a; padding: 20px; border-left: 1px solid #262b26; }
            .grid-2 { display: grid; grid-template-columns: 1fr 320px; gap: 16px; }
            @media (max-width: 720px) {
              .grid-2 { grid-template-columns: 1fr; }
              .shell-admin { display: block; }
              .shell-sidebar { width: 100%; border-right: 0; border-bottom: 1px solid #262b26; display: flex; gap: 4px; overflow-x: auto; }
              .shell-sidebar button { width: auto; white-space: nowrap; }
            }
            .log-entry { border: 1px solid #262b26; border-radius: 8px; margin-bottom: 6px; background: rgba(255,255,255,0.02); }
            .log-entry summary { cursor: pointer; list-style: none; padding: 8px 12px; display: flex; align-items: center; gap: 8px; font-size: 12px; }
            .log-entry summary::-webkit-details-marker { display: none; }
            .log-status { border-radius: 4px; padding: 2px 6px; font-size: 10px; font-weight: 700; background: rgba(34,197,94,0.15); color: #22c55e; }
            .log-status.error { background: rgba(239,68,68,0.15); color: #f87171; }
            .log-url { color: #9ca3af; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .log-body { padding: 10px 12px; border-top: 1px solid #262b26; }
            .log-body pre { background: rgba(0,0,0,0.3); border-radius: 6px; padding: 8px; font-size: 11px; overflow-x: auto; color: #9ca3af; }
            """;

    // GamjaBox_2.0_Key_Features.md 3·7절 — 관련 API를 페이지 하나로 묶어서 보여준다. Blueprint
    // Schema/Registry/Slot 시스템 없이 5개 고정 패턴만 조립하는 Phase C 렌더러를 순수 React(플레인
    // CSS, 포털 전용 컴포넌트/Tailwind 의존 없음)로 그대로 이식한 것 — 동작은 Phase C에서 브라우저로
    // 이미 검증됨.
    // App.tsx가 JVM CONSTANT_Utf8 65,535 byte 한도를 넘지 않도록 두 상수로 나누고 런타임에 결합한다.
    // 컴파일 타임 문자열 덧셈은 다시 하나의 거대 상수로 folding될 수 있어 StringBuilder를 사용한다.
    private static final String APP_TSX_TEMPLATE_PART_1 = """
            import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
            import {
              createFlowContext,
              executeFlow,
              type ApiBinding,
              type BindingRequest,
              type FlowBlueprint,
            } from "./flow";

            type CapabilityType = "LIST" | "DETAIL" | "CREATE" | "UPDATE" | "DELETE" | "LOGIN";
            type PageSkeletonType = "AUTH_PAGE" | "RESOURCE_LIST" | "LIST_DETAIL" | "DASHBOARD";
            type Purpose = "API_TEST" | "PRODUCT_LIKE" | "ADMIN";
            type RiskLevel = "SAFE" | "STATE_CHANGING" | "DESTRUCTIVE" | "IRREVERSIBLE" | "EXTERNAL_SIDE_EFFECT";
            type AutomationPolicy =
              | "AUTO_SAFE" | "USER_INITIATED" | "EXPLICIT_CONFIRMATION" | "TYPED_CONFIRMATION" | "DISABLED_IN_AUTO_TEST";
            type CapabilityKind =
              | "QUERY" | "MUTATION" | "COMMAND" | "AUTH" | "METRIC" | "EVENT_STREAM" | "FILE_TRANSFER" | "WORKFLOW";

            interface Capability {
              id: string;
              resourceName: string;
              // kind=COMMAND일 때는 null — CRUD 6종 enum에 억지로 끼워 넣지 않는다.
              type: CapabilityType | null;
              operationId: string | null;
              path: string;
              method: string;
              hasSearch: boolean;
              hasSort: boolean;
              hasPagination: boolean;
              confidence: string;
              evidence: string[];
              fields: string[];
              accessTokenPath: string | null;
              searchParam: string | null;
              risk: RiskLevel;
              automationPolicy: AutomationPolicy;
              collectionPath: string | null;
              totalCountPath: string | null;
              // Direction Recovery Change Request §7.1 — kind=COMMAND는 QuickActionButtonGroup이 그린다.
              kind: CapabilityKind;
              action: string | null;
              dependencies: string[];
              // AC-4 폴링 힌트(DETAIL만, 그 외 null). 런타임은 flow의 PollCondition을 쓰므로 이 필드를
              // 직접 소비하진 않지만, 서버 직렬화에 포함되므로 타입에 선언해 tsc를 통과시킨다.
              pollHint: { statusPath: string; terminalValues: string[] } | null;
            }

            interface PageDraft {
              id: string;
              title: string;
              skeleton: PageSkeletonType;
              capabilityIds: string[];
            }

            type AuthStrategyType = "NONE" | "BEARER" | "API_KEY_HEADER" | "API_KEY_QUERY";
            interface AuthStrategy {
              type: AuthStrategyType;
              headerName: string | null;
              prefix: string | null;
              queryParamName: string | null;
            }

            // auto-preview-design/01-blueprint-schema.md의 Block Instance 축소판 — PageRenderer가
            // page.skeleton을 직접 switch하는 대신 이 목록을 순회해 조립한다.
            type ComponentId =
              | "login-form" | "resource-table" | "resource-card-grid" | "detail-panel" | "create-edit-modal"
              | "form-drawer" | "delete-confirm-modal" | "typed-confirm-modal" | "dashboard-view"
              | "recent-activity-dashboard" | "quick-action-button-group" | "full-detail-page"
              | "child-resource-list";
            interface Block {
              instanceId: string;
              componentId: ComponentId;
              slot: string;
              capabilityIds: string[];
              mode: "CREATE" | "UPDATE" | null;
              // 이 Block이 활성화됐을 때 다른 Block(주로 같은 Slot을 두고 다투는 대안) 자리를 대신
              // 차지한다는 표시(그 Block의 instanceId). null이면 독립적으로 존재. PAGE_BLOCKS는 Java
              // Block record를 그대로 직렬화해 내려주므로 이 필드는 서버에서 이미 채워져 있다.
              replaces: string | null;
            }

            // auto-preview-design/08-compatibility-rules.md §6 Slot 규칙 3 "Overlay 최대 동시 활성
            // Instance 1개" — showCreate/editTarget/deleteTargetId를 독립된 상태 3개로 관리하면
            // 이론상 여러 개가 동시에 켜질 수 있어(상세 패널에서 수정 클릭 후 삭제 클릭 등) 하나의
            // 판별 유니언으로 묶어 상호 배타를 코드로 보장한다.
            type OverlayState =
              | { kind: "NONE" }
              | { kind: "CREATE" }
              | { kind: "UPDATE"; row: Record<string, unknown> }
              | { kind: "DELETE"; id: string };

            const API_BASE_URL: string = __API_BASE_URL_JSON__;
            const CAPABILITIES: Capability[] = __CAPABILITIES_JSON__;
            const PAGES: PageDraft[] = __PAGES_JSON__;
            // 로그인으로 받은 토큰을 나머지 모든 보호된 요청에 어떻게 실어 보낼지 — 문서 전체에 하나만
            // 있다(Capability별로 다르지 않음). Bearer만 가정하던 기존 방식은 API Key 인증 API에서 항상
            // 401/403이 났다.
            const AUTH_STRATEGY: AuthStrategy = __AUTH_STRATEGY_JSON__;
            const PURPOSE: Purpose | null = __PURPOSE_JSON__;
            // 페이지 ID별 Block 목록 — PreviewBlockResolver가 서버에서 미리 계산해 심어둔다.
            const PAGE_BLOCKS: Record<string, Block[]> = __PAGE_BLOCKS_JSON__;
            const FLOWS: FlowBlueprint[] = __FLOWS_JSON__;
            const BINDINGS: ApiBinding[] = __BINDINGS_JSON__;

            // DetailPanel/CreateEditModal/DeleteConfirmModal은 실제 경로 파라미터 이름을 모른 채 항상
            // { id: ... } 하나만 넘긴다. 이름 그대로 "{id}"를 찾아 치환하면 capability.path가
            // "/vms/{vmId}"처럼 다른 이름을 쓸 때 치환에 실패해 URL에 "{vmId}"가 그대로 남는다.
            // DETAIL/UPDATE/DELETE는 항상 경로의 마지막 세그먼트만 파라미터이므로 이름과 무관하게
            // "경로의 마지막 {...}"를 대상 리소스 ID로 치환한다.
            function replaceLastPathPlaceholder(path: string, value: string): string {
              const lastOpen = path.lastIndexOf("{");
              const lastClose = path.lastIndexOf("}");
              if (lastOpen === -1 || lastClose === -1 || lastClose < lastOpen) {
                return path;
              }
              return path.slice(0, lastOpen) + encodeURIComponent(value) + path.slice(lastClose + 1);
            }

            function buildUrl(
              capability: Capability,
              authToken: string | null,
              pathParams: Record<string, string> = {},
              query: Record<string, string> = {}
            ): string {
              let path = capability.path;
              // 자식 리소스(/machines/{machineId}/ports/{portId})는 파라미터가 둘 이상이라 이름으로
              // 치환한다. 이름 없이 { id }만 넘어오는 단일 리소스는 아래 fallback이 마지막 {...}를 채운다.
              for (const [name, value] of Object.entries(pathParams)) {
                if (name === "id") continue;
                path = path.replaceAll(`{${name}}`, encodeURIComponent(value));
              }
              if (pathParams.id !== undefined && path.includes("{")) {
                path = replaceLastPathPlaceholder(path, pathParams.id);
              }
              const url = new URL(API_BASE_URL.replace(/\\/$/, "") + path);
              for (const [key, value] of Object.entries(query)) {
                if (value) url.searchParams.set(key, value);
              }
              if (authToken && AUTH_STRATEGY.type === "API_KEY_QUERY" && AUTH_STRATEGY.queryParamName) {
                url.searchParams.set(AUTH_STRATEGY.queryParamName, authToken);
              }
              return url.toString();
            }

            function buildAuthHeaders(authToken: string | null): Record<string, string> {
              if (!authToken || AUTH_STRATEGY.type === "NONE" || AUTH_STRATEGY.type === "API_KEY_QUERY") {
                return {};
              }
              const headerName = AUTH_STRATEGY.headerName ?? "Authorization";
              const prefix = AUTH_STRATEGY.prefix ?? "";
              return { [headerName]: `${prefix}${authToken}` };
            }

            // 요청·응답 확인 패널(App 하단)이 구독하는 단순 pub-sub — 모든 컴포넌트가 authToken을
            // 개별 prop으로 받는 이 코드생성 구조에서는 config 객체 하나를 새로 꿰매 넣는 것보다
            // callCapability 한 곳에서만 이벤트를 쏘고 App이 구독하는 편이 컴포넌트 5개의 시그니처를
            // 안 건드려서 더 안전하다.
            interface ApiCallLogEntry {
              id: string;
              method: string;
              url: string;
              status: number | null;
              requestBody: unknown;
              responseBody: unknown;
              error: string | null;
              timestamp: number;
            }

            let apiCallListener: ((entry: ApiCallLogEntry) => void) | null = null;

            function emitApiCall(entry: ApiCallLogEntry) {
              apiCallListener?.(entry);
            }

            async function callCapability(
              capability: Capability,
              authToken: string | null,
              options: { pathParams?: Record<string, string>; query?: Record<string, string>; body?: Record<string, unknown> } = {}
            ): Promise<unknown> {
              const url = buildUrl(capability, authToken, options.pathParams, options.query);
              let status: number | null = null;
              let responseBody: unknown = null;
              let errorMessage: string | null = null;
              try {
                const res = await fetch(url, {
                  method: capability.method,
                  headers: {
                    ...(options.body ? { "Content-Type": "application/json" } : {}),
                    ...buildAuthHeaders(authToken),
                  },
                  body: options.body ? JSON.stringify(options.body) : undefined,
                });
                status = res.status;
                if (!res.ok) {
                  errorMessage = `${capability.method} ${url} 요청이 실패했습니다 (${res.status})`;
                  throw new Error(errorMessage);
                }
                if (res.status === 204) {
                  return null;
                }
                const text = await res.text();
                responseBody = text ? JSON.parse(text) : null;
                return responseBody;
              } catch (err) {
                errorMessage = errorMessage ?? (err instanceof Error ? err.message : "요청 실패");
                throw err;
              } finally {
                emitApiCall({
                  id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
                  method: capability.method,
                  url,
                  status,
                  requestBody: options.body ?? null,
                  responseBody,
                  error: errorMessage,
                  timestamp: Date.now(),
                });
              }
            }

            function isPasswordLikeField(name: string): boolean {
              const lower = name.toLowerCase();
              return lower.includes("password") || lower === "pw" || lower === "pwd";
            }

            const ARRAY_ENVELOPE_KEYS = [
              "content", "items", "data", "list", "results", "records", "rows", "elements", "result", "payload",
            ];
            const MAX_ENVELOPE_UNWRAP_DEPTH = 4;

            function extractArrayHeuristic(result: unknown, depth = 0): Record<string, unknown>[] {
              if (Array.isArray(result)) {
                return result as Record<string, unknown>[];
              }
              if (depth >= MAX_ENVELOPE_UNWRAP_DEPTH || !result || typeof result !== "object") {
                return [];
              }
              const obj = result as Record<string, unknown>;
              const orderedKeys = [
                ...ARRAY_ENVELOPE_KEYS.filter((key) => key in obj),
                ...Object.keys(obj).filter((key) => !ARRAY_ENVELOPE_KEYS.includes(key)),
              ];
              for (const key of orderedKeys) {
                const nested = extractArrayHeuristic(obj[key], depth + 1);
                if (nested.length > 0) {
                  return nested;
                }
              }
              return [];
            }

            // 분석 단계(또는 사용자가 직접 지정)에서 확인된 dot-path를 우선 신뢰한다 — 없거나 그 위치에
            // 값이 없을 때만 기존 방식(이름 추측 / 재귀 탐색)으로 대체한다.
            function readValueAtPath(result: unknown, dotPath: string): unknown {
              let current: unknown = result;
              for (const key of dotPath.split(".")) {
                if (!current || typeof current !== "object") {
                  return undefined;
                }
                current = (current as Record<string, unknown>)[key];
              }
              return current;
            }

            function readDotPath(result: unknown, dotPath: string): string | null {
              const value = readValueAtPath(result, dotPath);
              return typeof value === "string" && value.length > 0 ? value : null;
            }

            // 목록 응답에서 실제 배열을 찾는다. 분석 단계가 collectionPath를 미리 확정해뒀으면 그 위치를
            // 우선 신뢰하고, 없거나 그 위치에 배열이 없으면 기존 재귀 휴리스틱으로 대체한다.
            function extractArray(result: unknown, collectionPath?: string | null): Record<string, unknown>[] {
              if (collectionPath) {
                const viaPath = readValueAtPath(result, collectionPath);
                if (Array.isArray(viaPath)) {
                  return viaPath as Record<string, unknown>[];
                }
              }
              return extractArrayHeuristic(result);
            }

            const COUNT_FIELD_KEYS = ["totalElements", "total", "totalCount", "count"];
            const MAX_COUNT_FIELD_DEPTH = 3;

            // Dashboard 카드용 — Spring Data Page류 응답은 배열(content)과 같은 깊이에 총 개수를 함께
            // 담는 경우가 많아 그 필드를 먼저 찾고, 없으면 받은 페이지의 배열 길이로 대체한다.
            function findCountField(value: unknown, depth: number): number | null {
              if (depth > MAX_COUNT_FIELD_DEPTH || !value || typeof value !== "object") {
                return null;
              }
              const obj = value as Record<string, unknown>;
              for (const key of COUNT_FIELD_KEYS) {
                if (typeof obj[key] === "number") {
                  return obj[key] as number;
                }
              }
              for (const key of Object.keys(obj)) {
                if (obj[key] && typeof obj[key] === "object" && !Array.isArray(obj[key])) {
                  const nested = findCountField(obj[key], depth + 1);
                  if (nested !== null) return nested;
                }
              }
              return null;
            }

            function extractCount(result: unknown, totalCountPath?: string | null, collectionPath?: string | null): number {
              if (totalCountPath) {
                const viaPath = readValueAtPath(result, totalCountPath);
                if (typeof viaPath === "number") {
                  return viaPath;
                }
              }
              return findCountField(result, 0) ?? extractArray(result, collectionPath).length;
            }

            function extractToken(result: unknown, accessTokenPath: string | null): string | null {
              if (accessTokenPath) {
                const viaPath = readDotPath(result, accessTokenPath);
                if (viaPath) {
                  return viaPath;
                }
              }
              if (!result || typeof result !== "object") {
                return null;
              }
              const obj = result as Record<string, unknown>;
              const data = obj.data && typeof obj.data === "object" ? (obj.data as Record<string, unknown>) : undefined;
              const candidates = [obj.accessToken, obj.token, data?.accessToken, data?.token];
              for (const candidate of candidates) {
                if (typeof candidate === "string" && candidate.length > 0) {
                  return candidate;
                }
              }
              return null;
            }

            function formatCellValue(value: unknown): string {
              if (value === null || value === undefined) {
                return "—";
              }
              if (typeof value === "object") {
                return JSON.stringify(value);
              }
              return String(value);
            }

            function rowId(row: Record<string, unknown>): string {
              const candidate = row.id ?? row.ID ?? row.Id ?? row.uuid;
              return candidate != null ? String(candidate) : "";
            }

            // 상세 응답의 봉투를 한 겹 벗긴다(포털 api.ts unwrapEnvelope와 동일). {success, data:{...}}
            // 같은 래퍼면 실제 리소스 객체를 렌더하도록 data를 꺼낸다. 배열 값(목록)이나 봉투 아님은 제외.
            const ENVELOPE_KEYS = ["data", "result", "payload", "item", "content", "body"];
            function unwrapEnvelope(result: unknown): Record<string, unknown> | null {
              if (!result || typeof result !== "object" || Array.isArray(result)) {
                return (result ?? null) as Record<string, unknown> | null;
              }
              const obj = result as Record<string, unknown>;
              for (const key of ENVELOPE_KEYS) {
                const inner = obj[key];
                if (inner && typeof inner === "object" && !Array.isArray(inner)) {
                  return inner as Record<string, unknown>;
                }
              }
              return obj;
            }

            // 상태 배지 규칙 — 포털 status.ts와 동일 어휘/의미(둘 다 손대야 함). status/state/phase
            // 필드 값을 색으로 구분해 목록을 대시보드처럼 보이게 한다.
            type StatusTone = "ok" | "warn" | "idle" | "danger" | "neutral";
            const STATUS_TONE_BY_TOKEN: Record<string, StatusTone> = {};
            (
              [
                ["ok", ["running", "ready", "active", "available", "completed", "complete", "succeeded",
                  "success", "done", "healthy", "online", "approved", "enabled", "live", "passed", "ok", "up"]],
                ["warn", ["pending", "provisioning", "creating", "processing", "inprogress", "starting",
                  "queued", "initializing", "building", "deploying", "waiting", "scheduling", "restarting",
                  "stopping", "updating", "pausing", "retrying", "syncing"]],
                ["idle", ["stopped", "inactive", "disabled", "paused", "draft", "archived", "offline",
                  "closed", "expired", "suspended", "idle", "unknown", "down"]],
                ["danger", ["failed", "error", "terminated", "cancelled", "canceled", "rejected", "denied",
                  "crashed", "unhealthy", "timeout", "timedout", "deleted", "aborted", "declined"]],
              ] as [StatusTone, string[]][]
            ).forEach(([tone, tokens]) => tokens.forEach((token) => { STATUS_TONE_BY_TOKEN[token] = tone; }));

            function normalizeStatus(value: string): string {
              return value.toLowerCase().replace(/[_-]/g, "");
            }
            function isStatusKey(key: string): boolean {
              return ["status", "state", "phase"].includes(normalizeStatus(key));
            }
            function statusTone(value: unknown): StatusTone {
              if (typeof value !== "string") return "neutral";
              return STATUS_TONE_BY_TOKEN[normalizeStatus(value)] ?? "neutral";
            }
            function statusFieldOf(row: Record<string, unknown>): string | null {
              const keys = Object.keys(row);
              for (const preferred of ["status", "state", "phase"]) {
                const match = keys.find((key) => normalizeStatus(key) === preferred && typeof row[key] === "string");
                if (match) return match;
              }
              return null;
            }
            // 색은 index.css의 CSS 변수(--preview-status-*)에서 읽고 배경/테두리는 color-mix로 파생한다 —
            // tone당 변수 하나만 갈아끼우면 전체 색이 바뀐다. 변수 미정의 대비 fallback hex 포함.
            const TONE_VAR: Record<StatusTone, string> = {
              ok: "var(--preview-status-ok, #46d17f)",
              warn: "var(--preview-status-warn, #f5a623)",
              danger: "var(--preview-status-danger, #f2555a)",
              idle: "var(--preview-status-idle, #8b93a0)",
              neutral: "var(--preview-status-idle, #8b93a0)",
            };
            function toneStyle(tone: StatusTone): { color: string; background: string; borderColor: string } {
              const color = TONE_VAR[tone];
              return {
                color,
                background: `color-mix(in srgb, ${color} 14%, transparent)`,
                borderColor: `color-mix(in srgb, ${color} 32%, transparent)`,
              };
            }
            function summarizeStatus(rows: Record<string, unknown>[], fieldKey: string): { value: string; tone: StatusTone; count: number }[] {
              const order: string[] = [];
              const counts: Record<string, number> = {};
              for (const row of rows) {
                const raw = row[fieldKey];
                if (typeof raw !== "string") continue;
                if (counts[raw] === undefined) order.push(raw);
                counts[raw] = (counts[raw] ?? 0) + 1;
              }
              return order.map((value) => ({ value, tone: statusTone(value), count: counts[value] }));
            }
            function StatusBadge({ value }: { value: string }) {
              const style = toneStyle(statusTone(value));
              return (
                <span
                  style={{
                    display: "inline-flex", alignItems: "center", gap: 6, borderRadius: 999,
                    border: "1px solid " + style.borderColor, background: style.background, color: style.color,
                    padding: "3px 9px", fontSize: 11, fontWeight: 800, whiteSpace: "nowrap",
                  }}
                >
                  <span style={{ width: 6, height: 6, borderRadius: 999, background: style.color }} />
                  {value}
                </span>
              );
            }

            // Workflow Composition Phase 2 Change Request §7 "Navigation Requirements" — 배포된
            // 아티팩트에는 라우터 라이브러리가 없어(§13.2 "No arbitrary npm installation") 순수
            // History API로 선택 상태를 URL 쿼리파라미터에 반영한다. pushState는 popstate 이벤트를
            // 스스로 발생시키지 않으므로, App과 PageRenderer처럼 서로 다른 컴포넌트의 useQueryParam
            // 인스턴스끼리 동기화되도록 직접 popstate를 dispatch한다.
            function useQueryParam(key: string): [string | null, (value: string | null) => void] {
              const [value, setValue] = useState<string | null>(() =>
                new URLSearchParams(window.location.search).get(key)
              );

              useEffect(() => {
                function sync() {
                  setValue(new URLSearchParams(window.location.search).get(key));
                }
                window.addEventListener("popstate", sync);
                return () => window.removeEventListener("popstate", sync);
              }, [key]);

              function update(next: string | null) {
                const params = new URLSearchParams(window.location.search);
                if (next) {
                  params.set(key, next);
                } else {
                  params.delete(key);
                }
                const query = params.toString();
                window.history.pushState({}, "", query ? `?${query}` : window.location.pathname);
                window.dispatchEvent(new PopStateEvent("popstate"));
              }

              return [value, update];
            }

            function findCapabilityById(id: string): Capability | undefined {
              return CAPABILITIES.find((c) => c.id === id);
            }

            // PAGE_BLOCKS에서 특정 componentId(+선택적으로 mode)를 가진 block 하나를 찾아 그 block이
            // 가리키는 첫 capability를 반환한다. PageRenderer가 예전에 findCapabilityByType(page, "X")로
            // 하던 일을 이제 서버가 미리 계산해둔 Block 목록에서 찾는 것으로 대체한다.
            function findCapabilityForBlock(blocks: Block[], componentId: ComponentId, mode?: "CREATE" | "UPDATE"): Capability | undefined {
              const block = blocks.find((b) => b.componentId === componentId && (mode === undefined || b.mode === mode));
              const capabilityId = block?.capabilityIds[0];
              return capabilityId ? findCapabilityById(capabilityId) : undefined;
            }

            // list 계열은 BlueprintCompiler가 purpose에 따라 resource-table/resource-card-grid 중
            // 하나로 이미 컴파일해뒀다 — 어느 쪽이든 찾아서 실제로 어떤 컴포넌트를 마운트할지는
            // PageRenderer가 componentId를 보고 정한다.
            function findListBlock(blocks: Block[]): Block | undefined {
              return blocks.find((b) => b.componentId === "resource-table" || b.componentId === "resource-card-grid");
            }

            // destructive 계열도 마찬가지 — BlueprintCompiler가 purpose(ADMIN)에 따라
            // delete-confirm-modal/typed-confirm-modal 중 하나로 이미 컴파일해뒀다.
            function findDeleteBlock(blocks: Block[]): Block | undefined {
              return blocks.find((b) => b.componentId === "delete-confirm-modal" || b.componentId === "typed-confirm-modal");
            }

            // dashboard 계열도 마찬가지 — BlueprintCompiler가 purpose(PRODUCT_LIKE)에 따라
            // dashboard-view/recent-activity-dashboard 중 하나로 이미 컴파일해뒀다.
            function findDashboardBlock(blocks: Block[]): Block | undefined {
              return blocks.find((b) => b.componentId === "dashboard-view" || b.componentId === "recent-activity-dashboard");
            }

            // detail 계열도 마찬가지 — BlueprintCompiler가 purpose(PRODUCT_LIKE)에 따라 detail-panel/
            // full-detail-page 중 하나로 이미 컴파일해뒀다.
            function findDetailBlock(blocks: Block[]): Block | undefined {
              return blocks.find((b) => b.componentId === "detail-panel" || b.componentId === "full-detail-page");
            }

            // create/edit 계열도 마찬가지 — BlueprintCompiler가 purpose(PRODUCT_LIKE)에 따라
            // create-edit-modal/form-drawer 중 하나로 이미 컴파일해뒀다. mode로 생성/수정 인스턴스를 구분한다.
            function findCreateEditBlock(blocks: Block[], mode: "CREATE" | "UPDATE"): Block | undefined {
              return blocks.find(
                (b) => (b.componentId === "create-edit-modal" || b.componentId === "form-drawer") && b.mode === mode
              );
            }

            // §14 WP-8 "BindingRuntime" — 라이브 프리뷰 flow/runtime.ts의 createCapabilityBindingCaller와
            // 동일 — request.path는 값 하나만(사실상 하나만 있다고 가정) "id"로 넘긴다(buildUrl이 경로
            // 파라미터 이름과 무관하게 "경로의 마지막 {...}"만 치환하는 것과 짝을 이룸).
            function createCapabilityBindingCaller(authToken: string | null) {
              return async function callBinding(binding: ApiBinding, request: BindingRequest): Promise<unknown> {
                const capability = findCapabilityById(binding.capabilityId);
                if (!capability) {
                  throw new Error(`알 수 없는 capabilityId: ${binding.capabilityId}`);
                }
                const pathValue = Object.values(request.path)[0];
                return callCapability(capability, authToken, {
                  pathParams: pathValue !== undefined ? { id: pathValue } : {},
                  query: request.query,
                  body: Object.keys(request.body).length > 0 ? request.body : undefined,
                });
              };
            }

            function LoginForm({ capability, onLogin }: { capability: Capability; onLogin: (token: string) => void }) {
              const fields = capability.fields.length > 0 ? capability.fields : ["email", "password"];
              const [values, setValues] = useState<Record<string, string>>({});
              const [loading, setLoading] = useState(false);
              const [error, setError] = useState<string | null>(null);

              async function handleSubmit(e: FormEvent) {
                e.preventDefault();
                setError(null);
                setLoading(true);
                try {
                  const result = await callCapability(capability, null, { body: values });
                  const token = extractToken(result, capability.accessTokenPath);
                  if (!token) {
                    setError("응답에서 토큰을 찾지 못했습니다.");
                    return;
                  }
                  onLogin(token);
                } catch (err) {
                  setError(err instanceof Error ? err.message : "로그인에 실패했습니다");
                } finally {
                  setLoading(false);
                }
              }

              return (
                <form onSubmit={handleSubmit} style={{ maxWidth: 360, margin: "0 auto" }}>
                  {fields.map((field) => (
                    <label key={field} className="field">
                      {field}
                      <input
                        type={isPasswordLikeField(field) ? "password" : "text"}
                        value={values[field] ?? ""}
                        onChange={(e) => setValues((prev) => ({ ...prev, [field]: e.target.value }))}
                        required
                      />
                    </label>
                  ))}
                  {error && <p className="error">{error}</p>}
                  <button type="submit" className="primary" disabled={loading}>
                    {loading ? "로그인 중..." : "로그인"}
                  </button>
                </form>
              );
            }

            function ResourceTable({
              capability, authToken, onRowClick, onCreateClick, refreshKey,
            }: {
              capability: Capability;
              authToken: string | null;
              onRowClick?: (row: Record<string, unknown>) => void;
              onCreateClick?: () => void;
              refreshKey: number;
            }) {
              const [rows, setRows] = useState<Record<string, unknown>[]>([]);
              const [loading, setLoading] = useState(true);
              const [error, setError] = useState<string | null>(null);
              const [search, setSearch] = useState("");

              useEffect(() => {
                let cancelled = false;
                Promise.resolve().then(async () => {
                  if (cancelled) return;
                  setLoading(true);
                  setError(null);
                  const query: Record<string, string> = {};
                  if (capability.hasSearch && search) {
                    query[capability.searchParam ?? "search"] = search;
                  }
                  try {
                    const result = await callCapability(capability, authToken, { query });
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
              }, [capability, authToken, search, refreshKey]);

              const columns = rows.length > 0 ? Object.keys(rows[0]) : [];

              return (
                <div>
                  <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 12, gap: 8 }}>
                    {capability.hasSearch ? (
                      <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="검색" style={{ maxWidth: 240 }} />
                    ) : (
                      <span />
                    )}
                    {onCreateClick && (
                      <button className="primary" onClick={onCreateClick}>
                        + 추가
                      </button>
                    )}
                  </div>
                  {loading ? (
                    <p className="muted">불러오는 중...</p>
                  ) : error ? (
                    <p className="error">{error}</p>
                  ) : rows.length === 0 ? (
                    <p className="muted">데이터가 없습니다</p>
                  ) : (
                    <table>
                      <thead>
                        <tr>
                          {columns.map((column) => (
                            <th key={column}>{column}</th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {rows.map((row, index) => (
                          <tr key={index} onClick={() => onRowClick?.(row)} style={{ cursor: onRowClick ? "pointer" : undefined }}>
                            {columns.map((column) => (
                              <td key={column}>
                                {isStatusKey(column) && typeof row[column] === "string"
                                  ? <StatusBadge value={row[column] as string} />
                                  : formatCellValue(row[column])}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              );
            }

            // Direction Recovery Change Request §9.1 "resource-card-grid" — ResourceTable과 데이터
            // fetching·props는 완전히 동일하고(그래서 BlueprintCompiler가 componentId만 보고 갈아끼울
            // 수 있음), 표 대신 카드 그리드로 보여준다. PRODUCT_LIKE 목적일 때 고른다.
            function ResourceCardGrid({
              capability, authToken, onRowClick, onCreateClick, refreshKey,
            }: {
              capability: Capability;
              authToken: string | null;
              onRowClick?: (row: Record<string, unknown>) => void;
              onCreateClick?: () => void;
              refreshKey: number;
            }) {
              const [rows, setRows] = useState<Record<string, unknown>[]>([]);
              const [loading, setLoading] = useState(true);
              const [error, setError] = useState<string | null>(null);
              const [search, setSearch] = useState("");

              useEffect(() => {
                let cancelled = false;
                Promise.resolve().then(async () => {
                  if (cancelled) return;
                  setLoading(true);
                  setError(null);
                  const query: Record<string, string> = {};
                  if (capability.hasSearch && search) {
                    query[capability.searchParam ?? "search"] = search;
                  }
                  try {
                    const result = await callCapability(capability, authToken, { query });
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
              }, [capability, authToken, search, refreshKey]);

              const statusField = rows.length > 0 ? statusFieldOf(rows[0]) : null;
              const summary = statusField ? summarizeStatus(rows, statusField) : [];

              return (
                <div>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12, gap: 8 }}>
                    <div style={{ display: "flex", alignItems: "baseline", gap: 8 }}>
                      <h3 style={{ fontSize: 14, fontWeight: 800, margin: 0, textTransform: "capitalize" }}>{capability.resourceName}</h3>
                      {rows.length > 0 && <span className="muted" style={{ fontSize: 12, fontWeight: 600 }}>{rows.length}</span>}
                    </div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                      {capability.hasSearch && (
                        <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="검색" style={{ maxWidth: 180 }} />
                      )}
                      {onCreateClick && (
                        <button className="primary" onClick={onCreateClick}>
                          + 추가
                        </button>
                      )}
                    </div>
                  </div>
                  {summary.length > 0 && (
                    <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginBottom: 12 }}>
                      {summary.map((group) => {
                        const style = toneStyle(group.tone);
                        return (
                          <span
                            key={group.value}
                            style={{
                              display: "inline-flex", alignItems: "center", gap: 6, borderRadius: 8,
                              border: "1px solid " + style.borderColor, background: style.background, color: style.color,
                              padding: "4px 10px", fontSize: 12, fontWeight: 700,
                            }}
                          >
                            <span style={{ width: 6, height: 6, borderRadius: 999, background: style.color }} />
                            {group.value}
                            <span style={{ opacity: 0.7 }}>{group.count}</span>
                          </span>
                        );
                      })}
                    </div>
                  )}
                  {loading ? (
                    <p className="muted">불러오는 중...</p>
                  ) : error ? (
                    <p className="error">{error}</p>
                  ) : rows.length === 0 ? (
                    <p className="muted">데이터가 없습니다</p>
                  ) : (
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 12 }}>
                      {rows.map((row, index) => {
                        const title = row.name ?? row.title ?? row.label ?? rowId(row);
                        const statusValue = statusField ? row[statusField] : undefined;
                        const detailEntries = Object.entries(row).filter(
                          ([key]) => key !== statusField && !["name", "title", "label"].includes(key)
                        );
                        return (
                          <div
                            key={index}
                            onClick={() => onRowClick?.(row)}
                            className="panel"
                            style={{ cursor: onRowClick ? "pointer" : undefined }}
                          >
                            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 8 }}>
                              <p style={{ fontWeight: 800, margin: 0, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                                {formatCellValue(title)}
                              </p>
                              {typeof statusValue === "string" && <StatusBadge value={statusValue} />}
                            </div>
                            <div style={{ marginTop: 12, display: "grid", gap: 4 }}>
                              {detailEntries.slice(0, 4).map(([key, value]) => (
                                <div key={key} style={{ display: "flex", justifyContent: "space-between", gap: 8, fontSize: 12 }}>
                                  <span className="muted">{key}</span>
                                  <span style={{ overflow: "hidden", textOverflow: "ellipsis" }}>{formatCellValue(value)}</span>
                                </div>
                              ))}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            }

            function DetailPanel({
              capability, authToken, id, refreshKey,
            }: {
              capability: Capability;
              authToken: string | null;
              id: string;
              // Workflow Composition Phase 2 Change Request §9 "refresh related list or detail bindings
              // after success" — 생성/수정/삭제/커맨드 성공 후에도 이미 열린 상세가 갱신 안 되던 결함.
              refreshKey?: number;
            }) {
              const [data, setData] = useState<Record<string, unknown> | null>(null);
              const [loading, setLoading] = useState(true);
              const [error, setError] = useState<string | null>(null);

              useEffect(() => {
                let cancelled = false;
                Promise.resolve().then(async () => {
                  if (cancelled) return;
                  setLoading(true);
                  setError(null);
                  try {
                    const result = await callCapability(capability, authToken, { pathParams: { id } });
                    if (!cancelled) setData(unwrapEnvelope(result));
                  } catch (err) {
                    if (!cancelled) setError(err instanceof Error ? err.message : "상세 정보를 불러오지 못했습니다");
                  } finally {
                    if (!cancelled) setLoading(false);
                  }
                });
                return () => {
                  cancelled = true;
                };
              }, [capability, authToken, id, refreshKey]);

              if (loading) {
                return <p className="muted">불러오는 중...</p>;
              }
              if (error) {
                return <p className="error">{error}</p>;
              }
              if (!data) {
                return null;
              }

              const statusField = statusFieldOf(data);
              const statusValue = statusField ? data[statusField] : undefined;
              const fieldEntries = Object.entries(data).filter(([key]) => key !== statusField);

              return (
                <div>
                  {typeof statusValue === "string" && (
                    <div style={{ marginBottom: 12 }}><StatusBadge value={statusValue} /></div>
                  )}
                  {fieldEntries.map(([key, value]) => (
                    <div
                      key={key}
                      style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: "1px solid #262b26", fontSize: 13 }}
                    >
                      <span className="muted">{key}</span>
                      <span style={{ fontFamily: "monospace" }}>{formatCellValue(value)}</span>
                    </div>
                  ))}
                </div>
              );
            }

            // Direction Recovery Change Request §9.2 "full-detail-page" — DetailPanel과 데이터
            // 요구조건(DETAIL capability)은 동일하고, 좁은 사이드 칼럼 대신 필드를 카드형 그리드로
            // 넓게 펼쳐 보여준다.
            function FullDetailPage({
              capability, authToken, id, refreshKey,
            }: {
              capability: Capability;
              authToken: string | null;
              id: string;
              refreshKey?: number;
            }) {
              const [data, setData] = useState<Record<string, unknown> | null>(null);
              const [loading, setLoading] = useState(true);
              const [error, setError] = useState<string | null>(null);

              useEffect(() => {
                let cancelled = false;
                Promise.resolve().then(async () => {
                  if (cancelled) return;
                  setLoading(true);
                  setError(null);
                  try {
                    const result = await callCapability(capability, authToken, { pathParams: { id } });
                    if (!cancelled) setData(unwrapEnvelope(result));
                  } catch (err) {
                    if (!cancelled) setError(err instanceof Error ? err.message : "상세 정보를 불러오지 못했습니다");
                  } finally {
                    if (!cancelled) setLoading(false);
                  }
                });
                return () => {
                  cancelled = true;
                };
              }, [capability, authToken, id, refreshKey]);

              if (loading) {
                return <p className="muted">불러오는 중...</p>;
              }
              if (error) {
                return <p className="error">{error}</p>;
              }
              if (!data) {
                return null;
              }

              const statusField = statusFieldOf(data);
              const statusValue = statusField ? data[statusField] : undefined;
              const fieldEntries = Object.entries(data).filter(([key]) => key !== statusField);

              return (
                <div>
                  {typeof statusValue === "string" && (
                    <div style={{ marginBottom: 14 }}><StatusBadge value={statusValue} /></div>
                  )}
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(160px, 1fr))", gap: 12 }}>
                    {fieldEntries.map(([key, value]) => (
                      <div key={key} className="panel" style={{ padding: 12 }}>
                        <p className="muted" style={{ fontSize: 11 }}>{key}</p>
                        <p style={{ fontFamily: "monospace", fontSize: 13, marginTop: 4, wordBreak: "break-all" }}>
                          {formatCellValue(value)}
                        </p>
                      </div>
                    ))}
                  </div>
                </div>
              );
            }
            """;

    private static final String APP_TSX_TEMPLATE_PART_2 = """
            function CreateEditModal({
              capability, authToken, initialValues, onClose, onSuccess, onSubmitOverride, pathParamId,
            }: {
              capability: Capability;
              authToken: string | null;
              initialValues?: Record<string, unknown>;
              onClose: () => void;
              onSuccess: () => void;
              // 이 페이지의 CREATE 액션에 FlowBlueprint가 배정돼 있으면 이 모달은 API를 직접 안 부르고
              // 폼 값만 넘긴다 — 호출 측(PageRenderer)이 executeFlow로 전체 flow를 실행한다.
              onSubmitOverride?: (values: Record<string, string>) => Promise<void>;
              pathParamId?: string;
            }) {
              const fields = capability.fields;
              const [values, setValues] = useState<Record<string, string>>(() =>
                Object.fromEntries(fields.map((field) => [field, initialValues?.[field] != null ? String(initialValues[field]) : ""]))
              );
              const [loading, setLoading] = useState(false);
              const [error, setError] = useState<string | null>(null);

              async function handleSubmit(e: FormEvent) {
                e.preventDefault();
                setError(null);
                setLoading(true);
                try {
                  if (onSubmitOverride) {
                    await onSubmitOverride(values);
                  } else {
                    const resolvedPathId = pathParamId ?? (initialValues ? rowId(initialValues) : "");
                    const pathParams: Record<string, string> = resolvedPathId ? { id: resolvedPathId } : {};
                    await callCapability(capability, authToken, { body: values, pathParams });
                    onSuccess();
                  }
                  onClose();
                } catch (err) {
                  setError(err instanceof Error ? err.message : "저장에 실패했습니다");
                } finally {
                  setLoading(false);
                }
              }

              return (
                <div className="modal-backdrop" onClick={onClose}>
                  <div className="modal" onClick={(e) => e.stopPropagation()}>
                    <h2 style={{ marginTop: 0 }}>{capability.type === "CREATE" ? "생성" : "수정"}</h2>
                    <form onSubmit={handleSubmit}>
                      {fields.length === 0 && <p className="muted">이 API의 요청 필드를 확인하지 못했습니다.</p>}
                      {fields.map((field) => (
                        <label key={field} className="field">
                          {field}
                          <input
                            type={isPasswordLikeField(field) ? "password" : "text"}
                            value={values[field] ?? ""}
                            onChange={(e) => setValues((prev) => ({ ...prev, [field]: e.target.value }))}
                          />
                        </label>
                      ))}
                      {error && <p className="error">{error}</p>}
                      <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
                        <button type="button" className="plain" style={{ flex: 1 }} onClick={onClose}>
                          취소
                        </button>
                        <button type="submit" className="primary" style={{ flex: 1 }} disabled={loading}>
                          {loading ? "저장 중..." : "저장"}
                        </button>
                      </div>
                    </form>
                  </div>
                </div>
              );
            }

            // Direction Recovery Change Request §9.3 "form-drawer" — CreateEditModal과 데이터
            // 동작·props는 동일하고, 화면 오른쪽에서 열리는 패널로 보여준다. PRODUCT_LIKE 목적일 때
            // 고른다(§3 "Cards, detail pages, drawers, and guided creation flows").
            function FormDrawer({
              capability, authToken, initialValues, onClose, onSuccess, onSubmitOverride, pathParamId,
            }: {
              capability: Capability;
              authToken: string | null;
              initialValues?: Record<string, unknown>;
              onClose: () => void;
              onSuccess: () => void;
              // CreateEditModal과 동일 — FlowBlueprint가 배정된 CREATE 액션이면 폼 값만 넘긴다.
              onSubmitOverride?: (values: Record<string, string>) => Promise<void>;
              pathParamId?: string;
            }) {
              const fields = capability.fields;
              const [values, setValues] = useState<Record<string, string>>(() =>
                Object.fromEntries(fields.map((field) => [field, initialValues?.[field] != null ? String(initialValues[field]) : ""]))
              );
              const [loading, setLoading] = useState(false);
              const [error, setError] = useState<string | null>(null);

              async function handleSubmit(e: FormEvent) {
                e.preventDefault();
                setError(null);
                setLoading(true);
                try {
                  if (onSubmitOverride) {
                    await onSubmitOverride(values);
                  } else {
                    const resolvedPathId = pathParamId ?? (initialValues ? rowId(initialValues) : "");
                    const pathParams: Record<string, string> = resolvedPathId ? { id: resolvedPathId } : {};
                    await callCapability(capability, authToken, { body: values, pathParams });
                    onSuccess();
                  }
                  onClose();
                } catch (err) {
                  setError(err instanceof Error ? err.message : "저장에 실패했습니다");
                } finally {
                  setLoading(false);
                }
              }

              return (
                <div className="drawer-backdrop" onClick={onClose}>
                  <div className="drawer" onClick={(e) => e.stopPropagation()}>
                    <h2 style={{ marginTop: 0 }}>{capability.type === "CREATE" ? "생성" : "수정"}</h2>
                    <form onSubmit={handleSubmit}>
                      {fields.length === 0 && <p className="muted">이 API의 요청 필드를 확인하지 못했습니다.</p>}
                      {fields.map((field) => (
                        <label key={field} className="field">
                          {field}
                          <input
                            type={isPasswordLikeField(field) ? "password" : "text"}
                            value={values[field] ?? ""}
                            onChange={(e) => setValues((prev) => ({ ...prev, [field]: e.target.value }))}
                          />
                        </label>
                      ))}
                      {error && <p className="error">{error}</p>}
                      <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
                        <button type="button" className="plain" style={{ flex: 1 }} onClick={onClose}>
                          취소
                        </button>
                        <button type="submit" className="primary" style={{ flex: 1 }} disabled={loading}>
                          {loading ? "저장 중..." : "저장"}
                        </button>
                      </div>
                    </form>
                  </div>
                </div>
              );
            }

            function ChildResourceList({
              listCapability, createCapability, deleteCapability, authToken, parentId, refreshKey,
            }: {
              listCapability: Capability;
              createCapability?: Capability;
              deleteCapability?: Capability;
              authToken: string | null;
              parentId: string;
              refreshKey?: number;
            }) {
              const [rows, setRows] = useState<Record<string, unknown>[]>([]);
              const [loading, setLoading] = useState(true);
              const [error, setError] = useState<string | null>(null);
              const [createOpen, setCreateOpen] = useState(false);
              const [deletingId, setDeletingId] = useState<string | null>(null);
              const [localRefreshKey, setLocalRefreshKey] = useState(0);

              async function handleDelete(row: Record<string, unknown>) {
                if (!deleteCapability) return;
                const childId = rowId(row);
                if (!window.confirm("이 항목을 삭제하시겠습니까?")) return;
                setDeletingId(childId);
                setError(null);
                try {
                  // 자식 액션 경로는 부모+자식 두 파라미터를 가진다(/machines/{machineId}/ports/{portId}) —
                  // placeholder 이름을 뽑아 마지막(자식 자신)에 행 id, 앞쪽(부모)에 parentId를 채운다.
                  const names = Array.from(deleteCapability.path.matchAll(/\\{([^}]+)\\}/g), (match) => match[1]);
                  const params: Record<string, string> = {};
                  names.forEach((name, index) => { params[name] = index === names.length - 1 ? childId : parentId; });
                  await callCapability(deleteCapability, authToken, { pathParams: params });
                  setLocalRefreshKey((key) => key + 1);
                } catch (err) {
                  setError(err instanceof Error ? err.message : "삭제하지 못했습니다");
                } finally {
                  setDeletingId(null);
                }
              }

              useEffect(() => {
                let cancelled = false;
                Promise.resolve().then(async () => {
                  if (cancelled || !parentId) return;
                  setLoading(true);
                  setError(null);
                  try {
                    const result = await callCapability(listCapability, authToken, { pathParams: { id: parentId } });
                    if (!cancelled) setRows(extractArray(result, listCapability.collectionPath));
                  } catch (err) {
                    if (!cancelled) setError(err instanceof Error ? err.message : "하위 리소스를 불러오지 못했습니다");
                  } finally {
                    if (!cancelled) setLoading(false);
                  }
                });
                return () => { cancelled = true; };
              }, [listCapability, authToken, parentId, refreshKey, localRefreshKey]);

              const columns = rows.length > 0 ? Object.keys(rows[0]) : [];
              return (
                <section className="panel" style={{ marginTop: 16, padding: 16, background: "rgba(0,0,0,0.10)" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, marginBottom: 12 }}>
                    <div>
                      <span className="muted" style={{ textTransform: "uppercase", letterSpacing: 1.1 }}>Related resources</span>
                      <h3 style={{ margin: "3px 0 0", fontSize: 14 }}>{listCapability.resourceName}</h3>
                    </div>
                    {createCapability && (
                      <button className="plain" onClick={() => setCreateOpen(true)}>+ 추가</button>
                    )}
                  </div>
                  {loading ? (
                    <p className="muted">하위 리소스 불러오는 중...</p>
                  ) : error ? (
                    <p className="error">{error}</p>
                  ) : rows.length === 0 ? (
                    <p className="muted" style={{ textAlign: "center", padding: 16 }}>연결된 항목이 없습니다</p>
                  ) : (
                    <div style={{ overflowX: "auto" }}>
                      <table>
                        <thead><tr>{columns.map((column) => <th key={column}>{column}</th>)}{deleteCapability && <th> </th>}</tr></thead>
                        <tbody>
                          {rows.map((row, index) => (
                            <tr key={index}>
                              {columns.map((column) => <td key={column}>{formatCellValue(row[column])}</td>)}
                              {deleteCapability && (
                                <td>
                                  <button className="plain" style={{ color: "var(--danger, #e5484d)" }}
                                    disabled={deletingId === rowId(row)} onClick={() => handleDelete(row)}>
                                    {deletingId === rowId(row) ? "삭제 중..." : "삭제"}
                                  </button>
                                </td>
                              )}
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                  {createCapability && createOpen && (
                    <CreateEditModal
                      capability={createCapability}
                      authToken={authToken}
                      pathParamId={parentId}
                      onClose={() => setCreateOpen(false)}
                      onSuccess={() => setLocalRefreshKey((key) => key + 1)}
                    />
                  )}
                </section>
              );
            }

            function DeleteConfirmModal({
              capability, authToken, targetId, onClose, onSuccess,
            }: {
              capability: Capability;
              authToken: string | null;
              targetId: string;
              onClose: () => void;
              onSuccess: () => void;
            }) {
              const [loading, setLoading] = useState(false);
              const [error, setError] = useState<string | null>(null);

              async function handleConfirm() {
                setError(null);
                setLoading(true);
                try {
                  await callCapability(capability, authToken, { pathParams: { id: targetId } });
                  onSuccess();
                  onClose();
                } catch (err) {
                  setError(err instanceof Error ? err.message : "삭제에 실패했습니다");
                } finally {
                  setLoading(false);
                }
              }

              return (
                <div className="modal-backdrop" onClick={onClose}>
                  <div className="modal" onClick={(e) => e.stopPropagation()}>
                    <h2 style={{ marginTop: 0 }}>{capability.resourceName} 삭제</h2>
                    <p className="muted">삭제하면 복구할 수 없습니다. 계속하시겠습니까?</p>
                    {error && <p className="error">{error}</p>}
                    <div style={{ display: "flex", gap: 8 }}>
                      <button className="plain" style={{ flex: 1 }} onClick={onClose} disabled={loading}>
                        취소
                      </button>
                      <button className="danger" style={{ flex: 1 }} onClick={handleConfirm} disabled={loading}>
                        {loading ? "삭제 중..." : "삭제"}
                      </button>
                    </div>
                  </div>
                </div>
              );
            }

            // Direction Recovery Change Request §9.4 "typed-confirm-modal" — DeleteConfirmModal과
            // 데이터 동작·props는 동일하고, 리소스명을 정확히 입력해야만 삭제 버튼이 활성화된다.
            // ADMIN 목적일 때 고른다(§3 "Destructive-operation safeguards").
            function TypedConfirmModal({
              capability, authToken, targetId, onClose, onSuccess,
            }: {
              capability: Capability;
              authToken: string | null;
              targetId: string;
              onClose: () => void;
              onSuccess: () => void;
            }) {
              const [confirmText, setConfirmText] = useState("");
              const [loading, setLoading] = useState(false);
              const [error, setError] = useState<string | null>(null);
              const matches = confirmText.trim() === capability.resourceName;

              async function handleConfirm() {
                if (!matches) return;
                setError(null);
                setLoading(true);
                try {
                  await callCapability(capability, authToken, { pathParams: { id: targetId } });
                  onSuccess();
                  onClose();
                } catch (err) {
                  setError(err instanceof Error ? err.message : "삭제에 실패했습니다");
                } finally {
                  setLoading(false);
                }
              }

              return (
                <div className="modal-backdrop" onClick={onClose}>
                  <div className="modal" onClick={(e) => e.stopPropagation()}>
                    <h2 style={{ marginTop: 0 }}>{capability.resourceName} 삭제</h2>
                    <p className="muted">
                      삭제하면 복구할 수 없습니다. 계속하려면 <strong>{capability.resourceName}</strong>을(를) 입력하세요.
                    </p>
                    <input
                      value={confirmText}
                      onChange={(e) => setConfirmText(e.target.value)}
                      placeholder={capability.resourceName}
                      style={{ width: "100%", marginBottom: 12 }}
                    />
                    {error && <p className="error">{error}</p>}
                    <div style={{ display: "flex", gap: 8 }}>
                      <button className="plain" style={{ flex: 1 }} onClick={onClose} disabled={loading}>
                        취소
                      </button>
                      <button className="danger" style={{ flex: 1 }} onClick={handleConfirm} disabled={loading || !matches}>
                        {loading ? "삭제 중..." : "삭제"}
                      </button>
                    </div>
                  </div>
                </div>
              );
            }

            // Direction Recovery Change Request §9.6 "quick-action-button-group" — command 계열
            // (vm.start 등)의 첫 Variant. AutomationPolicy가 항상 USER_INITIATED로 고정 배정되므로
            // TypedConfirmModal과 달리 확인 없이 클릭하면 바로 실행한다.
            function QuickActionButtonGroup({
              capabilities, authToken, targetId, onSuccess, onExecute,
            }: {
              capabilities: Capability[];
              authToken: string | null;
              targetId: string;
              onSuccess?: () => void;
              onExecute?: (capability: Capability) => Promise<void>;
            }) {
              const [pendingId, setPendingId] = useState<string | null>(null);
              const [errors, setErrors] = useState<Record<string, string>>({});

              async function handleClick(capability: Capability) {
                setErrors((prev) => ({ ...prev, [capability.id]: "" }));
                setPendingId(capability.id);
                try {
                  if (onExecute) {
                    await onExecute(capability);
                  } else {
                    await callCapability(capability, authToken, { pathParams: { id: targetId } });
                  }
                  onSuccess?.();
                } catch (err) {
                  setErrors((prev) => ({
                    ...prev,
                    [capability.id]: err instanceof Error ? err.message : "요청에 실패했습니다",
                  }));
                } finally {
                  setPendingId(null);
                }
              }

              if (capabilities.length === 0) {
                return null;
              }

              return (
                <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
                  {capabilities.map((capability) => (
                    <div key={capability.id} style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                      <button className="plain" disabled={pendingId !== null} onClick={() => handleClick(capability)}>
                        {pendingId === capability.id ? "처리 중..." : (capability.action ?? capability.id)}
                      </button>
                      {errors[capability.id] && <p className="error" style={{ fontSize: 11 }}>{errors[capability.id]}</p>}
                    </div>
                  ))}
                </div>
              );
            }

            interface DashboardCountState {
              loading: boolean;
              value: number | null;
              error: string | null;
            }

            // Dashboard 스켈레톤 — 어떤 리소스가 중요한지 판단하지 않고, page.capabilityIds에 모인
            // LIST capability마다 개수 카드만 기계적으로 보여준다.
            function DashboardView({ capabilities, authToken }: { capabilities: Capability[]; authToken: string | null }) {
              const [counts, setCounts] = useState<Record<string, DashboardCountState>>({});

              // 부모(PageRenderer)가 capabilities 배열을 매 렌더마다 새로 만들어서, 배열 참조를 deps에
              // 넣으면 로드→apiCallListener→로그 setState→리렌더→새 배열→effect 재발화로 무한 요청이
              // 된다. 배열 참조 대신 안정적인 capability id 키에만 의존한다.
              const capabilityKey = capabilities.map((capability) => capability.id).join(",");

              useEffect(() => {
                let cancelled = false;
                capabilities.forEach((capability) => {
                  callCapability(capability, authToken)
                    .then((result) => {
                      if (cancelled) return;
                      setCounts((prev) => ({
                        ...prev,
                        [capability.id]: {
                          loading: false,
                          value: extractCount(result, capability.totalCountPath, capability.collectionPath),
                          error: null,
                        },
                      }));
                    })
                    .catch((err) => {
                      if (cancelled) return;
                      setCounts((prev) => ({
                        ...prev,
                        [capability.id]: { loading: false, value: null, error: err instanceof Error ? err.message : "불러오기 실패" },
                      }));
                    });
                });
                return () => {
                  cancelled = true;
                };
              }, [capabilityKey, authToken]);

              if (capabilities.length === 0) {
                return <p className="error">이 페이지에 표시할 목록 capability가 없습니다.</p>;
              }

              return (
                <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(140px, 1fr))", gap: 12 }}>
                  {capabilities.map((capability) => {
                    const state = counts[capability.id] ?? { loading: true, value: null, error: null };
                    return (
                      <div key={capability.id} className="panel" style={{ padding: 16 }}>
                        <p className="muted">{capability.resourceName}</p>
                        <p style={{ fontSize: 24, fontWeight: 800, margin: "4px 0 0" }}>
                          {state.loading ? "…" : state.error ? "—" : (state.value ?? "—")}
                        </p>
                        {state.error && <p className="error">{state.error}</p>}
                      </div>
                    );
                  })}
                </div>
              );
            }

            const MAX_ROWS_PER_RESOURCE = 5;
            const ID_LIKE_FIELDS = ["id", "ID", "Id", "uuid"];

            // 행 하나를 사람이 읽을 수 있는 한 줄 요약으로 압축한다 — 스키마를 모르는 채로 임의 API
            // 응답을 보여줘야 해서, id를 뺀 처음 두 필드만 보여준다.
            function summarizeRow(row: Record<string, unknown>): string {
              const entries = Object.entries(row)
                .filter(([key]) => !ID_LIKE_FIELDS.includes(key))
                .slice(0, 2);
              if (entries.length === 0) {
                return rowId(row);
              }
              return entries.map(([key, value]) => `${key}: ${formatCellValue(value)}`).join(" · ");
            }

            interface FeedState {
              loading: boolean;
              rows: Record<string, unknown>[];
              error: string | null;
            }

            // Direction Recovery Change Request §9.5 "recent-activity-dashboard" — dashboard-view와
            // 데이터 요구조건(LIST capability 여러 개)은 동일하고, 개수 카드 대신 리소스마다 최근 항목
            // 몇 개를 피드 형태로 보여준다.
            function RecentActivityDashboard({
              capabilities, authToken,
            }: {
              capabilities: Capability[];
              authToken: string | null;
            }) {
              const [feeds, setFeeds] = useState<Record<string, FeedState>>({});

              // capabilities 배열은 부모가 매 렌더마다 새로 만든다 — 배열 참조를 deps에 넣으면 무한
              // 요청이 되므로 안정적인 capability id 키에만 의존한다(DashboardView와 동일).
              const capabilityKey = capabilities.map((capability) => capability.id).join(",");

              useEffect(() => {
                let cancelled = false;
                capabilities.forEach((capability) => {
                  callCapability(capability, authToken)
                    .then((result) => {
                      if (cancelled) return;
                      const rows = extractArray(result, capability.collectionPath).slice(0, MAX_ROWS_PER_RESOURCE);
                      setFeeds((prev) => ({ ...prev, [capability.id]: { loading: false, rows, error: null } }));
                    })
                    .catch((err) => {
                      if (cancelled) return;
                      setFeeds((prev) => ({
                        ...prev,
                        [capability.id]: { loading: false, rows: [], error: err instanceof Error ? err.message : "불러오기 실패" },
                      }));
                    });
                });
                return () => {
                  cancelled = true;
                };
              }, [capabilityKey, authToken]);

              if (capabilities.length === 0) {
                return <p className="error">이 페이지에 표시할 목록 capability가 없습니다.</p>;
              }

              return (
                <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 12 }}>
                  {capabilities.map((capability) => {
                    const state = feeds[capability.id] ?? { loading: true, rows: [], error: null };
                    return (
                      <div key={capability.id} className="panel" style={{ padding: 16 }}>
                        <p className="muted">{capability.resourceName}</p>
                        {state.loading && <p className="muted">불러오는 중...</p>}
                        {state.error && <p className="error">{state.error}</p>}
                        {!state.loading && !state.error && state.rows.length === 0 && (
                          <p className="muted">항목이 없습니다</p>
                        )}
                        <ul style={{ margin: "8px 0 0", paddingLeft: 16, fontSize: 12 }}>
                          {state.rows.map((row, index) => (
                            <li key={rowId(row) || index}>{summarizeRow(row)}</li>
                          ))}
                        </ul>
                      </div>
                    );
                  })}
                </div>
              );
            }

            function PageRenderer({ page, authToken, onLogin }: { page: PageDraft; authToken: string | null; onLogin: (token: string) => void }) {
              const [selectedId, setSelectedId] = useQueryParam("selected");
              const [selectedRowObject, setSelectedRowObject] = useState<Record<string, unknown> | null>(null);
              const selectedRow = selectedId
                ? selectedRowObject && rowId(selectedRowObject) === selectedId
                  ? selectedRowObject
                  : { id: selectedId }
                : null;
              const [overlay, setOverlay] = useState<OverlayState>({ kind: "NONE" });
              const [refreshKey, setRefreshKey] = useState(0);

              // §7 — 페이지가 실제로 바뀔 때만(최초 마운트 제외) 선택 상태를 지운다. 최초 마운트에서
              // 지워버리면 새로고침 직후 URL의 selected가 곧바로 사라져 AC-2("새로고침 생존")를 깬다.
              const previousPageIdRef = useRef<string | undefined>(undefined);
              useEffect(() => {
                if (previousPageIdRef.current !== undefined && previousPageIdRef.current !== page.id) {
                  setSelectedId(null);
                  setSelectedRowObject(null);
                }
                previousPageIdRef.current = page.id;
              }, [page.id]);

              function selectRow(row: Record<string, unknown> | null) {
                setSelectedRowObject(row);
                setSelectedId(row ? rowId(row) : null);
              }

              const blocks = PAGE_BLOCKS[page.id] ?? [];

              if (page.skeleton === "AUTH_PAGE") {
                const login = findCapabilityForBlock(blocks, "login-form");
                if (!login) {
                  return <p className="error">이 페이지에 로그인 capability가 없습니다.</p>;
                }
                return <LoginForm capability={login} onLogin={onLogin} />;
              }

              if (page.skeleton === "DASHBOARD") {
                const dashboardBlock = findDashboardBlock(blocks);
                const listCapabilities = (dashboardBlock?.capabilityIds ?? [])
                  .map((id) => findCapabilityById(id))
                  .filter((c): c is Capability => c !== undefined);
                return dashboardBlock?.componentId === "recent-activity-dashboard" ? (
                  <RecentActivityDashboard capabilities={listCapabilities} authToken={authToken} />
                ) : (
                  <DashboardView capabilities={listCapabilities} authToken={authToken} />
                );
              }

              const listBlock = findListBlock(blocks);
              const list = listBlock ? findCapabilityById(listBlock.capabilityIds[0]) : undefined;
              const detailBlock = findDetailBlock(blocks);
              const detail = detailBlock ? findCapabilityById(detailBlock.capabilityIds[0]) : undefined;
              const isFullDetailPage = detailBlock?.componentId === "full-detail-page";
              const createBlock = findCreateEditBlock(blocks, "CREATE");
              const create = createBlock ? findCapabilityById(createBlock.capabilityIds[0]) : undefined;
              const updateBlock = findCreateEditBlock(blocks, "UPDATE");
              const update = updateBlock ? findCapabilityById(updateBlock.capabilityIds[0]) : undefined;
              const deleteBlock = findDeleteBlock(blocks);
              const del = deleteBlock ? findCapabilityById(deleteBlock.capabilityIds[0]) : undefined;
              const commandBlock = blocks.find((b) => b.componentId === "quick-action-button-group");
              const commandCapabilities = (commandBlock?.capabilityIds ?? [])
                .map((id) => findCapabilityById(id))
                .filter((c): c is Capability => c !== undefined);
              const childBlocks = blocks.filter((block) => block.componentId === "child-resource-list");

              if (!list) {
                return <p className="error">이 페이지에 목록 capability가 없습니다.</p>;
              }

              function refresh() {
                setRefreshKey((key) => key + 1);
              }

              // §14 WP-8 "FlowExecutor를 실제로 배선" — 이 페이지의 CREATE 액션에 RuleBasedFlowGenerator가
              // 만든 flow가 있으면 CreateEditModal/FormDrawer가 직접 API를 안 부르고 폼 값만 넘겨, 여기서
              // flow 전체(API_CALL→NAVIGATE)를 실행한다. NAVIGATE는 지금 생성기가 항상 자기 자신(같은
              // 페이지)+selected 파라미터로만 만들어서 selectRow 호출로 충분하다(다른 페이지로 이동하는
              // NAVIGATE는 아직 생성되지 않아 처리하지 않음 — 라이브 프리뷰 PreviewPageRenderer.tsx와 동일한
              // 알려진 범위).
              const createFlow = create
                ? FLOWS.find((flow) => flow.trigger?.pageId === page.id && flow.trigger?.actionId === create.id)
                : undefined;

              async function runCreateFlow(flow: FlowBlueprint, formValues: Record<string, string>) {
                const ctx = createFlowContext({ form: formValues });
                await executeFlow(flow, BINDINGS, ctx, {
                  callBinding: createCapabilityBindingCaller(authToken),
                  navigate: (_pageId, parameters) => {
                    const selected = parameters.selected != null ? String(parameters.selected) : null;
                    selectRow(selected ? { id: selected } : null);
                  },
                });
                refresh();
              }

              async function runCommandFlow(capability: Capability, targetId: string) {
                const flow = FLOWS.find((candidate) =>
                  candidate.trigger?.pageId === page.id && candidate.trigger?.actionId === capability.id
                );
                if (!flow) {
                  await callCapability(capability, authToken, { pathParams: { id: targetId } });
                  return;
                }
                const ctx = createFlowContext({ route: { selected: targetId } });
                await executeFlow(flow, BINDINGS, ctx, {
                  callBinding: createCapabilityBindingCaller(authToken),
                  navigate: (_pageId, parameters) => {
                    const selected = parameters.selected != null ? String(parameters.selected) : null;
                    if (selected) {
                      selectRow({ id: selected });
                    }
                  },
                  onRefreshBindingError: (bindingId, error) => {
                    console.warn(`command 후 refresh binding 실패: ${bindingId}`, error);
                  },
                });
              }

              function renderChildResources(parentId: string) {
                if (!parentId || childBlocks.length === 0) return null;
                return childBlocks.map((block) => {
                  const childCapabilities = block.capabilityIds
                    .map((id) => findCapabilityById(id))
                    .filter((capability): capability is Capability => capability !== undefined);
                  const childList = childCapabilities.find((capability) => capability.type === "LIST");
                  const childCreate = childCapabilities.find((capability) => capability.type === "CREATE");
                  const childDelete = childCapabilities.find((capability) => capability.type === "DELETE");
                  if (!childList) return null;
                  return (
                    <ChildResourceList
                      key={block.instanceId}
                      listCapability={childList}
                      createCapability={childCreate}
                      deleteCapability={childDelete}
                      authToken={authToken}
                      parentId={parentId}
                      refreshKey={refreshKey}
                    />
                  );
                });
              }

              // 두 레이아웃(side-detail-panel/full-detail-page)이 공유하는 수정/삭제 버튼.
              function renderHeaderActions(row: Record<string, unknown>) {
                return (
                  <div style={{ display: "flex", gap: 12 }}>
                    {update && (
                      <button className="plain" onClick={() => setOverlay({ kind: "UPDATE", row })}>
                        수정
                      </button>
                    )}
                    {del && (
                      <button className="danger" onClick={() => setOverlay({ kind: "DELETE", id: rowId(row) })}>
                        삭제
                      </button>
                    )}
                  </div>
                );
              }

              return (
                <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
                  {selectedRow && detail && isFullDetailPage ? (
                    <div className="panel">
                      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 12 }}>
                        <button className="plain" onClick={() => selectRow(null)}>
                          ← 목록으로
                        </button>
                        {renderHeaderActions(selectedRow)}
                      </div>
                      {commandCapabilities.length > 0 && (
                        <div style={{ marginBottom: 12 }}>
                          <QuickActionButtonGroup
                            capabilities={commandCapabilities}
                            authToken={authToken}
                            targetId={rowId(selectedRow)}
                            onSuccess={refresh}
                            onExecute={(capability) => runCommandFlow(capability, rowId(selectedRow))}
                          />
                        </div>
                      )}
                      <FullDetailPage capability={detail} authToken={authToken} id={rowId(selectedRow)} refreshKey={refreshKey} />
                      {renderChildResources(rowId(selectedRow))}
                    </div>
                  ) : (
                    <div className="grid-2">
                      {listBlock?.componentId === "resource-card-grid" ? (
                        <ResourceCardGrid
                          capability={list}
                          authToken={authToken}
                          refreshKey={refreshKey}
                          onRowClick={detail || update || del || commandBlock ? (row) => selectRow(row) : undefined}
                          onCreateClick={create ? () => setOverlay({ kind: "CREATE" }) : undefined}
                        />
                      ) : (
                        <ResourceTable
                          capability={list}
                          authToken={authToken}
                          refreshKey={refreshKey}
                          onRowClick={detail || update || del || commandBlock ? (row) => selectRow(row) : undefined}
                          onCreateClick={create ? () => setOverlay({ kind: "CREATE" }) : undefined}
                        />
                      )}
                      {selectedRow && (detail || commandCapabilities.length > 0) && (
                        <div className="panel">
                          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 12 }}>
                            <strong>상세</strong>
                            {renderHeaderActions(selectedRow)}
                          </div>
                          {commandCapabilities.length > 0 && (
                            <div style={{ marginBottom: 12 }}>
                              <QuickActionButtonGroup
                                capabilities={commandCapabilities}
                                authToken={authToken}
                                targetId={rowId(selectedRow)}
                                onSuccess={refresh}
                                onExecute={(capability) => runCommandFlow(capability, rowId(selectedRow))}
                              />
                            </div>
                          )}
                          {detail && <DetailPanel capability={detail} authToken={authToken} id={rowId(selectedRow)} refreshKey={refreshKey} />}
                          {renderChildResources(rowId(selectedRow))}
                        </div>
                      )}
                    </div>
                  )}
                  {create && overlay.kind === "CREATE" && (
                    createBlock?.componentId === "form-drawer" ? (
                      <FormDrawer
                        capability={create}
                        authToken={authToken}
                        onClose={() => setOverlay({ kind: "NONE" })}
                        onSuccess={refresh}
                        onSubmitOverride={createFlow ? (values) => runCreateFlow(createFlow, values) : undefined}
                      />
                    ) : (
                      <CreateEditModal
                        capability={create}
                        authToken={authToken}
                        onClose={() => setOverlay({ kind: "NONE" })}
                        onSuccess={refresh}
                        onSubmitOverride={createFlow ? (values) => runCreateFlow(createFlow, values) : undefined}
                      />
                    )
                  )}
                  {update && overlay.kind === "UPDATE" && (
                    updateBlock?.componentId === "form-drawer" ? (
                      <FormDrawer
                        capability={update}
                        authToken={authToken}
                        initialValues={overlay.row}
                        onClose={() => setOverlay({ kind: "NONE" })}
                        onSuccess={refresh}
                      />
                    ) : (
                      <CreateEditModal
                        capability={update}
                        authToken={authToken}
                        initialValues={overlay.row}
                        onClose={() => setOverlay({ kind: "NONE" })}
                        onSuccess={refresh}
                      />
                    )
                  )}
                  {del && overlay.kind === "DELETE" && deleteBlock?.componentId === "typed-confirm-modal" && (
                    <TypedConfirmModal
                      capability={del}
                      authToken={authToken}
                      targetId={overlay.id}
                      onClose={() => setOverlay({ kind: "NONE" })}
                      onSuccess={() => {
                        selectRow(null);
                        setOverlay({ kind: "NONE" });
                        refresh();
                      }}
                    />
                  )}
                  {del && overlay.kind === "DELETE" && deleteBlock?.componentId !== "typed-confirm-modal" && (
                    <DeleteConfirmModal
                      capability={del}
                      authToken={authToken}
                      targetId={overlay.id}
                      onClose={() => setOverlay({ kind: "NONE" })}
                      onSuccess={() => {
                        selectRow(null);
                        setOverlay({ kind: "NONE" });
                        refresh();
                      }}
                    />
                  )}
                </div>
              );
            }

            function ApiCallLog({ entries }: { entries: ApiCallLogEntry[] }) {
              if (entries.length === 0) {
                return <p className="muted">아직 API 호출이 없습니다. 위에서 화면을 조작해보세요.</p>;
              }
              return (
                <div>
                  {entries.map((entry) => (
                    <details key={entry.id} className="log-entry">
                      <summary>
                        <span className={`log-status ${entry.error ? "error" : ""}`}>{entry.status ?? "실패"}</span>
                        <strong>{entry.method}</strong>
                        <span className="log-url">{entry.url}</span>
                        <span className="muted" style={{ marginLeft: "auto" }}>
                          {new Date(entry.timestamp).toLocaleTimeString()}
                        </span>
                      </summary>
                      <div className="log-body">
                        {entry.error && <p className="error">{entry.error}</p>}
                        {entry.requestBody != null && (
                          <>
                            <p className="muted">요청 본문</p>
                            <pre>{JSON.stringify(entry.requestBody, null, 2)}</pre>
                          </>
                        )}
                        <p className="muted">응답</p>
                        <pre>{entry.responseBody != null ? JSON.stringify(entry.responseBody, null, 2) : "(본문 없음)"}</pre>
                      </div>
                    </details>
                  ))}
                </div>
              );
            }

            function ProductShell({
              pages, activePage, onSelectPage, children,
            }: {
              pages: PageDraft[];
              activePage: PageDraft;
              onSelectPage: (pageId: string) => void;
              children: ReactNode;
            }) {
              const totalCapabilities = new Set(pages.flatMap((page) => page.capabilityIds)).size;

              if (PURPOSE === "ADMIN") {
                return (
                  <div className="shell shell-admin">
                    <aside className="shell-sidebar">
                      <div style={{ padding: "4px 10px 14px" }}>
                        <strong style={{ color: "#22c55e", fontSize: 11, letterSpacing: 1.4 }}>ADMIN CONSOLE</strong>
                        <p className="muted" style={{ margin: "4px 0 0" }}>운영 및 리소스 관리</p>
                      </div>
                      {pages.map((page) => (
                        <button key={page.id} className={activePage.id === page.id ? "active" : ""} onClick={() => onSelectPage(page.id)}>
                          <strong style={{ display: "block", fontSize: 12 }}>{page.title}</strong>
                          <span style={{ fontSize: 10 }}>{page.capabilityIds.length} capabilities</span>
                        </button>
                      ))}
                    </aside>
                    <main className="shell-main">
                      <div className="shell-toolbar">
                        <div>
                          <span className="muted" style={{ textTransform: "uppercase", letterSpacing: 1.2 }}>Administration</span>
                          <h2 style={{ margin: "4px 0 0", fontSize: 18 }}>{activePage.title}</h2>
                        </div>
                        <span className="shell-badge">{activePage.capabilityIds.length} operations · ADMIN</span>
                      </div>
                      {children}
                    </main>
                  </div>
                );
              }

              if (PURPOSE === "PRODUCT_LIKE") {
                return (
                  <div className="shell">
                    <header className="shell-header">
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "end", gap: 12 }}>
                        <div className="shell-brand">
                          <div className="shell-logo">G</div>
                          <div>
                            <strong>Service Preview</strong>
                            <p className="muted" style={{ margin: "2px 0 0" }}>실제 사용자 흐름을 위한 동작형 프론트</p>
                          </div>
                        </div>
                        <span className="shell-badge">{pages.length} pages · {totalCapabilities} capabilities</span>
                      </div>
                      <nav className="shell-product-nav">
                        {pages.map((page) => (
                          <button key={page.id} className={activePage.id === page.id ? "active" : ""} onClick={() => onSelectPage(page.id)}>
                            {page.title}
                          </button>
                        ))}
                      </nav>
                    </header>
                    <main className="shell-main">
                      <span style={{ color: "#22c55e", fontSize: 10, fontWeight: 800, letterSpacing: 1.2 }}>CURRENT PAGE</span>
                      <h2 style={{ margin: "5px 0 16px", fontSize: 22 }}>{activePage.title}</h2>
                      {children}
                    </main>
                  </div>
                );
              }

              return (
                <div className="shell">
                  <div className="shell-api-head">
                    <div>
                      <strong style={{ color: "#22c55e", fontFamily: "ui-monospace, monospace", fontSize: 12 }}>API Test Console</strong>
                      <p className="muted" style={{ margin: "3px 0 0" }}>OpenAPI operations · request/response inspection</p>
                    </div>
                    <span className="shell-badge">PAGES {pages.length} · OPS {totalCapabilities}</span>
                  </div>
                  <nav className="shell-api-nav">
                    {pages.map((page) => (
                      <button key={page.id} className={activePage.id === page.id ? "active" : ""} onClick={() => onSelectPage(page.id)}>
                        /{page.id}
                      </button>
                    ))}
                  </nav>
                  <main className="shell-main">
                    <div className="muted" style={{ display: "flex", justifyContent: "space-between", fontFamily: "ui-monospace, monospace", marginBottom: 12 }}>
                      <span>PAGE: {activePage.title}</span>
                      <span>{activePage.capabilityIds.length} operations</span>
                    </div>
                    {children}
                  </main>
                </div>
              );
            }

            export default function App() {
              const [authToken, setAuthToken] = useState<string | null>(null);
              const [activePageId, setActivePageId] = useQueryParam("page");
              const activePage = PAGES.find((p) => p.id === activePageId) ?? PAGES[0];
              const [apiLog, setApiLog] = useState<ApiCallLogEntry[]>([]);

              useEffect(() => {
                apiCallListener = (entry) => setApiLog((prev) => [entry, ...prev].slice(0, 30));
                return () => {
                  apiCallListener = null;
                };
              }, []);

              return (
                <div className="container">
                  <h1>GamjaBox Auto Preview</h1>
                  <p className="muted">이 화면은 OpenAPI 문서를 분석해 자동 생성되었습니다.</p>
                  {authToken && (
                    <p className="muted" style={{ color: "#22c55e" }}>
                      로그인됨
                    </p>
                  )}
                  <ProductShell pages={PAGES} activePage={activePage} onSelectPage={setActivePageId}>
                    <PageRenderer page={activePage} authToken={authToken} onLogin={setAuthToken} />
                  </ProductShell>
                  <div className="panel" style={{ marginTop: 16 }}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                      <strong style={{ fontSize: 13 }}>요청·응답 확인</strong>
                      {apiLog.length > 0 && (
                        <button className="plain" onClick={() => setApiLog([])}>
                          기록 지우기
                        </button>
                      )}
                    </div>
                    <ApiCallLog entries={apiLog} />
                  </div>
                </div>
              );
            }
            """;

    private static String appTsxTemplate() {
        return new StringBuilder(APP_TSX_TEMPLATE_PART_1.length() + APP_TSX_TEMPLATE_PART_2.length())
                .append(APP_TSX_TEMPLATE_PART_1)
                .append(APP_TSX_TEMPLATE_PART_2)
                .toString();
    }
}
