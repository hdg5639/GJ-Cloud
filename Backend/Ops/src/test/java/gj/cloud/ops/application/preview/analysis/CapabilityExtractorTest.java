package gj.cloud.ops.application.preview.analysis;

import gj.cloud.ops.application.deployment.repoanalysis.RepositoryEvidence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// GamjaBox_2.0_Key_Features.md 5·8절 규칙 회귀 테스트 — CRUD 패턴과 로그인 오퍼레이션이 AI 없이도
// 결정론적으로 뽑히는지, 검색/정렬/페이지네이션 쿼리 파라미터가 LIST capability에만 반영되는지 확인.
class CapabilityExtractorTest {

    private final CapabilityExtractor extractor = new CapabilityExtractor();

    @Test
    void extractsCrudCapabilitiesAndQueryFeaturesForListOnly() {
        List<ApiOperationEvidence> operations = List.of(
                operation("GET", "/vms", "listVms", true,
                        List.of(param("search", "query"), param("sort", "query"), param("page", "query"))),
                operation("GET", "/vms/{id}", "getVm", false, List.of()),
                operation("POST", "/vms", "createVm", false, List.of()),
                operation("PATCH", "/vms/{id}", "updateVm", false, List.of()),
                operation("DELETE", "/vms/{id}", "deleteVm", false, List.of())
        );
        OpenApiEvidence evidence = new OpenApiEvidence("vm-service", "1.0", List.of(), List.of(), operations, 0);

        List<Capability> capabilities = extractor.extract(evidence);

        assertThat(capabilities).extracting(Capability::id)
                .containsExactlyInAnyOrder("vms.list", "vms.detail", "vms.create", "vms.update", "vms.delete");

        Capability list = findCapability(capabilities, "vms.list");
        assertThat(list.hasSearch()).isTrue();
        assertThat(list.hasSort()).isTrue();
        assertThat(list.hasPagination()).isTrue();
        assertThat(list.searchParam()).isEqualTo("search");
        assertThat(list.confidence()).isEqualTo(RepositoryEvidence.CONFIDENCE_HIGH);

        Capability detail = findCapability(capabilities, "vms.detail");
        assertThat(detail.hasSearch()).isFalse();
        assertThat(detail.searchParam()).isNull();

        assertThat(list.kind()).isEqualTo(CapabilityKind.QUERY);
        assertThat(detail.kind()).isEqualTo(CapabilityKind.QUERY);
        assertThat(findCapability(capabilities, "vms.create").kind()).isEqualTo(CapabilityKind.MUTATION);
        assertThat(findCapability(capabilities, "vms.update").kind()).isEqualTo(CapabilityKind.MUTATION);
        assertThat(findCapability(capabilities, "vms.delete").kind()).isEqualTo(CapabilityKind.MUTATION);
    }

    // Direction Recovery Change Request §7.1/§4.3 회귀 테스트 — "/resource/{id}/action" 형태의 커맨드형
    // 오퍼레이션이 discard되지 않고 COMMAND capability로 인식되는지, 그리고 예전엔 "start"라는 가짜
    // 리소스에 대한 CREATE로 오분류됐던 버그가 고쳐졌는지 확인한다.
    @Test
    void extractsKnownCommandKeywordAsCommandCapabilityWithDetailDependency() {
        List<ApiOperationEvidence> operations = List.of(
                operation("GET", "/vms/{id}", "getVm", false, List.of()),
                operation("POST", "/vms/{id}/start", "startVm", false, List.of())
        );
        OpenApiEvidence evidence = new OpenApiEvidence("vm-service", "1.0", List.of(), List.of(), operations, 0);

        List<Capability> capabilities = extractor.extract(evidence);

        Capability start = findCapability(capabilities, "vms.start");
        assertThat(start.kind()).isEqualTo(CapabilityKind.COMMAND);
        assertThat(start.type()).isNull();
        assertThat(start.resourceName()).isEqualTo("vms");
        assertThat(start.action()).isEqualTo("start");
        assertThat(start.dependencies()).containsExactly("vms.detail");

        // 회귀: "start"가 더 이상 독립된 가짜 리소스에 대한 CREATE로 오분류되지 않는다.
        assertThat(capabilities).noneMatch(c -> "start".equals(c.resourceName()));
        assertThat(capabilities).noneMatch(c -> c.type() == CapabilityType.CREATE);
    }

