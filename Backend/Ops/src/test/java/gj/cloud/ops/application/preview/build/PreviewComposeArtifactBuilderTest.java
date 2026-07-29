package gj.cloud.ops.application.preview.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.deployment.dto.ComposeArtifact;
import gj.cloud.ops.application.deployment.dto.UploadedFile;
import gj.cloud.ops.application.preview.analysis.AuthStrategy;
import gj.cloud.ops.application.preview.analysis.AutomationPolicy;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.PageSkeletonType;
import gj.cloud.ops.application.preview.analysis.PreviewBlockResolver;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.application.preview.flow.RuleBasedFlowGenerator;
import gj.cloud.ops.application.preview.planning.model.PagePlanMapper;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenario;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenarioStage;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompilationStatus;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.PreviewMode;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageRole;
import gj.cloud.ops.domain.deployment.enums.SourceType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// Phase D — 생성된 Vite 프로젝트 소스가 구조적으로 온전한지 검증(플레이스홀더 치환 누락, JSON 임베딩 손상,
// 필수 파일 누락 등). 실제 npm/vite 빌드까지는 네트워크·시간이 필요해 이 유닛 테스트에서는 하지 않고
// (수동으로 한 번 확인함), 여기서는 항상 빠르게 도는 구조 검증만 담당한다.
class PreviewComposeArtifactBuilderTest {

    private final PreviewComposeArtifactBuilder builder =
            new PreviewComposeArtifactBuilder(new ObjectMapper(), new PreviewBlockResolver());
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generatesAllExpectedFilesWithoutLeftoverPlaceholders() {
        // API_KEY_HEADER로 검증 — Bearer만 가정하던 예전 방식이었다면 이 값이 App.tsx에 안 박혀 있었을 것.
        // purpose=API_TEST(BlueprintCompiler가 손대지 않는 목적)로 기본 Variant(resource-table)가
        // 그대로 나오는지 확인한다.
        ComposeArtifact artifact = buildArtifact(Purpose.API_TEST);

        assertThat(artifact.sourceType()).isEqualTo(SourceType.AUTO_PREVIEW);
        assertThat(artifact.exposedRoutes()).hasSize(1);
        assertThat(artifact.exposedRoutes().get(0).nickname()).matches("^preview-[a-z0-9]{8}$");
        assertThat(artifact.healthChecks()).hasSize(1);

        Map<String, String> files = artifact.uploadedFiles().stream()
                .collect(Collectors.toMap(UploadedFile::vmPath, f -> new String(f.content(), StandardCharsets.UTF_8)));

        assertThat(files.keySet()).contains(
                "package.json", "tsconfig.json", "vite.config.ts", "index.html",
                "Dockerfile", "src/main.tsx", "src/index.css", "src/App.tsx",
                "src/components/preview-runtime/PreviewPageRenderer.tsx",
                "src/components/preview-runtime/journey/JourneyRuntime.tsx",
                "src/components/preview-runtime/journey/recipes.ts",
                "src/components/preview-runtime/journey/validator.ts",
                "src/components/preview-runtime/scenario/ScenarioWorkbench.tsx",
                "src/components/preview-runtime/scenario/ProductExperienceRuntime.tsx",
                "src/components/preview-runtime/scenario/ProductExperienceInspector.tsx",
                "src/components/preview-runtime/scenario/productExperience.ts",
                "src/components/preview-runtime/scenario/productTheme.ts",
                "src/components/preview-runtime/scenario/runtime.ts",
                "src/components/preview-runtime/blueprints/manifests/component-manifest.json",
                "src/components/preview-runtime/blueprints/adapters/generatedPartComponents.ts");

        String appTsx = files.get("src/App.tsx");
        assertThat(appTsx).doesNotContain(
                "__API_BASE_URL_JSON__", "__CAPABILITIES_JSON__", "__PAGES_JSON__",
                "__AUTH_STRATEGY_JSON__", "__PURPOSE_JSON__", "__PAGE_BLOCKS_JSON__", "__FLOWS_JSON__",
                "__BINDINGS_JSON__", "__SCENARIOS_JSON__", "__PREVIEW_MODE_JSON__");
        // App.tsx는 JSON만 주입하고 React 구현은 공유 Runtime을 import해야 한다.
        assertThat(appTsx).contains("import { PreviewRuntimeApp }");
        assertThat(appTsx).doesNotContain("function ResourceTable(", "function PageRenderer(");
        assertThat(files.get("src/components/preview-runtime/PreviewRuntimeApp.tsx"))
                .contains("<ProductExperienceRuntime")
                .contains("서비스 화면")
                .contains("시나리오 디버거");
        assertThat(files.get("src/components/preview-runtime/scenario/productExperience.ts"))
                .contains("composeProductExperience")
                .contains("validateProductExperience");
        assertThat(files.get("src/components/preview-runtime/scenario/ProductExperienceInspector.tsx"))
                .contains("Live test inspector")
                .contains("이 단계부터 재시도")
                .contains("Raw request body");
        assertThat(files.get("src/components/preview-runtime/scenario/productTheme.ts"))
                .contains("selectProductExperienceTheme")
                .contains("--px-accent")
                .contains("crimson-security");
        assertThat(appTsx).contains("https://api.example.com");
        assertThat(appTsx).contains("\"auth.login\"");
        assertThat(appTsx).contains("\"vms-page\"");
        assertThat(appTsx).contains("\"dashboard\"");
        assertThat(appTsx).contains("\"test-scenario\"").contains("\"SCENARIO_PREVIEW\"");
        assertThat(appTsx).contains("\"API_KEY_HEADER\"").contains("\"X-API-Key\"");
        // Blueprint 1차 — PAGE_BLOCKS가 componentId 기준으로 실제로 채워지는지(빈 객체로 치환되지
        // 않았는지)까지 확인. "login-form"은 auth-login 페이지의 Block.componentId여야 한다.
        assertThat(appTsx).contains("\"componentId\":\"login-form\"");
        assertThat(appTsx).contains("\"componentId\":\"resource-table\"");
        assertThat(appTsx).contains("\"componentId\":\"dashboard-view\"");

        // package.json은 유효한 JSON이어야 한다(단순 문자열 결합 실수 감지용)
        assertThatIsValidJson(files.get("package.json"));
    }

