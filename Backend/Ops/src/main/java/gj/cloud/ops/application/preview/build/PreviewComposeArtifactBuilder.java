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
import gj.cloud.ops.application.preview.blueprint.BlueprintCompiler;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
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

    public ComposeArtifact build(
            String apiBaseUrl, List<Capability> capabilities, List<PageDraft> pages, AuthStrategy authStrategy,
            Purpose purpose
    ) {
        String appTsx = renderAppTsx(apiBaseUrl, capabilities, pages, authStrategy, purpose);

        List<UploadedFile> uploadedFiles = List.of(
                file("package.json", PACKAGE_JSON),
                file("tsconfig.json", TSCONFIG_JSON),
                file("vite.config.ts", VITE_CONFIG_TS),
                file("index.html", INDEX_HTML),
                file("Dockerfile", DOCKERFILE),
                file("src/main.tsx", MAIN_TSX),
                file("src/index.css", INDEX_CSS),
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
            String apiBaseUrl, List<Capability> capabilities, List<PageDraft> pages, AuthStrategy authStrategy,
            Purpose purpose
    ) {
        Map<String, List<Block>> pageBlocks = BlueprintCompiler.compile(blockResolver.resolveAll(pages, capabilities), purpose);
        return APP_TSX_TEMPLATE
                .replace("__API_BASE_URL_JSON__", toJson(apiBaseUrl))
                .replace("__CAPABILITIES_JSON__", toJson(capabilities))
                .replace("__PAGES_JSON__", toJson(pages))
                .replace("__AUTH_STRATEGY_JSON__", toJson(authStrategy))
                .replace("__PAGE_BLOCKS_JSON__", toJson(pageBlocks));
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
                "target": "ES2020",
                "useDefineForClassFields": true,
                "lib": ["ES2020", "DOM", "DOM.Iterable"],
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

    private static final String INDEX_CSS = """
            :root {
              font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
              background: #0b0d0f;
              color: #e5e7eb;
            }
            * { box-sizing: border-box; }
            body { margin: 0; }
            .container { max-width: 960px; margin: 0 auto; padding: 32px; }
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
            .grid-2 { display: grid; grid-template-columns: 1fr 320px; gap: 16px; }
            @media (max-width: 720px) { .grid-2 { grid-template-columns: 1fr; } }
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
    private static final String APP_TSX_TEMPLATE = """
            import { useEffect, useState, type FormEvent } from "react";

            type CapabilityType = "LIST" | "DETAIL" | "CREATE" | "UPDATE" | "DELETE" | "LOGIN";
            type PageSkeletonType = "AUTH_PAGE" | "RESOURCE_LIST" | "LIST_DETAIL" | "DASHBOARD";
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
              // Direction Recovery Change Request §7.1 — 이번 증분에서는 렌더링에 아직 쓰이지 않는다
              // (Variant Registry/Compiler가 생기는 다음 증분에서 COMMAND capability를 실제로 그린다).
              kind: CapabilityKind;
              action: string | null;
              dependencies: string[];
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
              | "delete-confirm-modal" | "dashboard-view";
            interface Block {
              instanceId: string;
              componentId: ComponentId;
              slot: string;
              capabilityIds: string[];
              mode: "CREATE" | "UPDATE" | null;
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
            // 페이지 ID별 Block 목록 — PreviewBlockResolver가 서버에서 미리 계산해 심어둔다.
            const PAGE_BLOCKS: Record<string, Block[]> = __PAGE_BLOCKS_JSON__;

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
              if (pathParams.id !== undefined) {
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
                              <td key={column}>{formatCellValue(row[column])}</td>
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
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 12 }}>
                      {rows.map((row, index) => {
                        const entries = Object.entries(row);
                        const [firstEntry, ...restEntries] = entries;
                        return (
                          <div
                            key={index}
                            onClick={() => onRowClick?.(row)}
                            className="panel"
                            style={{ cursor: onRowClick ? "pointer" : undefined }}
                          >
                            {firstEntry && (
                              <p style={{ fontWeight: 700, marginBottom: 8, overflow: "hidden", textOverflow: "ellipsis" }}>
                                {formatCellValue(firstEntry[1])}
                              </p>
                            )}
                            {restEntries.slice(0, 4).map(([key, value]) => (
                              <div key={key} style={{ display: "flex", justifyContent: "space-between", gap: 8, fontSize: 12 }}>
                                <span className="muted">{key}</span>
                                <span style={{ overflow: "hidden", textOverflow: "ellipsis" }}>{formatCellValue(value)}</span>
                              </div>
                            ))}
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            }

            function DetailPanel({ capability, authToken, id }: { capability: Capability; authToken: string | null; id: string }) {
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
                    if (!cancelled) setData(result as Record<string, unknown>);
                  } catch (err) {
                    if (!cancelled) setError(err instanceof Error ? err.message : "상세 정보를 불러오지 못했습니다");
                  } finally {
                    if (!cancelled) setLoading(false);
                  }
                });
                return () => {
                  cancelled = true;
                };
              }, [capability, authToken, id]);

              if (loading) {
                return <p className="muted">불러오는 중...</p>;
              }
              if (error) {
                return <p className="error">{error}</p>;
              }
              if (!data) {
                return null;
              }

              return (
                <div>
                  {Object.entries(data).map(([key, value]) => (
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

            function CreateEditModal({
              capability, authToken, initialValues, onClose, onSuccess,
            }: {
              capability: Capability;
              authToken: string | null;
              initialValues?: Record<string, unknown>;
              onClose: () => void;
              onSuccess: () => void;
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
                  const pathParams: Record<string, string> = initialValues ? { id: rowId(initialValues) } : {};
                  await callCapability(capability, authToken, { body: values, pathParams });
                  onSuccess();
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

            interface DashboardCountState {
              loading: boolean;
              value: number | null;
              error: string | null;
            }

            // Dashboard 스켈레톤 — 어떤 리소스가 중요한지 판단하지 않고, page.capabilityIds에 모인
            // LIST capability마다 개수 카드만 기계적으로 보여준다.
            function DashboardView({ capabilities, authToken }: { capabilities: Capability[]; authToken: string | null }) {
              const [counts, setCounts] = useState<Record<string, DashboardCountState>>({});

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
              }, [capabilities, authToken]);

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

            function PageRenderer({ page, authToken, onLogin }: { page: PageDraft; authToken: string | null; onLogin: (token: string) => void }) {
              const [selectedRow, setSelectedRow] = useState<Record<string, unknown> | null>(null);
              const [overlay, setOverlay] = useState<OverlayState>({ kind: "NONE" });
              const [refreshKey, setRefreshKey] = useState(0);

              const blocks = PAGE_BLOCKS[page.id] ?? [];

              if (page.skeleton === "AUTH_PAGE") {
                const login = findCapabilityForBlock(blocks, "login-form");
                if (!login) {
                  return <p className="error">이 페이지에 로그인 capability가 없습니다.</p>;
                }
                return <LoginForm capability={login} onLogin={onLogin} />;
              }

              if (page.skeleton === "DASHBOARD") {
                const dashboardBlock = blocks.find((b) => b.componentId === "dashboard-view");
                const listCapabilities = (dashboardBlock?.capabilityIds ?? [])
                  .map((id) => findCapabilityById(id))
                  .filter((c): c is Capability => c !== undefined);
                return <DashboardView capabilities={listCapabilities} authToken={authToken} />;
              }

              const listBlock = findListBlock(blocks);
              const list = listBlock ? findCapabilityById(listBlock.capabilityIds[0]) : undefined;
              const detail = findCapabilityForBlock(blocks, "detail-panel");
              const create = findCapabilityForBlock(blocks, "create-edit-modal", "CREATE");
              const update = findCapabilityForBlock(blocks, "create-edit-modal", "UPDATE");
              const del = findCapabilityForBlock(blocks, "delete-confirm-modal");

              if (!list) {
                return <p className="error">이 페이지에 목록 capability가 없습니다.</p>;
              }

              function refresh() {
                setRefreshKey((key) => key + 1);
              }

              return (
                <div className="grid-2">
                  {listBlock?.componentId === "resource-card-grid" ? (
                    <ResourceCardGrid
                      capability={list}
                      authToken={authToken}
                      refreshKey={refreshKey}
                      onRowClick={detail || update || del ? (row) => setSelectedRow(row) : undefined}
                      onCreateClick={create ? () => setOverlay({ kind: "CREATE" }) : undefined}
                    />
                  ) : (
                    <ResourceTable
                      capability={list}
                      authToken={authToken}
                      refreshKey={refreshKey}
                      onRowClick={detail || update || del ? (row) => setSelectedRow(row) : undefined}
                      onCreateClick={create ? () => setOverlay({ kind: "CREATE" }) : undefined}
                    />
                  )}
                  {selectedRow && detail && (
                    <div className="panel">
                      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 12 }}>
                        <strong>상세</strong>
                        <div style={{ display: "flex", gap: 12 }}>
                          {update && (
                            <button className="plain" onClick={() => setOverlay({ kind: "UPDATE", row: selectedRow })}>
                              수정
                            </button>
                          )}
                          {del && (
                            <button className="danger" onClick={() => setOverlay({ kind: "DELETE", id: rowId(selectedRow) })}>
                              삭제
                            </button>
                          )}
                        </div>
                      </div>
                      <DetailPanel capability={detail} authToken={authToken} id={rowId(selectedRow)} />
                    </div>
                  )}
                  {create && overlay.kind === "CREATE" && (
                    <CreateEditModal capability={create} authToken={authToken} onClose={() => setOverlay({ kind: "NONE" })} onSuccess={refresh} />
                  )}
                  {update && overlay.kind === "UPDATE" && (
                    <CreateEditModal
                      capability={update}
                      authToken={authToken}
                      initialValues={overlay.row}
                      onClose={() => setOverlay({ kind: "NONE" })}
                      onSuccess={refresh}
                    />
                  )}
                  {del && overlay.kind === "DELETE" && (
                    <DeleteConfirmModal
                      capability={del}
                      authToken={authToken}
                      targetId={overlay.id}
                      onClose={() => setOverlay({ kind: "NONE" })}
                      onSuccess={() => {
                        setSelectedRow(null);
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

            export default function App() {
              const [authToken, setAuthToken] = useState<string | null>(null);
              const [activePage, setActivePage] = useState<PageDraft>(PAGES[0]);
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
                  <div className="tabs">
                    {PAGES.map((page) => (
                      <button
                        key={page.id}
                        className={`tab ${activePage.id === page.id ? "active" : ""}`}
                        onClick={() => setActivePage(page)}
                      >
                        {page.title}
                      </button>
                    ))}
                  </div>
                  {authToken && (
                    <p className="muted" style={{ color: "#22c55e" }}>
                      로그인됨
                    </p>
                  )}
                  <div className="panel">
                    <PageRenderer page={activePage} authToken={authToken} onLogin={setAuthToken} />
                  </div>
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
}
