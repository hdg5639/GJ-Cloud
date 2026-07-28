package gj.cloud.ops.application.preview.binding;

import gj.cloud.ops.application.preview.analysis.AutomationPolicy;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiBindingValidatorTest {

    private final Capability createVm = capability("vms.create", "vms", CapabilityType.CREATE, "/vms", "POST");
    private final Capability detailVm = capability("vms.detail", "vms", CapabilityType.DETAIL, "/vms/{vmId}", "GET");

    // §8 예시(response.data.id→context.vmId, response.data.operationId→context.operationId)를 그대로
    // 구성한 happy path — 이 모델이 문서가 원하는 시나리오를 실제로 표현할 수 있다는 증거.
    @Test
    void section8ExampleShapedBindingsHaveNoErrors() {
        ApiBinding create = new ApiBinding("vm-create-binding", "vms.create",
                List.of(new ApiBinding.InputMapping("body", ApiBinding.InputMapping.InputTarget.BODY, "$form")),
                List.of(
                        new ApiBinding.OutputMapping("data.id", "vmId"),
                        new ApiBinding.OutputMapping("data.operationId", "operationId")
                ),
                List.of());
        ApiBinding detail = new ApiBinding("vm-detail-binding", "vms.detail",
                List.of(new ApiBinding.InputMapping("vmId", ApiBinding.InputMapping.InputTarget.PATH, "$context.vmId")),
                List.of(),
                List.of());

        assertThat(ApiBindingValidator.validate(List.of(create, detail), List.of(createVm, detailVm))).isEmpty();
    }

    @Test
    void missingPathParameterMappingIsAnError() {
        ApiBinding detail = new ApiBinding("vm-detail-binding", "vms.detail", List.of(), List.of(), List.of());

        assertThat(ApiBindingValidator.validate(List.of(detail), List.of(detailVm)))
                .anyMatch(e -> e.contains("vmId") && e.contains("PATH inputMapping이 없음"));
    }

    @Test
    void outputMappingToSensitiveFieldNameIsRejected() {
        ApiBinding create = new ApiBinding("vm-create-binding", "vms.create", List.of(),
                List.of(new ApiBinding.OutputMapping("data.accessToken", "token")), List.of());

        assertThat(ApiBindingValidator.validate(List.of(create), List.of(createVm)))
                .anyMatch(e -> e.contains("민감한 필드"));
    }

    @Test
    void refreshBindingCycleIsDetected() {
        ApiBinding a = new ApiBinding("binding-a", "vms.create", List.of(), List.of(), List.of("binding-b"));
        ApiBinding b = new ApiBinding("binding-b", "vms.create", List.of(), List.of(), List.of("binding-a"));

        assertThat(ApiBindingValidator.validate(List.of(a, b), List.of(createVm)))
                .anyMatch(e -> e.contains("순환"));
    }

    @Test
    void unknownCapabilityIdAndRefreshTargetAreErrors() {
        ApiBinding binding = new ApiBinding("binding-1", "unknown.capability", List.of(), List.of(),
                List.of("unknown-binding"));

        List<String> errors = ApiBindingValidator.validate(List.of(binding), List.of(createVm));

        assertThat(errors).anyMatch(e -> e.contains("존재하지 않는 capabilityId"));
        assertThat(errors).anyMatch(e -> e.contains("존재하지 않는 refreshBindingIds 대상"));
    }

    @Test
    void duplicateBindingIdsAreRejected() {
        ApiBinding first = new ApiBinding("duplicate", "vms.create", List.of(), List.of(), List.of());
        ApiBinding second = new ApiBinding("duplicate", "vms.create", List.of(), List.of(), List.of());

        assertThat(ApiBindingValidator.validate(List.of(first, second), List.of(createVm)))
                .anyMatch(error -> error.contains("중복된 binding id"));
    }

    @Test
    void duplicateInputTargetsAndBlankSourcesAreRejected() {
        ApiBinding binding = new ApiBinding("binding-1", "vms.create", List.of(
                new ApiBinding.InputMapping("name", ApiBinding.InputMapping.InputTarget.BODY, "$form.name"),
                new ApiBinding.InputMapping("name", ApiBinding.InputMapping.InputTarget.BODY, "")
        ), List.of(), List.of());

        List<String> errors = ApiBindingValidator.validate(List.of(binding), List.of(createVm));

        assertThat(errors).anyMatch(error -> error.contains("중복된 inputMapping target"));
        assertThat(errors).anyMatch(error -> error.contains("from이 비어있음"));
    }

    @Test
    void disallowedExpressionSyntaxInInputMappingIsAnError() {
        ApiBinding binding = new ApiBinding("binding-1", "vms.create",
                List.of(new ApiBinding.InputMapping("body", ApiBinding.InputMapping.InputTarget.BODY,
                        "$form.name.toUpperCase()")),
                List.of(), List.of());

        assertThat(ApiBindingValidator.validate(List.of(binding), List.of(createVm)))
                .anyMatch(e -> e.contains("허용되지 않는 표현식"));
    }

    @Test
    void malformedOutputMappingDotPathIsAnError() {
        ApiBinding binding = new ApiBinding("binding-1", "vms.create", List.of(),
                List.of(new ApiBinding.OutputMapping("$data.id", "vmId")), List.of());

        assertThat(ApiBindingValidator.validate(List.of(binding), List.of(createVm)))
                .anyMatch(e -> e.contains("유효한 점경로가 아님"));
    }

    private Capability capability(String id, String resourceName, CapabilityType type, String path, String method) {
        return new Capability(id, resourceName, type, null, path, method,
                false, false, false, "HIGH", List.of(), List.of(), null, null,
                RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null,
                CapabilityKind.MUTATION, null, List.of());
    }
}