    @Test
    void unknownCommandKeywordIsPreservedInsteadOfDiscarded() {
        List<ApiOperationEvidence> operations = List.of(
                operation("POST", "/vms/{id}/reboot", "rebootVm", false, List.of())
        );
        OpenApiEvidence evidence = new OpenApiEvidence("vm-service", "1.0", List.of(), List.of(), operations, 0);

        List<Capability> capabilities = extractor.extract(evidence);

        assertThat(capabilities).hasSize(1);
        Capability reboot = capabilities.get(0);
        assertThat(reboot.kind()).isEqualTo(CapabilityKind.COMMAND);
        assertThat(reboot.action()).isEqualTo("reboot");
        assertThat(reboot.dependencies()).isEmpty();
    }

    // auto-preview-design/05-capability-taxonomy.md §5·6 기본 매핑 표 회귀 테스트 — CapabilityType별
    // 위험도 기본값과 그 위험도의 기본 자동화 정책이 문서와 어긋나지 않는지 확인.
    @Test
    void assignsDefaultRiskAndAutomationPolicyPerCapabilityType() {
        List<ApiOperationEvidence> operations = List.of(
                operation("GET", "/vms", "listVms", true, List.of()),
                operation("GET", "/vms/{id}", "getVm", false, List.of()),
                operation("POST", "/vms", "createVm", false, List.of()),
                operation("PATCH", "/vms/{id}", "updateVm", false, List.of()),
                operation("DELETE", "/vms/{id}", "deleteVm", false, List.of())
        );
        OpenApiEvidence evidence = new OpenApiEvidence("vm-service", "1.0", List.of(), List.of(), operations, 0);

        List<Capability> capabilities = extractor.extract(evidence);

        assertThat(findCapability(capabilities, "vms.list").risk()).isEqualTo(RiskLevel.SAFE);
        assertThat(findCapability(capabilities, "vms.list").automationPolicy()).isEqualTo(AutomationPolicy.AUTO_SAFE);
        assertThat(findCapability(capabilities, "vms.detail").risk()).isEqualTo(RiskLevel.SAFE);
        assertThat(findCapability(capabilities, "vms.create").risk()).isEqualTo(RiskLevel.STATE_CHANGING);
        assertThat(findCapability(capabilities, "vms.create").automationPolicy()).isEqualTo(AutomationPolicy.USER_INITIATED);
        assertThat(findCapability(capabilities, "vms.update").risk()).isEqualTo(RiskLevel.STATE_CHANGING);
        assertThat(findCapability(capabilities, "vms.delete").risk()).isEqualTo(RiskLevel.DESTRUCTIVE);
        assertThat(findCapability(capabilities, "vms.delete").automationPolicy())
                .isEqualTo(AutomationPolicy.EXPLICIT_CONFIRMATION);
    }

    // 렌더러가 검색 파라미터 이름을 "search"로 하드코딩하면, API가 keyword/q/query 같은 다른 이름을 쓸 때
    // 검색이 조용히 실패한다 — 감지된 실제 이름이 그대로 보존되는지 확인.
    @Test
    void preservesActualQueryParameterNameWhenNotLiterallyNamedSearch() {
        List<ApiOperationEvidence> operations = List.of(
                operation("GET", "/posts", "listPosts", true, List.of(param("keyword", "query")))
        );
        OpenApiEvidence evidence = new OpenApiEvidence("blog-service", "1.0", List.of(), List.of(), operations, 0);

        Capability list = findCapability(extractor.extract(evidence), "posts.list");

        assertThat(list.hasSearch()).isTrue();
        assertThat(list.searchParam()).isEqualTo("keyword");
    }

    // auto-preview-design/04-api-binding-schema.md §7.2 회귀 테스트 — ApiResponse<Page<T>>처럼 이중으로
    // 감싼 응답에서도 목록 위치(collectionPath)와 총 개수 위치(totalCountPath)를 미리 추정해두는지 확인.
    @Test
    void detectsCollectionAndTotalCountPathFromWrappedListResponse() {
        List<ApiOperationEvidence> operations = List.of(
                operation("GET", "/vms", "listVms", true, List.of(),
                        List.of("success", "message", "data.content", "data.totalElements"),
                        List.of("data.content"))
        );
        OpenApiEvidence evidence = new OpenApiEvidence("vm-service", "1.0", List.of(), List.of(), operations, 0);

        Capability list = findCapability(extractor.extract(evidence), "vms.list");

        assertThat(list.collectionPath()).isEqualTo("data.content");
        assertThat(list.totalCountPath()).isEqualTo("data.totalElements");
    }

