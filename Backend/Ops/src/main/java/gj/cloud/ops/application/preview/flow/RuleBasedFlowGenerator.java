package gj.cloud.ops.application.preview.flow;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.binding.ApiBindingValidator;
import gj.cloud.ops.application.preview.planning.model.NavigationRule;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Workflow Composition Phase 2의 결정론적 Flow 생성기. PagePlan의 create.success/row.select
// Navigation과 COMMAND dependency를 이용해 CREATE 결과 연결·상세 이동·명령 후 refresh를 만든다.
// AI의 ADD_FLOW/ASSIGN_FLOW와 함께 동작하지만, OpenAPI만으로 확정할 수 없는 상태 필드·완료 값은
// 추측하지 않는다. 따라서 규칙 기반 POLL은 생성하지 않고 사용자가 승인한 Flow 또는 명시적 Binding에
// 한해서 Runtime이 bounded polling을 실행한다.
@Component
public class RuleBasedFlowGenerator {

    private static final Pattern PATH_PARAM = Pattern.compile("\\{([^}]+)}");

    public record Result(List<FlowBlueprint> flows, List<ApiBinding> bindings) {
    }

    public record ValidatedResult(Result result, List<String> errors) {
    }

    // generate()가 만든 결과를 응답에 싣기 전 마지막 안전장치로 한 번 더 검증한다(§16 안전 폴백과
    // 동일 원칙) — 하나라도 실패하면 전체를 비운 Result를 반환한다(부분 드랍은 하지 않음: refresh
    // 바인딩이 여러 flow에 공유될 수 있어 "이 flow만 빼고 바인딩은 유지"가 항상 안전하지 않기 때문).
    // 호출 측(PreviewAnalysisService/PreviewController)이 반복 구현하지 않도록 이 클래스가 직접 제공한다.
    public ValidatedResult generateValidated(List<PagePlan> pages, List<Capability> capabilities) {
        List<PagePlan> safePages = pages == null ? List.of() : pages;
        List<Capability> safeCapabilities = capabilities == null ? List.of() : capabilities;
        Result raw = generate(safePages, safeCapabilities);
        Set<String> knownPageIds = safePages.stream().filter(Objects::nonNull)
                .map(PagePlan::id).filter(Objects::nonNull).collect(Collectors.toSet());

        List<String> errors = new ArrayList<>();
        for (FlowBlueprint flow : raw.flows()) {
            errors.addAll(FlowBlueprintValidator.validate(flow, knownPageIds));
        }
        errors.addAll(ApiBindingValidator.validate(raw.bindings(), safeCapabilities));

        if (!errors.isEmpty()) {
            return new ValidatedResult(new Result(List.of(), List.of()), errors);
        }
        return new ValidatedResult(raw, List.of());
    }

    public Result generate(List<PagePlan> pages, List<Capability> capabilities) {
        pages = pages == null ? List.of() : pages;
        capabilities = capabilities == null ? List.of() : capabilities;
        Map<String, Capability> capabilityById = new LinkedHashMap<>();
        for (Capability capability : capabilities) {
            if (capability != null && capability.id() != null) {
                capabilityById.putIfAbsent(capability.id(), capability);
            }
        }

        List<FlowBlueprint> flows = new ArrayList<>();
        // capabilityId -> ApiBinding, "새로고침 전용"(outputMappings 없는) 바인딩을 재사용하기 위한
        // 누적 맵. 한 페이지에 vm.start/vm.stop처럼 같은 dependency(vm.detail)를 공유하는 COMMAND가
        // 여럿이면 각자 새 ApiBinding을 만들지 않고 이 맵에서 같은 것을 재사용한다 — 안 그러면 같은
        // id("vm.detail-binding")를 가진 ApiBinding이 중복 생성된다.
        Map<String, ApiBinding> refreshBindingsByCapabilityId = new LinkedHashMap<>();
        List<ApiBinding> ownBindings = new ArrayList<>();

        for (PagePlan page : pages) {
            if (page == null) {
                continue;
            }
            List<Capability> pageCapabilities = page.capabilityIds().stream()
                    .map(capabilityById::get)
                    .filter(Objects::nonNull)
                    .toList();

            generateCreateFlow(page, pageCapabilities, pages).ifPresent(result -> {
                flows.add(result.flow());
                ownBindings.addAll(result.bindings());
            });

            for (Capability command : findCommands(pageCapabilities)) {
                generateCommandFlow(page, command, capabilityById, refreshBindingsByCapabilityId).ifPresent(result -> {
                    flows.add(result.flow());
                    ownBindings.add(result.bindings().get(0));
                });
            }
        }

        List<ApiBinding> bindings = new ArrayList<>(ownBindings);
        bindings.addAll(refreshBindingsByCapabilityId.values());
        return new Result(flows, bindings);
    }

