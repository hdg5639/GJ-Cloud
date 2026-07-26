package gj.cloud.ops.application.preview.service;

import gj.cloud.ops.application.preview.analysis.AutomationPolicy;
import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.PageSkeletonType;
import gj.cloud.ops.application.preview.analysis.PreviewBlockResolver;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Direction Recovery Change Request §13.1 — PreviewController(/ops/preview/blocks)와
// PreviewDeployController가 공유하는 resolveAll+compile 조합이 실제로 두 단계를 이어붙이는지 확인한다
// (PreviewBlockResolverTest는 resolve만, BlueprintCompilerTest는 compile만 독립적으로 검증하므로
// 이 테스트가 그 둘을 실제로 잇는 유일한 지점).
class PreviewBlueprintServiceTest {

    private final PreviewBlueprintService service = new PreviewBlueprintService(new PreviewBlockResolver());

    @Test
    void nullPurposeReturnsResolvedBlocksUnchanged() {
        Capability list = capability("vms.list", "vms", CapabilityType.LIST);
        PageDraft page = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of("vms.list"));

        Map<String, List<Block>> compiled = service.compilePageBlocks(List.of(page), List.of(list), null);

        assertThat(compiled.get("vms-page")).containsExactly(
                new Block("list", "resource-table", "page.main", List.of("vms.list"), null));
    }

    @Test
    void productLikePurposeCompilesResolvedBlocksToCardGrid() {
        Capability list = capability("vms.list", "vms", CapabilityType.LIST);
        PageDraft page = new PageDraft("vms-page", "Vms", PageSkeletonType.RESOURCE_LIST, List.of("vms.list"));

        Map<String, List<Block>> compiled = service.compilePageBlocks(List.of(page), List.of(list), Purpose.PRODUCT_LIKE);

        assertThat(compiled.get("vms-page").get(0).componentId()).isEqualTo("resource-card-grid");
    }

    private Capability capability(String id, String resourceName, CapabilityType type) {
        return new Capability(id, resourceName, type, null, "/" + resourceName, "GET",
                false, false, false, "HIGH", List.of(), List.of(), null, null,
                RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null,
                CapabilityKind.QUERY, null, List.of());
    }
}