    @Test
    void leavesCollectionPathNullWhenMultipleUnnamedArrayCandidatesExist() {
        List<ApiOperationEvidence> operations = List.of(
                operation("GET", "/vms", "listVms", true, List.of(),
                        List.of(),
                        List.of("primaryRows", "secondaryRows"))
        );
        OpenApiEvidence evidence = new OpenApiEvidence("vm-service", "1.0", List.of(), List.of(), operations, 0);

        Capability list = findCapability(extractor.extract(evidence), "vms.list");

        assertThat(list.collectionPath()).isNull();
    }

    @Test
    void detectsLoginByCredentialFieldsWithHighConfidence() {
        List<ApiOperationEvidence> operations = List.of(
                new ApiOperationEvidence("/auth/login", "POST", "login", "로그인", List.of(),
                        List.of(), List.of("email", "password"), false, false, List.of(), List.of())
        );
        OpenApiEvidence evidence = new OpenApiEvidence("auth-service", "1.0", List.of(), List.of(), operations, 0);

        List<Capability> capabilities = extractor.extract(evidence);

        assertThat(capabilities).hasSize(1);
        Capability login = capabilities.get(0);
        assertThat(login.type()).isEqualTo(CapabilityType.LOGIN);
        assertThat(login.confidence()).isEqualTo(RepositoryEvidence.CONFIDENCE_HIGH);
    }

    @Test
    void detectsAccessTokenPathFromResponseFieldNameHints() {
        List<ApiOperationEvidence> operations = List.of(
                new ApiOperationEvidence("/auth/login", "POST", "login", "로그인", List.of(),
                        List.of(), List.of("email", "password"), false, false,
                        List.of("success", "message", "data.accessToken", "data.refreshToken"), List.of())
        );
        OpenApiEvidence evidence = new OpenApiEvidence("auth-service", "1.0", List.of(), List.of(), operations, 0);

        Capability login = extractor.extract(evidence).get(0);

        assertThat(login.accessTokenPath()).isEqualTo("data.accessToken");
    }

    @Test
    void leavesAccessTokenPathNullWhenNoFieldNameLooksLikeAToken() {
        List<ApiOperationEvidence> operations = List.of(
                new ApiOperationEvidence("/auth/login", "POST", "login", "로그인", List.of(),
                        List.of(), List.of("email", "password"), false, false,
                        List.of("success", "message", "data.sessionId"), List.of())
        );
        OpenApiEvidence evidence = new OpenApiEvidence("auth-service", "1.0", List.of(), List.of(), operations, 0);

        Capability login = extractor.extract(evidence).get(0);

        assertThat(login.accessTokenPath()).isNull();
    }

    @Test
    void doesNotInventLoginWhenNoTextHintOrCredentialFieldsExist() {
        List<ApiOperationEvidence> operations = List.of(
                new ApiOperationEvidence("/orders", "POST", "createOrder", "주문 생성", List.of(),
                        List.of(), List.of("itemId", "quantity"), true, false, List.of(), List.of())
        );
        OpenApiEvidence evidence = new OpenApiEvidence("shop-service", "1.0", List.of(), List.of(), operations, 0);

        List<Capability> capabilities = extractor.extract(evidence);

        assertThat(capabilities).noneMatch(c -> c.type() == CapabilityType.LOGIN);
    }

    private Capability findCapability(List<Capability> capabilities, String id) {
        return capabilities.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private ApiOperationEvidence operation(String method, String path, String operationId,
                                            boolean responseIsArray, List<ApiParameterEvidence> parameters) {
        return operation(method, path, operationId, responseIsArray, parameters, List.of(), List.of());
    }

    private ApiOperationEvidence operation(String method, String path, String operationId, boolean responseIsArray,
                                            List<ApiParameterEvidence> parameters, List<String> responseFieldPaths,
                                            List<String> arrayFieldPaths) {
        return new ApiOperationEvidence(path, method, operationId, null, List.of(), parameters,
                List.of(), true, responseIsArray, responseFieldPaths, arrayFieldPaths);
    }

    private ApiParameterEvidence param(String name, String in) {
        return new ApiParameterEvidence(name, in, "string", false);
    }
}
