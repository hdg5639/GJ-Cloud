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
import gj.cloud.ops.application.preview.blueprint.BlueprintPartSelector;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenario;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.PreviewMode;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    // App.tsx에 컴포넌트를 인라인하지 않고, 포털 preview-runtime '실물' 소스를
    // 그대로 번들해 렌더한다. build.gradle의 syncPreviewTemplate이 classpath(preview-template/src/**)에
    // 실물 파일을 baked해두고, 여기서 그걸 읽어 생성 프로젝트로 emit한다. App.tsx는 JSON을 PreviewRuntimeApp
    // (공유 호스트)에 넘기는 얇은 진입점뿐이다. 이로써 배포 앱과 포털이 같은 컴포넌트를 쓴다(이중 유지보수 제거).
    // Blueprint 파츠도 이 경로에선 라이브 프리뷰와 동일하게 선택기를 적용해 실제로 렌더된다.
    public ComposeArtifact build(
            String apiBaseUrl, List<Capability> capabilities, List<PageDraft> pages, List<FlowBlueprint> flows,
            List<ApiBinding> bindings, AuthStrategy authStrategy, Purpose purpose,
            List<CompiledScenario> scenarios, PreviewMode previewMode, Map<String, String> partOverrides
    ) {
        Map<String, List<Block>> pageBlocks = BlueprintPartSelector.select(
                BlueprintCompiler.compile(blockResolver.resolveAll(pages, capabilities), purpose),
                capabilities, purpose, partOverrides);

        List<UploadedFile> uploadedFiles = new ArrayList<>();
        uploadedFiles.add(file("package.json", PACKAGE_JSON));
        uploadedFiles.add(file("tsconfig.json", TSCONFIG_JSON));
        uploadedFiles.add(file("vite.config.ts", VITE_CONFIG_TS));
        uploadedFiles.add(file("index.html", INDEX_HTML));
        uploadedFiles.add(file("Dockerfile", DOCKERFILE));
        uploadedFiles.add(file("src/main.tsx", MAIN_TSX));
        uploadedFiles.add(file("src/index.css", INDEX_CSS));
        uploadedFiles.add(file("src/App.tsx", renderAppTsx(
                apiBaseUrl, capabilities, pages, pageBlocks, flows, bindings, authStrategy, purpose,
                scenarios, previewMode)));
        // 포털 preview-runtime + ui 프리미티브 + lib/types 실물(build.gradle이 baked).
        uploadedFiles.addAll(readPreviewTemplateFiles());

        String nickname = "preview-" + UUID.randomUUID().toString().substring(0, 8);
        ExposedRoute route = new ExposedRoute("web", CONTAINER_PORT, "HTTP", "PUBLIC", nickname, null);
        HealthCheck healthCheck = new HealthCheck("web", "/", CONTAINER_PORT, CONTAINER_PORT);
        return new ComposeArtifact(
                COMPOSE_CONTENT, List.of(), uploadedFiles, List.of(route), List.of(healthCheck),
                SourceType.AUTO_PREVIEW);
    }

    // classpath의 preview-template/src/** 전부를 읽어 생성 프로젝트의 src/... 로 emit한다.
    // exploded(테스트/bootRun)와 jar(배포) 양쪽에서 동작하도록 PathMatchingResourcePatternResolver 사용.
    private List<UploadedFile> readPreviewTemplateFiles() {
        String marker = "preview-template/";
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:preview-template/src/**");
            List<UploadedFile> files = new ArrayList<>();
            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                String uri = resource.getURI().toString();
                int idx = uri.indexOf(marker);
                if (idx < 0) {
                    continue;
                }
                String relPath = uri.substring(idx + marker.length()); // "src/..."
                if (relPath.isEmpty() || relPath.endsWith("/")) {
                    continue;
                }
                try (var in = resource.getInputStream()) {
                    files.add(new UploadedFile(relPath, in.readAllBytes()));
                }
            }
            if (files.isEmpty()) {
                throw new IllegalStateException("preview-template 리소스를 찾지 못했습니다 — build.gradle의 "
                        + "syncPreviewTemplate이 실행됐는지 확인 필요");
            }
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException("preview-template 리소스 읽기 실패", e);
        }
    }

    private String renderAppTsx(
            String apiBaseUrl, List<Capability> capabilities, List<PageDraft> pages,
            Map<String, List<Block>> pageBlocks, List<FlowBlueprint> flows, List<ApiBinding> bindings,
            AuthStrategy authStrategy, Purpose purpose, List<CompiledScenario> scenarios, PreviewMode previewMode
    ) {
        return APP_TSX
                .replace("__API_BASE_URL_JSON__", toJson(apiBaseUrl))
                .replace("__CAPABILITIES_JSON__", toJson(capabilities))
                .replace("__PAGES_JSON__", toJson(pages))
                .replace("__PAGE_BLOCKS_JSON__", toJson(pageBlocks))
                .replace("__FLOWS_JSON__", toJson(flows))
                .replace("__BINDINGS_JSON__", toJson(bindings))
                .replace("__AUTH_STRATEGY_JSON__", toJson(authStrategy))
                .replace("__PURPOSE_JSON__", toJson(purpose != null ? purpose.name() : null))
                .replace("__SCENARIOS_JSON__", toJson(scenarios == null ? List.of() : scenarios))
                .replace("__PREVIEW_MODE_JSON__", toJson(previewMode == null
                        ? PreviewMode.OPERATION_PREVIEW : previewMode));
    }

    private UploadedFile file(String vmPath, String content) {
        return new UploadedFile(vmPath, content.getBytes(StandardCharsets.UTF_8));
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

    // ── 전면 이전(Phase B) 전용 생성 config: 포털 실물 컴포넌트를 Tailwind v4 + @/ alias로 번들 ──
    private static final String PACKAGE_JSON = """
            {
              "name": "gamjabox-auto-preview",
              "private": true,
              "version": "0.0.1",
              "type": "module",
              "scripts": {
                "dev": "vite",
                "build": "vite build",
                "preview": "vite preview"
              },
              "dependencies": {
                "react": "^19.0.0",
                "react-dom": "^19.0.0"
              },
              "devDependencies": {
                "@types/react": "^19.0.0",
                "@types/react-dom": "^19.0.0",
                "@vitejs/plugin-react": "^4.3.4",
                "@tailwindcss/vite": "^4.0.0",
                "tailwindcss": "^4.0.0",
                "typescript": "^5.5.3",
                "vite": "^5.4.1"
              }
            }
            """;

    private static final String VITE_CONFIG_TS = """
            import { defineConfig } from "vite";
            import react from "@vitejs/plugin-react";
            import tailwindcss from "@tailwindcss/vite";
            import { fileURLToPath, URL } from "node:url";

            export default defineConfig({
              plugins: [react(), tailwindcss()],
              resolve: {
                alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
              },
            });
            """;

    private static final String TSCONFIG_JSON = """
            {
              "compilerOptions": {
                "target": "ES2021",
                "useDefineForClassFields": true,
                "lib": ["ESNext", "DOM", "DOM.Iterable"],
                "module": "ESNext",
                "skipLibCheck": true,
                "moduleResolution": "bundler",
                "allowImportingTsExtensions": true,
                "resolveJsonModule": true,
                "isolatedModules": true,
                "noEmit": true,
                "jsx": "react-jsx",
                "strict": true,
                "baseUrl": ".",
                "paths": { "@/*": ["./src/*"] }
              },
              "include": ["src"]
            }
            """;

    // 포털 globals.css의 .theme-dark 토큰 + @theme inline 매핑을 배포용(다크 고정)으로 이식.
    // 파츠/컴포넌트가 쓰는 bg-panel·text-muted-soft·rounded-panel 등 유틸이 이 @theme에서 나온다.
    private static final String INDEX_CSS = """
            @import "tailwindcss";

            :root {
              --background: #07080b;
              --foreground: #f4f7f1;
              --brand: #baff4a;
              --brand-strong: #a3e63f;
              --panel: #11141a;
              --line: rgba(255, 255, 255, 0.09);
              --line-strong: rgba(255, 255, 255, 0.16);
              --muted: #9aa39a;
              --muted-soft: #6e776f;
              --soft: rgba(186, 255, 74, 0.08);
              --danger: #ff6b6b;
              --danger-soft: rgba(255, 107, 107, 0.35);
              --accent: #b39dff;
              --accent-soft: rgba(179, 157, 255, 0.14);
              --success: #79d95e;
              --success-soft: rgba(121, 217, 94, 0.12);
              --radius-panel-size: 16px;
              --preview-status-ok: #46d17f;
              --preview-status-warn: #f5a623;
              --preview-status-idle: #8b93a0;
              --preview-status-danger: #f2555a;
              --preview-blueprint-brand: var(--brand);
              --preview-blueprint-info: #5aa8ff;
              --preview-blueprint-violet: #a98bff;
              --preview-blueprint-cyan: #4fd1c5;
              --preview-blueprint-surface-raised: color-mix(in srgb, var(--panel) 94%, white 6%);
              --preview-blueprint-surface-soft: color-mix(in srgb, var(--panel) 97%, white 3%);
              --preview-blueprint-shadow: 0 18px 48px rgba(0, 0, 0, 0.24);
              color-scheme: dark;
            }

            @theme inline {
              --color-background: var(--background);
              --color-foreground: var(--foreground);
              --color-brand: var(--brand);
              --color-brand-strong: var(--brand-strong);
              --color-panel: var(--panel);
              --color-line: var(--line);
              --color-line-strong: var(--line-strong);
              --color-muted: var(--muted);
              --color-muted-soft: var(--muted-soft);
              --color-soft: var(--soft);
              --color-danger: var(--danger);
              --color-danger-soft: var(--danger-soft);
              --color-accent: var(--accent);
              --color-accent-soft: var(--accent-soft);
              --color-success: var(--success);
              --color-success-soft: var(--success-soft);
              --radius-panel: var(--radius-panel-size);
              --font-sans: system-ui, -apple-system, "Segoe UI", sans-serif;
              --font-mono: ui-monospace, SFMono-Regular, Menlo, monospace;
            }

            * { box-sizing: border-box; }
            body {
              margin: 0;
              background: var(--background);
              color: var(--foreground);
              font-family: var(--font-sans, system-ui, sans-serif);
            }
            """;

    // 얇은 진입점 — JSON을 공유 호스트 PreviewRuntimeApp에 넘긴다(컴포넌트 인라인 없음).
    private static final String APP_TSX = """
            import { PreviewRuntimeApp } from "@/components/preview-runtime/PreviewRuntimeApp";
            import type { PreviewCapability, PreviewPage, PreviewAuthStrategy, Purpose } from "@/components/preview-runtime/types";
            import type { Block } from "@/components/preview-runtime/blueprint";
            import type { ApiBinding, FlowBlueprint } from "@/components/preview-runtime/flow/types";
            import type { PreviewCompiledScenario, PreviewMode } from "@/lib/types";

            const API_BASE_URL = __API_BASE_URL_JSON__ as string;
            const CAPABILITIES = __CAPABILITIES_JSON__ as unknown as PreviewCapability[];
            const PAGES = __PAGES_JSON__ as unknown as PreviewPage[];
            const PAGE_BLOCKS = __PAGE_BLOCKS_JSON__ as unknown as Record<string, Block[]>;
            const FLOWS = __FLOWS_JSON__ as unknown as FlowBlueprint[];
            const BINDINGS = __BINDINGS_JSON__ as unknown as ApiBinding[];
            const AUTH_STRATEGY = __AUTH_STRATEGY_JSON__ as unknown as PreviewAuthStrategy;
            const PURPOSE = __PURPOSE_JSON__ as unknown as Purpose | null;
            const SCENARIOS = __SCENARIOS_JSON__ as unknown as PreviewCompiledScenario[];
            const PREVIEW_MODE = __PREVIEW_MODE_JSON__ as unknown as PreviewMode;

            export default function App() {
              return (
                <main style={{ maxWidth: 1120, margin: "0 auto", padding: 24 }}>
                  <PreviewRuntimeApp
                    apiBaseUrl={API_BASE_URL}
                    capabilities={CAPABILITIES}
                    pages={PAGES}
                    pageBlocks={PAGE_BLOCKS}
                    flows={FLOWS}
                    bindings={BINDINGS}
                    authStrategy={AUTH_STRATEGY}
                    purpose={PURPOSE}
                    scenarios={SCENARIOS}
                    previewMode={PREVIEW_MODE}
                  />
                </main>
              );
            }
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

}