    // Direction Recovery Change Request Increment 4 회귀 테스트 — purpose=PRODUCT_LIKE면
    // BlueprintCompiler가 list 계열 Block의 componentId를 resource-card-grid로 바꿔 생성된 소스에
    // 실제로 반영되는지 확인한다.
    @Test
    void productLikePurposeCompilesToResourceCardGridVariant() {
        ComposeArtifact artifact = buildArtifact(Purpose.PRODUCT_LIKE);

        Map<String, String> files = artifact.uploadedFiles().stream()
                .collect(Collectors.toMap(UploadedFile::vmPath, f -> new String(f.content(), StandardCharsets.UTF_8)));
        String appTsx = files.get("src/App.tsx");

        assertThat(appTsx).contains("\"componentId\":\"resource-card-grid\"");
        assertThat(appTsx).contains("\"componentId\":\"infrastructure-resource-detail\"");
        assertThat(appTsx).contains("\"componentId\":\"resource-provisioning-wizard\"");
        assertThat(appTsx).doesNotContain("function ResourceCardGrid(", "function FormDrawer(");
        assertThat(appTsx).contains("const PURPOSE = \"PRODUCT_LIKE\" as unknown as Purpose | null");
    }

    // purpose=ADMIN이면 destructive 계열도 typed-confirm-modal로 컴파일되는지 확인한다.
    @Test
    void adminPurposeCompilesToTypedConfirmModalVariant() {
        ComposeArtifact artifact = buildArtifact(Purpose.ADMIN);

        Map<String, String> files = artifact.uploadedFiles().stream()
                .collect(Collectors.toMap(UploadedFile::vmPath, f -> new String(f.content(), StandardCharsets.UTF_8)));
        String appTsx = files.get("src/App.tsx");

        assertThat(appTsx).contains("\"componentId\":\"dependency-impact-modal\"");
        assertThat(appTsx).doesNotContain("function TypedConfirmModal(");
        assertThat(appTsx).contains("const PURPOSE = \"ADMIN\" as unknown as Purpose | null");
    }

