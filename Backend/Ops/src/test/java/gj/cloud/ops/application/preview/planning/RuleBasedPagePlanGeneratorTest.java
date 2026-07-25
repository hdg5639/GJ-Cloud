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

    private Capability listCapability(String resourceName) {
        return new Capability(resourceName + ".list", resourceName, CapabilityType.LIST, null,
                "/" + resourceName, "GET", false, false, false, "HIGH", List.of(), List.of(), null, null,
                RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null,
                CapabilityKind.QUERY, null, List.of());
    }
}
