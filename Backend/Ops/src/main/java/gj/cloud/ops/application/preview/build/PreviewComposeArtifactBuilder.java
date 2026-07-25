package gj.cloud.ops.application.preview.build;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import gj.cloud.ops.application.deployment.dto.ExposedRoute;
import gj.cloud.ops.application.deployment.dto.HealthCheck;
import gj.cloud.ops.application.deployment.dto.UploadedFile;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
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

    public ComposeArtifact build(String apiBaseUrl, List<Capability> capabilities, List<PageDraft> pages) {
        String appTsx = renderAppTsx(apiBaseUrl, capabilities, pages);

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

    private String renderAppTsx(String apiBaseUrl, List<Capability> capabilities, List<PageDraft> pages) {
        return APP_TSX_TEMPLATE
                .replace("__API_BASE_URL_JSON__", toJson(apiBaseUrl))
                .replace("__CAPABILITIES_JSON__", toJson(capabilities))
                .replace("__PAGES_JSON__", toJson(pages));
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
            """;

    // GamjaBox_2.0_Key_Features.md 3·7절 — 관련 API를 페이지 하나로 묶어서 보여준다. Blueprint
    // Schema/Registry/Slot 시스템 없이 5개 고정 패턴만 조립하는 Phase C 렌더러를 순수 React(플레인
    // CSS, 포털 전용 컴포넌트/Tailwind 의존 없음)로 그대로 이식한 것 — 동작은 Phase C에서 브라우저로
    // 이미 검증됨.
    private static final String APP_TSX_TEMPLATE = """
            import { useEffect, useState, type FormEvent } from "react";

            type CapabilityType = "LIST" | "DETAIL" | "CREATE" | "UPDATE" | "DELETE" | "LOGIN";
            type PageSkeletonType = "AUTH_PAGE" | "RESOURCE_LIST" | "LIST_DETAIL" | "DASHBOARD";

            interface Capability {
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

            interface PageDraft {
              id: string;
              title: string;
              skeleton: PageSkeletonType;
              capabilityIds: string[];
            }

            const API_BASE_URL: string = __API_BASE_URL_JSON__;
            const CAPABILITIES: Capability[] = __CAPABILITIES_JSON__;
            const PAGES: PageDraft[] = __PAGES_JSON__;

            function buildUrl(capability: Capability, pathParams: Record<string, string> = {}, query: Record<string, string> = {}): string {
              let path = capability.path;
              for (const [key, value] of Object.entries(pathParams)) {
                path = path.replace(`{${key}}`, encodeURIComponent(value));
              }
              const url = new URL(API_BASE_URL.replace(/\\/$/, "") + path);
              for (const [key, value] of Object.entries(query)) {
                if (value) url.searchParams.set(key, value);
              }
              return url.toString();
            }

            async function callCapability(
              capability: Capability,
              authToken: string | null,
              options: { pathParams?: Record<string, string>; query?: Record<string, string>; body?: Record<string, unknown> } = {}
            ): Promise<unknown> {
              const url = buildUrl(capability, options.pathParams, options.query);
              const res = await fetch(url, {
                method: capability.method,
                headers: {
                  ...(options.body ? { "Content-Type": "application/json" } : {}),
                  ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
                },
                body: options.body ? JSON.stringify(options.body) : undefined,
              });
              if (!res.ok) {
                throw new Error(`${capability.method} ${url} 요청이 실패했습니다 (${res.status})`);
              }
              if (res.status === 204) {
                return null;
              }
              const text = await res.text();
              return text ? JSON.parse(text) : null;
            }

            function isPasswordLikeField(name: string): boolean {
              const lower = name.toLowerCase();
              return lower.includes("password") || lower === "pw" || lower === "pwd";
            }

            function extractArray(result: unknown): Record<string, unknown>[] {
              if (Array.isArray(result)) {
                return result as Record<string, unknown>[];
              }
              if (result && typeof result === "object") {
                const obj = result as Record<string, unknown>;
                for (const key of ["content", "items", "data", "list", "results"]) {
                  if (Array.isArray(obj[key])) {
                    return obj[key] as Record<string, unknown>[];
                  }
                }
              }
              return [];
            }

            function extractToken(result: unknown): string | null {
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

            function findCapabilityByType(page: PageDraft, type: CapabilityType): Capability | undefined {
              for (const id of page.capabilityIds) {
                const capability = findCapabilityById(id);
                if (capability && capability.type === type) {
                  return capability;
                }
              }
              return undefined;
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
                  const token = extractToken(result);
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
                    query.search = search;
                  }
                  try {
                    const result = await callCapability(capability, authToken, { query });
                    if (!cancelled) setRows(extractArray(result));
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

            function PageRenderer({ page, authToken, onLogin }: { page: PageDraft; authToken: string | null; onLogin: (token: string) => void }) {
              const [selectedRow, setSelectedRow] = useState<Record<string, unknown> | null>(null);
              const [showCreate, setShowCreate] = useState(false);
              const [editTarget, setEditTarget] = useState<Record<string, unknown> | null>(null);
              const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null);
              const [refreshKey, setRefreshKey] = useState(0);

              if (page.skeleton === "AUTH_PAGE") {
                const login = findCapabilityByType(page, "LOGIN");
                if (!login) {
                  return <p className="error">이 페이지에 로그인 capability가 없습니다.</p>;
                }
                return <LoginForm capability={login} onLogin={onLogin} />;
              }

              const list = findCapabilityByType(page, "LIST");
              const detail = findCapabilityByType(page, "DETAIL");
              const create = findCapabilityByType(page, "CREATE");
              const update = findCapabilityByType(page, "UPDATE");
              const del = findCapabilityByType(page, "DELETE");

              if (!list) {
                return <p className="error">이 페이지에 목록 capability가 없습니다.</p>;
              }

              function refresh() {
                setRefreshKey((key) => key + 1);
              }

              return (
                <div className="grid-2">
                  <ResourceTable
                    capability={list}
                    authToken={authToken}
                    refreshKey={refreshKey}
                    onRowClick={detail || update || del ? (row) => setSelectedRow(row) : undefined}
                    onCreateClick={create ? () => setShowCreate(true) : undefined}
                  />
                  {selectedRow && detail && (
                    <div className="panel">
                      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 12 }}>
                        <strong>상세</strong>
                        <div style={{ display: "flex", gap: 12 }}>
                          {update && (
                            <button className="plain" onClick={() => setEditTarget(selectedRow)}>
                              수정
                            </button>
                          )}
                          {del && (
                            <button className="danger" onClick={() => setDeleteTargetId(rowId(selectedRow))}>
                              삭제
                            </button>
                          )}
                        </div>
                      </div>
                      <DetailPanel capability={detail} authToken={authToken} id={rowId(selectedRow)} />
                    </div>
                  )}
                  {create && showCreate && (
                    <CreateEditModal capability={create} authToken={authToken} onClose={() => setShowCreate(false)} onSuccess={refresh} />
                  )}
                  {update && editTarget && (
                    <CreateEditModal
                      capability={update}
                      authToken={authToken}
                      initialValues={editTarget}
                      onClose={() => setEditTarget(null)}
                      onSuccess={refresh}
                    />
                  )}
                  {del && deleteTargetId && (
                    <DeleteConfirmModal
                      capability={del}
                      authToken={authToken}
                      targetId={deleteTargetId}
                      onClose={() => setDeleteTargetId(null)}
                      onSuccess={() => {
                        setSelectedRow(null);
                        refresh();
                      }}
                    />
                  )}
                </div>
              );
            }

            export default function App() {
              const [authToken, setAuthToken] = useState<string | null>(null);
              const [activePage, setActivePage] = useState<PageDraft>(PAGES[0]);

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
                </div>
              );
            }
            """;
}