    // §22 7번 — sampleCapabilities()의 vms 리소스는 CREATE+DETAIL을 모두 갖고 있어
    // RuleBasedFlowGenerator가 실제로 create-flow를 만들어야 한다. 이 flow가 App.tsx의 FLOWS
    // 전역에 실제로 임베딩되고, PageRenderer의 Journey 실행 단계가 Flow로 위임하는지까지 확인한다.
    @Test
    void generatedCreateFlowIsEmbeddedAndWiredIntoPageRenderer() {
        ComposeArtifact artifact = buildArtifact(Purpose.API_TEST);

        Map<String, String> files = artifact.uploadedFiles().stream()
                .collect(Collectors.toMap(UploadedFile::vmPath, f -> new String(f.content(), StandardCharsets.UTF_8)));
        String appTsx = files.get("src/App.tsx");

        assertThat(appTsx).contains("\"vms-page-create-flow\"");
        assertThat(appTsx).contains("\"vms.create-binding\"");
        assertThat(files.get("src/components/preview-runtime/PreviewPageRenderer.tsx"))
                .contains("createJourneyBlueprint({ pageId: page.id, mode, capability, componentId })")
                .contains("const flow = flows.find(")
                .contains("form: values");
    }

    @Test
    void commandFlowAndChildResourceComponentAreEmbeddedAndWired() {
        ComposeArtifact artifact = buildArtifact(Purpose.PRODUCT_LIKE);

        Map<String, String> files = artifact.uploadedFiles().stream()
                .collect(Collectors.toMap(UploadedFile::vmPath, f -> new String(f.content(), StandardCharsets.UTF_8)));
        String appTsx = files.get("src/App.tsx");

        assertThat(appTsx).contains("\"vms-page-start-flow\"");
        assertThat(files.get("src/components/preview-runtime/PreviewPageRenderer.tsx"))
                .contains("beginJourney(\"COMMAND\", capability")
                .contains("onExecute={executeJourney}");
        assertThat(appTsx).contains("\"componentId\":\"child-resource-list\"");
        assertThat(files.get("src/components/preview-runtime/ChildResourceList.tsx"))
                .contains("pathParamId={parentId}");
    }

