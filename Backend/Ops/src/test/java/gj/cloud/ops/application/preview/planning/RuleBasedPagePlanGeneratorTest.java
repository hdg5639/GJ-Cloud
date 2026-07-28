package gj.cloud.ops.application.preview.planning;

import gj.cloud.ops.application.preview.analysis.AutomationPolicy;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.PageSkeletonType;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Direction Recovery Change Request §18.1 회귀 테스트 축소판 — purpose가 실제로 페이지 구성(대시보드
// 포함 여부)을 분기시키는지 확인한다. Variant Registry가 없어 컴포넌트/레이아웃 자체는 아직 못 바꾸지만,
// 이 축(페이지가 아예 생기고 안 생기고)은 지금도 검증 가능하다.
class RuleBasedPagePlanGeneratorTest {

    private final RuleBasedPagePlanGenerator generator = new RuleBasedPagePlanGenerator();

    @Test
    void apiTestPurposeNeverIncludesDashboardEvenWithMultipleResources() {
        List<Capability> capabilities = List.of(
                listCapability("vms"), listCapability("tags"));

        List<PageDraft> pages = generator.generate(capabilities, Purpose.API_TEST);

        assertThat(pages).noneMatch(p -> p.skeleton() == PageSkeletonType.DASHBOARD);
    }

    @Test
    void adminPurposeIncludesDashboardEvenWithOnlyOneResource() {
        List<Capability> capabilities = List.of(listCapability("vms"));

        List<PageDraft> pages = generator.generate(capabilities, Purpose.ADMIN);

        assertThat(pages).anyMatch(p -> p.skeleton() == PageSkeletonType.DASHBOARD);
    }

    @Test
    void productLikePurposeKeepsExistingTwoResourceThreshold() {
        List<Capability> single = List.of(listCapability("vms"));
        List<Capability> multiple = List.of(listCapability("vms"), listCapability("tags"));

        assertThat(generator.generate(single, Purpose.PRODUCT_LIKE))
                .noneMatch(p -> p.skeleton() == PageSkeletonType.DASHBOARD);
        assertThat(generator.generate(multiple, Purpose.PRODUCT_LIKE))
                .anyMatch(p -> p.skeleton() == PageSkeletonType.DASHBOARD);
    }

    @Test
    void nullPurposeBehavesLikeProductLike() {
        List<Capability> multiple = List.of(listCapability("vms"), listCapability("tags"));

        assertThat(generator.generate(multiple, null))
                .anyMatch(p -> p.skeleton() == PageSkeletonType.DASHBOARD);
    }

    @Test
    void resourceWithDetailButNoListBecomesResourceDetailPage() {
        // 실제 겪음(memme API의 boards): GET 목록 없이 상세만 있는 리소스. RESOURCE_LIST로 만들면
        // page.main 목록 Block이 0개라 배포 slot 검증에서 깨지므로 RESOURCE_DETAIL이어야 한다.
        List<Capability> capabilities = List.of(
                capability("boards.detail", "boards", CapabilityType.DETAIL));

        List<PageDraft> pages = generator.generate(capabilities, Purpose.ADMIN);

        assertThat(pages).filteredOn(p -> p.id().equals("boards-page"))
                .singleElement()
                .satisfies(p -> assertThat(p.skeleton()).isEqualTo(PageSkeletonType.RESOURCE_DETAIL));
    }

    @Test
    void resourceWithNeitherListNorDetailIsSkipped() {
        // 업로드/변환처럼 CREATE만 있는 리소스는 유효한 MVP 페이지를 만들 수 없어 건너뛴다.
        List<Capability> capabilities = List.of(
                listCapability("vms"),
                capability("video.create", "video", CapabilityType.CREATE));

        List<PageDraft> pages = generator.generate(capabilities, Purpose.ADMIN);

        assertThat(pages).noneMatch(p -> p.id().equals("video-page"));
        assertThat(pages).anyMatch(p -> p.id().equals("vms-page"));
    }

    @Test
    void nestedChildResourceIsFoldedIntoParentPageNotStandalone() {
        // 실제 겪음(machines/ports): 자식 리소스를 별도 페이지로 떼면 자식 요청의 부모 경로 파라미터
        // (machineId)를 채울 컨텍스트가 없어 "{machineId}"가 그대로 나가 실패한다. 자식 capability는
        // 부모(machines) 페이지에 함께 담겨야 한다(AC-6).
        List<Capability> capabilities = List.of(
                capabilityWithPath("machines.list", "machines", CapabilityType.LIST, "/machines"),
                capabilityWithPath("machines.detail", "machines", CapabilityType.DETAIL, "/machines/{id}"),
                capabilityWithPath("ports.list", "ports", CapabilityType.LIST, "/machines/{machineId}/ports"),
                capabilityWithPath("ports.create", "ports", CapabilityType.CREATE, "/machines/{machineId}/ports"));

        List<PageDraft> pages = generator.generate(capabilities, Purpose.ADMIN);

        assertThat(pages).noneMatch(p -> p.id().equals("ports-page"));
        assertThat(pages).filteredOn(p -> p.id().equals("machines-page"))
                .singleElement()
                .satisfies(p -> assertThat(p.capabilityIds())
                        .contains("machines.list", "machines.detail", "ports.list", "ports.create"));
    }

    private Capability listCapability(String resourceName) {
        return capability(resourceName + ".list", resourceName, CapabilityType.LIST);
    }

    private Capability capabilityWithPath(String id, String resourceName, CapabilityType type, String path) {
        return new Capability(id, resourceName, type, null,
                path, type == CapabilityType.CREATE ? "POST" : "GET",
                false, false, false, "HIGH", List.of(), List.of(), null, null,
                RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null,
                type == CapabilityType.CREATE ? CapabilityKind.MUTATION : CapabilityKind.QUERY, null, List.of());
    }

    private Capability capability(String id, String resourceName, CapabilityType type) {
        return new Capability(id, resourceName, type, null,
                "/" + resourceName, type == CapabilityType.CREATE ? "POST" : "GET",
                false, false, false, "HIGH", List.of(), List.of(), null, null,
                RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null,
                type == CapabilityType.CREATE ? CapabilityKind.MUTATION : CapabilityKind.QUERY, null, List.of());
    }
}