    private record GeneratedFlow(FlowBlueprint flow, List<ApiBinding> bindings) {
    }

    private Optional<GeneratedFlow> generateCreateFlow(
            PagePlan page, List<Capability> pageCapabilities, List<PagePlan> allPages
    ) {
        Capability create = findByType(pageCapabilities, CapabilityType.CREATE);
        if (create == null) {
            return Optional.empty();
        }

        NavigationRule navigation = page.navigationRules().stream()
                .filter(Objects::nonNull)
                .filter(rule -> "create.success".equals(rule.trigger()))
                .findFirst()
                .or(() -> page.navigationRules().stream().filter(Objects::nonNull)
                        .filter(rule -> "row.select".equals(rule.trigger())).findFirst())
                .orElse(null);
        Capability localDetail = findByType(pageCapabilities, CapabilityType.DETAIL);
        if (localDetail == null && navigation == null) {
            return Optional.empty();
        }

        List<ApiBinding.OutputMapping> outputMappings = List.of(
                new ApiBinding.OutputMapping("data.id", "createdId"),
                new ApiBinding.OutputMapping("result.id", "createdId"),
                new ApiBinding.OutputMapping("payload.id", "createdId"),
                new ApiBinding.OutputMapping("id", "createdId")
        );
        ApiBinding createBinding = buildBinding(page, create, outputMappings, List.of());
        FlowStep callStep = new FlowStep("submit-create", FlowStepType.API_CALL, createBinding.id(),
                null, null, null, null, null, null, null, null, null);

        String targetPageId = navigation != null ? navigation.targetPageId() : page.id();
        PagePlan targetPage = allPages.stream().filter(Objects::nonNull)
                .filter(candidate -> Objects.equals(candidate.id(), targetPageId)).findFirst().orElse(page);
        Map<String, String> parameters = new LinkedHashMap<>();
        if (navigation != null && navigation.parameters() != null) {
            navigation.parameters().forEach((key, value) ->
                    parameters.put(key, value != null && value.startsWith("$row.") ? "$context.createdId" : value));
        }
        if (parameters.isEmpty()) {
            String parameterName = targetPage.routeParameters().isEmpty()
                    ? "selected"
                    : targetPage.routeParameters().get(0).name();
            parameters.put(parameterName, "$context.createdId");
        }

        FlowStep navigateStep = new FlowStep("open-created-resource", FlowStepType.NAVIGATE, null,
                null, null, targetPageId, parameters, null, null, null, null, null);
        FlowBlueprint flow = new FlowBlueprint(page.id() + "-create-flow",
                new FlowBlueprint.FlowTrigger(page.id(), create.id()), List.of(callStep, navigateStep));
        return Optional.of(new GeneratedFlow(flow, List.of(createBinding)));
    }

