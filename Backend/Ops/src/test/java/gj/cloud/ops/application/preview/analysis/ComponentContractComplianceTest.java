package gj.cloud.ops.application.preview.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// PreviewBlockResolver가 만드는 Block이 ComponentContracts 카탈로그를 어기지 않는지 확인한다 —
// auto-preview-design/08-compatibility-rules.md가 요구하는 Compatibility Validator의 아주 작은
// 선행 버전. Block.slot이 그 컴포넌트가 실제로 받는 위치인지, Block이 물고 있는 capability 타입이
// 그 컴포넌트가 실제로 받는 타입인지를 검증해서, 리졸버 규칙이 나중에 실수로 바뀌어도(예: 잘못해서
// resource-table을 page.aside에 두는 등) 이 테스트가 즉시 잡아낸다.
class ComponentContractComplianceTest {

    private final PreviewBlockResolver resolver = new PreviewBlockResolver();

    @Test
    void everyResolvedBlockMatchesItsComponentContract() {
        Capability login = capability("auth.login", "auth", CapabilityType.LOGIN);
        Capability list = capability("vms.list", "vms", CapabilityType.LIST);
        Capability detail = capability("vms.detail", "vms", CapabilityType.DETAIL);
        Capability create = capability("vms.create", "vms", CapabilityType.CREATE);
        Capability update = capability("vms.update", "vms", CapabilityType.UPDATE);
        Capability delete = capability("vms.delete", "vms", CapabilityType.DELETE);
        Capability tagsList = capability("tags.list", "tags", CapabilityType.LIST);
        List<Capability> allCapabilities =
                List.of(login, list, detail, create, update, delete, tagsList);

        List<PageDraft> pages = List.of(
                new PageDraft("dashboard", "대시보드", PageSkeletonType.DASHBOARD,
                        List.of("vms.list", "tags.list")),
                new PageDraft("auth-login", "로그인", PageSkeletonType.AUTH_PAGE,
                        List.of("auth.login")),
                new PageDraft("vms-page", "Vms", PageSkeletonType.LIST_DETAIL,
                        List.of("vms.list", "vms.detail", "vms.create", "vms.update", "vms.delete"))
        );

        for (PageDraft page : pages) {
            List<Block> blocks = resolver.resolve(page, allCapabilities);
            for (Block block : blocks) {
                assertBlockMatchesContract(block, allCapabilities);
            }
            assertThat(SlotCardinalityValidator.validate(page.skeleton(), blocks))
                    .withFailMessage("페이지 %s의 Block이 Slot Contract를 어김", page.id())
                    .isEmpty();
        }
    }

    private void assertBlockMatchesContract(Block block, List<Capability> allCapabilities) {
        ComponentContract contract = ComponentContracts.ALL.get(block.componentId());
        assertThat(contract)
                .withFailMessage("등록되지 않은 componentId: %s", block.componentId())
                .isNotNull();

        assertThat(contract.acceptedSurfaces())
                .withFailMessage("%s는 %s Slot을 받지 않음", block.componentId(), block.slot())
                .contains(block.slot());

        if (!contract.allowsMultipleCapabilities()) {
            assertThat(block.capabilityIds())
                    .withFailMessage("%s는 capability를 정확히 1개만 받아야 함", block.componentId())
                    .hasSize(1);
        }

        for (String capabilityId : block.capabilityIds()) {
            CapabilityType type = allCapabilities.stream()
                    .filter(c -> c.id().equals(capabilityId))
                    .findFirst()
                    .orElseThrow()
                    .type();
            assertThat(contract.acceptedCapabilityTypes())
                    .withFailMessage("%s는 %s 타입을 받지 않음", block.componentId(), type)
                    .contains(type);
        }
    }

    private Capability capability(String id, String resourceName, CapabilityType type) {
        return new Capability(id, resourceName, type, null, "/" + resourceName, "GET",
                false, false, false, "HIGH", List.of(), List.of(), null, null,
                RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null,
                kindOf(type), null, List.of());
    }

    private CapabilityKind kindOf(CapabilityType type) {
        return switch (type) {
            case LIST, DETAIL -> CapabilityKind.QUERY;
            case CREATE, UPDATE, DELETE -> CapabilityKind.MUTATION;
            case LOGIN -> CapabilityKind.AUTH;
        };
    }
}
