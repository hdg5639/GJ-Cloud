package gj.cloud.ops.application.preview.analysis;

import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Capability start = commandCapability("vms.start", "vms", "start");
        Capability portsList = capabilityWithPath("ports.list", "ports", CapabilityType.LIST,
                "/vms/{vmId}/ports", "GET");
        Capability portsCreate = capabilityWithPath("ports.create", "ports", CapabilityType.CREATE,
                "/vms/{vmId}/ports", "POST");
        List<Capability> allCapabilities =
                List.of(login, list, detail, create, update, delete, tagsList, start, portsList, portsCreate);

        List<PageDraft> pages = List.of(
                new PageDraft("dashboard", "대시보드", PageSkeletonType.DASHBOARD,
                        List.of("vms.list", "tags.list")),
                new PageDraft("auth-login", "로그인", PageSkeletonType.AUTH_PAGE,
                        List.of("auth.login")),
                new PageDraft("vms-page", "Vms", PageSkeletonType.LIST_DETAIL,
                        List.of("vms.list", "vms.detail", "vms.create", "vms.update", "vms.delete", "vms.start",
                                "ports.list", "ports.create"))
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
            Capability capability = allCapabilities.stream()
                    .filter(c -> c.id().equals(capabilityId))
                    .findFirst()
                    .orElseThrow();
            if (capability.kind() == CapabilityKind.COMMAND) {
                assertThat(contract.acceptedCapabilityKinds())
                        .withFailMessage("%s는 COMMAND kind를 받지 않음", block.componentId())
                        .contains(CapabilityKind.COMMAND);
            } else {
                assertThat(contract.acceptedCapabilityTypes())
                        .withFailMessage("%s는 %s 타입을 받지 않음", block.componentId(), capability.type())
                        .contains(capability.type());
            }
        }
    }

    private Capability capabilityWithPath(String id, String resourceName, CapabilityType type,
                                                  String path, String method) {
        return new Capability(id, resourceName, type, null, path, method,
                false, false, false, "HIGH", List.of(), List.of(), null, null,
                type == CapabilityType.CREATE ? RiskLevel.STATE_CHANGING : RiskLevel.SAFE,
                type == CapabilityType.CREATE ? AutomationPolicy.USER_INITIATED : AutomationPolicy.AUTO_SAFE,
                null, null, kindOf(type), null, List.of());
    }

    private Capability commandCapability(String id, String resourceName, String action) {
        return new Capability(id, resourceName, null, null, "/" + resourceName + "/{id}/" + action, "POST",
                false, false, false, "HIGH", List.of(), List.of(), null, null,
                RiskLevel.STATE_CHANGING, AutomationPolicy.USER_INITIATED, null, null,
                CapabilityKind.COMMAND, action, List.of());
    }

    // Workflow Composition Phase 2 Change Request §13 WP-5 — BlueprintCompiler가 family/
    // preferredPurposes만 보고 결정론적으로 유일한 Variant를 고를 수 있으려면, family마다 정확히
    // 하나는 "기본값"(preferredPurposes 빈 리스트)이어야 하고 같은 purpose를 두 Variant가 동시에
    // 선호하면 안 된다(그러면 Map.values() 순회 순서에 선택이 좌우돼 결정론이 깨짐, AC-9와 직결).
    @Test
    void everyComponentFamilyHasExactlyOneDefaultAndNoAmbiguousPurposePreference() {
        Map<String, List<ComponentContract>> byFamily = new LinkedHashMap<>();
        for (ComponentContract contract : ComponentContracts.ALL.values()) {
            if (contract.family() == null) {
                continue;
            }
            byFamily.computeIfAbsent(contract.family(), f -> new ArrayList<>()).add(contract);
        }

        assertThat(byFamily).isNotEmpty();
        for (Map.Entry<String, List<ComponentContract>> entry : byFamily.entrySet()) {
            String family = entry.getKey();
            List<ComponentContract> members = entry.getValue();

            long defaultCount = members.stream().filter(c -> c.preferredPurposes().isEmpty()).count();
            assertThat(defaultCount).as("family '%s'는 정확히 하나의 기본값(preferredPurposes 빈 리스트)을 가져야 함", family)
                    .isEqualTo(1);

            for (Purpose purpose : Purpose.values()) {
                long matches = members.stream().filter(c -> c.preferredPurposes().contains(purpose)).count();
                assertThat(matches).as("family '%s'에서 purpose %s를 선호하는 Variant는 최대 하나여야 함", family, purpose)
                        .isLessThanOrEqualTo(1);
            }
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