    // 반환하는 GeneratedFlow.bindings()에는 커맨드 자신의 바인딩 하나만 담는다 — refresh 대상
    // 바인딩은 호출 측(generate)이 관리하는 refreshBindingsByCapabilityId 캐시에 직접 쌓는다.
    private Optional<GeneratedFlow> generateCommandFlow(PagePlan page, Capability command,
                                                          Map<String, Capability> capabilityById,
                                                          Map<String, ApiBinding> refreshBindingsByCapabilityId) {
        List<String> refreshBindingIds = new ArrayList<>();
        for (String dependencyId : command.dependencies()) {
            Capability dependency = capabilityById.get(dependencyId);
            if (dependency == null) {
                continue;
            }
            ApiBinding refreshBinding = refreshBindingsByCapabilityId.computeIfAbsent(dependencyId,
                    id -> buildBinding(page, dependency, List.of(), List.of()));
            refreshBindingIds.add(refreshBinding.id());
        }

        ApiBinding commandBinding = buildBinding(page, command, List.of(), refreshBindingIds);

        FlowStep callStep = new FlowStep("run-command", FlowStepType.API_CALL, commandBinding.id(),
                null, null, null, null, null, null, null, null, null);

        FlowBlueprint flow = new FlowBlueprint(
                page.id() + "-" + command.action() + "-flow",
                new FlowBlueprint.FlowTrigger(page.id(), command.id()),
                List.of(callStep));

        return Optional.of(new GeneratedFlow(flow, List.of(commandBinding)));
    }

    // capability.path()의 경로 파라미터마다 PATH InputMapping을, CREATE/UPDATE의 요청 본문 필드마다
    // BODY InputMapping을 만든다. 독립 상세 페이지에서는 PagePlan.routeParameters를 실제 source로
    // 사용하고, LIST_DETAIL의 기존 선택행 관례만 $route.selected로 폴백한다. OpenAPI의 {vmId}와
    // PagePlan의 :id처럼 이름이 달라도 페이지 route parameter가 하나뿐이면 그 값을 사용한다.
    // 여러 route parameter가 있는데 이름까지 다르면 안전하게 추론할 수 없으므로 $route.selected로
    // 폴백하고, 최종 Binding 검토/Override에서 명시적으로 고치게 한다.
    private ApiBinding buildBinding(PagePlan page, Capability capability,
                                     List<ApiBinding.OutputMapping> outputMappings,
                                     List<String> refreshBindingIds) {
        List<ApiBinding.InputMapping> inputMappings = new ArrayList<>();

        Matcher matcher = PATH_PARAM.matcher(capability.path() == null ? "" : capability.path());
        while (matcher.find()) {
            String paramName = matcher.group(1);
            inputMappings.add(new ApiBinding.InputMapping(paramName, ApiBinding.InputMapping.InputTarget.PATH,
                    resolveRouteSource(page, paramName)));
        }

        if (capability.type() == CapabilityType.CREATE || capability.type() == CapabilityType.UPDATE) {
            for (String field : capability.fields()) {
                inputMappings.add(new ApiBinding.InputMapping(field, ApiBinding.InputMapping.InputTarget.BODY,
                        "$form." + field));
            }
        }

        return new ApiBinding(capability.id() + "-binding", capability.id(), inputMappings, outputMappings,
                refreshBindingIds);
    }


    private String resolveRouteSource(PagePlan page, String capabilityPathParameter) {
        if (page != null && page.routeParameters() != null && !page.routeParameters().isEmpty()) {
            List<String> routeNames = page.routeParameters().stream()
                    .filter(Objects::nonNull)
                    .map(parameter -> parameter.name())
                    .filter(Objects::nonNull)
                    .toList();
            Optional<String> exact = routeNames.stream()
                    .filter(capabilityPathParameter::equals)
                    .findFirst();
            if (exact.isPresent()) {
                return "$route." + exact.get();
            }
            if (routeNames.size() == 1) {
                return "$route." + routeNames.get(0);
            }
        }
        return "$route.selected";
    }

    private Capability findByType(List<Capability> capabilities, CapabilityType type) {
        return capabilities.stream().filter(Objects::nonNull)
                .filter(c -> c.type() == type).findFirst().orElse(null);
    }

    private List<Capability> findCommands(List<Capability> capabilities) {
        return capabilities.stream().filter(Objects::nonNull)
                .filter(c -> c.kind() == CapabilityKind.COMMAND).toList();
    }
}