    private void assertThatIsValidJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            assertThat(node.has("name")).isTrue();
        } catch (IOException e) {
            throw new AssertionError("package.json이 유효한 JSON이 아닙니다", e);
        }
    }

    // 포털 실물 컴포넌트를 번들한 생성 프로젝트를 temp에 쓴다.
    // 개별 실행 후 npm install && npx tsc --noEmit && npx vite build로 그린 확인.
    @Test
    void writeRealComponentProjectToTempDirForManualBuildVerification() throws IOException {
        ComposeArtifact artifact = buildArtifact(Purpose.PRODUCT_LIKE);
        Path dir = Files.createTempDirectory("gamjabox-preview-real-");
        for (UploadedFile file : artifact.uploadedFiles()) {
            Path target = dir.resolve(file.vmPath());
            Files.createDirectories(target.getParent());
            Files.write(target, file.content());
        }
        System.out.println("Real-component preview project written to: " + dir);
    }

    private ComposeArtifact buildArtifact(Purpose purpose) {
        List<Capability> capabilities = sampleCapabilities();
        List<PageDraft> pages = samplePages();
        RuleBasedFlowGenerator.ValidatedResult generated = new RuleBasedFlowGenerator()
                .generateValidated(PagePlanMapper.from(pages, capabilities), capabilities);
        return builder.build(
                "https://api.example.com", capabilities, pages,
                generated.result().flows(), generated.result().bindings(),
                AuthStrategy.apiKeyHeader("X-API-Key"), purpose,
                List.of(sampleScenario()), PreviewMode.SCENARIO_PREVIEW, Map.of());
    }

    private CompiledScenario sampleScenario() {
        return new CompiledScenario(
                "test-scenario", "Test scenario", "developer", "Verify scenario embedding", "complete",
                List.of(new CompiledScenarioStage(
                        "complete", StageRole.COMPLETE, "Done", null, null, false,
                        List.of(), List.of(), List.of(), List.of(), List.of(), null, RiskLevel.SAFE)),
                List.of(), CompilationStatus.EXECUTABLE, List.of(), 1.0, "1.0", "3.0.0"
        );
    }

    private List<Capability> sampleCapabilities() {
        return List.of(
                new Capability("auth.login", "auth", CapabilityType.LOGIN, "login", "/auth/login", "POST",
                        false, false, false, "HIGH", List.of(), List.of("email", "password"), "data.accessToken", null,
                        RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null,
                        CapabilityKind.AUTH, null, List.of()),
                // 목록/총개수 위치를 이중 봉투(data.content/data.totalElements)로 미리 지정해 생성된 앱이
                // 실제로 이 경로를 우선 사용하는지(런타임 재귀 휴리스틱으로 대체되지 않는지) 빌드까지 검증한다.
                new Capability("vms.list", "vms", CapabilityType.LIST, "listVms", "/vms", "GET",
                        true, false, false, "HIGH", List.of(), List.of(), null, "keyword",
                        RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, "data.content", "data.totalElements",
                        CapabilityKind.QUERY, null, List.of()),
                // {id}가 아니라 {vmId}를 씀 — 경로 파라미터 이름이 "id"가 아닐 때도 buildUrl이 마지막
                // {...}를 찾아 치환하는지(이름 매칭이 아니라 위치 매칭인지) 이 값으로 실제 빌드까지 검증한다.
                new Capability("vms.detail", "vms", CapabilityType.DETAIL, "getVm", "/vms/{vmId}", "GET",
                        false, false, false, "HIGH", List.of(), List.of(), null, null,
                        RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null,
                        CapabilityKind.QUERY, null, List.of()),
                new Capability("vms.create", "vms", CapabilityType.CREATE, "createVm", "/vms", "POST",
                        false, false, false, "HIGH", List.of(), List.of("name", "planType"), null, null,
                        RiskLevel.STATE_CHANGING, AutomationPolicy.USER_INITIATED, null, null,
                        CapabilityKind.MUTATION, null, List.of()),
                new Capability("vms.delete", "vms", CapabilityType.DELETE, "deleteVm", "/vms/{vmId}", "DELETE",
                        false, false, false, "HIGH", List.of(), List.of(), null, null,
                        RiskLevel.DESTRUCTIVE, AutomationPolicy.EXPLICIT_CONFIRMATION, null, null,
                        CapabilityKind.MUTATION, null, List.of()),
                new Capability("vms.start", "vms", null, "startVm", "/vms/{vmId}/start", "POST",
                        false, false, false, "HIGH", List.of(), List.of(), null, null,
                        RiskLevel.STATE_CHANGING, AutomationPolicy.USER_INITIATED, null, null,
                        CapabilityKind.COMMAND, "start", List.of("vms.detail")),
                new Capability("ports.list", "ports", CapabilityType.LIST, "listPorts", "/vms/{vmId}/ports", "GET",
                        false, false, false, "HIGH", List.of(), List.of(), null, null,
                        RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, "data", null,
                        CapabilityKind.QUERY, null, List.of()),
                new Capability("ports.create", "ports", CapabilityType.CREATE, "createPort", "/vms/{vmId}/ports", "POST",
                        false, false, false, "HIGH", List.of(), List.of("port", "protocol"), null, null,
                        RiskLevel.STATE_CHANGING, AutomationPolicy.USER_INITIATED, null, null,
                        CapabilityKind.MUTATION, null, List.of()),
                new Capability("tags.list", "tags", CapabilityType.LIST, "listTags", "/tags", "GET",
                        false, false, false, "HIGH", List.of(), List.of(), null, null,
                        RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null,
                        CapabilityKind.QUERY, null, List.of())
        );
    }

    private List<PageDraft> samplePages() {
        return List.of(
                new PageDraft("dashboard", "대시보드", PageSkeletonType.DASHBOARD, List.of("vms.list", "tags.list")),
                new PageDraft("auth-login", "로그인", PageSkeletonType.AUTH_PAGE, List.of("auth.login")),
                new PageDraft("vms-page", "Vms", PageSkeletonType.LIST_DETAIL,
                        List.of("vms.list", "vms.detail", "vms.create", "vms.delete", "vms.start",
                                "ports.list", "ports.create")),
                new PageDraft("tags-page", "Tags", PageSkeletonType.RESOURCE_LIST, List.of("tags.list"))
        );
    }
}
