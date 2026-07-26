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
            new PreviewComposeArtifactBuilder(new ObjectMapper(), new PreviewBlockResolver(), new RuleBasedFlowGenerator());
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generatesAllExpectedFilesWithoutLeftoverPlaceholders() {
        // API_KEY_HEADER로 검증 — Bearer만 가정하던 예전 방식이었다면 이 값이 App.tsx에 안 박혀 있었을 것.
        // purpose=API_TEST(BlueprintCompiler가 손대지 않는 목적)로 기본 Variant(resource-table)가
        // 그대로 나오는지 확인한다.
        ComposeArtifact artifact = builder.build(
                "https://api.example.com", sampleCapabilities(), samplePages(), AuthStrategy.apiKeyHeader("X-API-Key"),
                Purpose.API_TEST);

        assertThat(artifact.sourceType()).isEqualTo(SourceType.AUTO_PREVIEW);
        assertThat(artifact.exposedRoutes()).hasSize(1);
        assertThat(artifact.exposedRoutes().get(0).nickname()).matches("^preview-[a-z0-9]{8}$");
        assertThat(artifact.healthChecks()).hasSize(1);

        Map<String, String> files = artifact.uploadedFiles().stream()
                .collect(Collectors.toMap(UploadedFile::vmPath, f -> new String(f.content(), StandardCharsets.UTF_8)));

        assertThat(files.keySet()).containsExactlyInAnyOrder(
                "package.json", "tsconfig.json", "vite.config.ts", "index.html",
                "Dockerfile", "src/main.tsx", "src/index.css", "src/flow.ts", "src/App.tsx");

        String appTsx = files.get("src/App.tsx");
        assertThat(appTsx).doesNotContain(
                "__API_BASE_URL_JSON__", "__CAPABILITIES_JSON__", "__PAGES_JSON__",
                "__AUTH_STRATEGY_JSON__", "__PURPOSE_JSON__", "__PAGE_BLOCKS_JSON__", "__FLOWS_JSON__", "__BINDINGS_JSON__");
        // App.tsx가 실행기를 직접 인라인하지 않고 분리된 파일에서 import하는지(위 주석의 JVM 상수 풀
        // 제한 회피 이유가 실제로 지켜지는지) 확인.
        assertThat(appTsx).contains("from \"./flow\"");
        assertThat(files.get("src/flow.ts")).contains("export async function executeFlow(");
        assertThat(appTsx).contains("https://api.example.com");
        assertThat(appTsx).contains("\"auth.login\"");
        assertThat(appTsx).contains("\"vms-page\"");
        assertThat(appTsx).contains("\"dashboard\"");
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
        ComposeArtifact artifact = builder.build(
                "https://api.example.com", sampleCapabilities(), samplePages(), AuthStrategy.apiKeyHeader("X-API-Key"),
                Purpose.PRODUCT_LIKE);

        Map<String, String> files = artifact.uploadedFiles().stream()
                .collect(Collectors.toMap(UploadedFile::vmPath, f -> new String(f.content(), StandardCharsets.UTF_8)));
        String appTsx = files.get("src/App.tsx");

        assertThat(appTsx).contains("\"componentId\":\"resource-card-grid\"");
        assertThat(appTsx).contains("function ResourceCardGrid(");
        assertThat(appTsx).doesNotContain("\"componentId\":\"resource-table\"");
        // create/edit 계열도 같은 purpose로 함께 컴파일되는지 확인한다(vms-page의 CREATE 블록).
        assertThat(appTsx).contains("\"componentId\":\"form-drawer\"");
        assertThat(appTsx).contains("function FormDrawer(");
        assertThat(appTsx).doesNotContain("\"componentId\":\"create-edit-modal\"");
        assertThat(appTsx).contains("const PURPOSE: Purpose | null = \"PRODUCT_LIKE\"");
        assertThat(appTsx).contains("Service Preview").contains("shell-product-nav");
    }

    // purpose=ADMIN이면 destructive 계열도 typed-confirm-modal로 컴파일되는지 확인한다.
    @Test
    void adminPurposeCompilesToTypedConfirmModalVariant() {
        ComposeArtifact artifact = builder.build(
                "https://api.example.com", sampleCapabilities(), samplePages(), AuthStrategy.apiKeyHeader("X-API-Key"),
                Purpose.ADMIN);

        Map<String, String> files = artifact.uploadedFiles().stream()
                .collect(Collectors.toMap(UploadedFile::vmPath, f -> new String(f.content(), StandardCharsets.UTF_8)));
        String appTsx = files.get("src/App.tsx");

        assertThat(appTsx).contains("\"componentId\":\"typed-confirm-modal\"");
        assertThat(appTsx).contains("function TypedConfirmModal(");
        assertThat(appTsx).doesNotContain("\"componentId\":\"delete-confirm-modal\"");
        // ADMIN은 list 계열은 그대로 resource-table을 유지해야 한다(PRODUCT_LIKE 전용 규칙과 섞이지 않는지 확인).
        assertThat(appTsx).contains("\"componentId\":\"resource-table\"");
        assertThat(appTsx).contains("const PURPOSE: Purpose | null = \"ADMIN\"");
        assertThat(appTsx).contains("ADMIN CONSOLE").contains("shell-sidebar");
    }

    // §22 7번 — sampleCapabilities()의 vms 리소스는 CREATE+DETAIL을 모두 갖고 있어
    // RuleBasedFlowGenerator가 실제로 create-flow를 만들어야 한다. 이 flow가 App.tsx의 FLOWS
    // 전역에 실제로 임베딩되고, PageRenderer가 onSubmitOverride로 배선하는지까지 확인한다.
    @Test
    void generatedCreateFlowIsEmbeddedAndWiredIntoPageRenderer() {
        ComposeArtifact artifact = builder.build(
                "https://api.example.com", sampleCapabilities(), samplePages(), AuthStrategy.apiKeyHeader("X-API-Key"),
                Purpose.API_TEST);

        Map<String, String> files = artifact.uploadedFiles().stream()
                .collect(Collectors.toMap(UploadedFile::vmPath, f -> new String(f.content(), StandardCharsets.UTF_8)));
        String appTsx = files.get("src/App.tsx");

        assertThat(appTsx).contains("\"vms-page-create-flow\"");
        assertThat(appTsx).contains("\"vms.create-binding\"");
        assertThat(appTsx).contains("onSubmitOverride={createFlow ? (values) => runCreateFlow(createFlow, values) : undefined}");
    }

    @Test
    void commandFlowAndChildResourceComponentAreEmbeddedAndWired() {
        ComposeArtifact artifact = builder.build(
                "https://api.example.com", sampleCapabilities(), samplePages(), AuthStrategy.apiKeyHeader("X-API-Key"),
                Purpose.PRODUCT_LIKE);

        Map<String, String> files = artifact.uploadedFiles().stream()
                .collect(Collectors.toMap(UploadedFile::vmPath, f -> new String(f.content(), StandardCharsets.UTF_8)));
        String appTsx = files.get("src/App.tsx");

        assertThat(appTsx).contains("\"vms-page-start-flow\"");
        assertThat(appTsx).contains("runCommandFlow(capability, rowId(selectedRow))");
        assertThat(appTsx).contains("\"componentId\":\"child-resource-list\"");
        assertThat(appTsx).contains("function ChildResourceList(");
        assertThat(appTsx).contains("pathParamId={parentId}");
    }

    private void assertThatIsValidJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            assertThat(node.has("name")).isTrue();
        } catch (IOException e) {
            throw new AssertionError("package.json이 유효한 JSON이 아닙니다", e);
        }
    }

    // 수동 npm/vite 빌드 검증용 — 필요할 때만 이 테스트를 개별 실행해 임시 디렉토리에 파일을 쓴다.
    @Test
    void writeGeneratedProjectToTempDirForManualBuildVerification() throws IOException {
        ComposeArtifact artifact = builder.build(
                "https://api.example.com", sampleCapabilities(), samplePages(), AuthStrategy.apiKeyHeader("X-API-Key"),
                Purpose.PRODUCT_LIKE);
        Path dir = Files.createTempDirectory("gamjabox-preview-verify-");
        for (UploadedFile file : artifact.uploadedFiles()) {
            Path target = dir.resolve(file.vmPath());
            Files.createDirectories(target.getParent());
            Files.write(target, file.content());
        }
        System.out.println("Generated preview project written to: " + dir);
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
