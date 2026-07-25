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
        assertThat(list.confidence()).isEqualTo(RepositoryEvidence.CONFIDENCE_HIGH);

        Capability detail = findCapability(capabilities, "vms.detail");
        assertThat(detail.hasSearch()).isFalse();
    }

    @Test
    void detectsLoginByCredentialFieldsWithHighConfidence() {
        List<ApiOperationEvidence> operations = List.of(
                new ApiOperationEvidence("/auth/login", "POST", "login", "로그인", List.of(),
                        List.of(), List.of("email", "password"), false, false)
        );
        OpenApiEvidence evidence = new OpenApiEvidence("auth-service", "1.0", List.of(), List.of(), operations, 0);

        List<Capability> capabilities = extractor.extract(evidence);

        assertThat(capabilities).hasSize(1);
        Capability login = capabilities.get(0);
        assertThat(login.type()).isEqualTo(CapabilityType.LOGIN);
        assertThat(login.confidence()).isEqualTo(RepositoryEvidence.CONFIDENCE_HIGH);
    }

    @Test
    void doesNotInventLoginWhenNoTextHintOrCredentialFieldsExist() {
        List<ApiOperationEvidence> operations = List.of(
                new ApiOperationEvidence("/orders", "POST", "createOrder", "주문 생성", List.of(),
                        List.of(), List.of("itemId", "quantity"), true, false)
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
        return new ApiOperationEvidence(path, method, operationId, null, List.of(), parameters,
                List.of(), true, responseIsArray);
    }

    private ApiParameterEvidence param(String name, String in) {
        return new ApiParameterEvidence(name, in, "string", false);
    }
}
