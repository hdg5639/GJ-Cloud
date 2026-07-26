package gj.cloud.ops.application.preview.flow;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.binding.ApiBindingValidator;
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

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §22 7번(수직 슬라이스)로 가는
// 첫 조각. WP-4(AiPagePlanner의 ADD_FLOW/ASSIGN_FLOW)는 아직 없어 지금은 이 규칙기반 생성기만
// FlowBlueprint+ApiBinding을 실제로 만들어낸다 — RuleBasedPagePlanGenerator와 같은 관례로, purpose
// 해석이나 서비스 설명 이해 없이 OpenAPI로부터 결정론적으로 알 수 있는 패턴만 다룬다.
//
// 생성하는 패턴 2가지(둘 다 §15 수직 슬라이스 시나리오에 필요):
// 1) 같은 페이지에 CREATE+DETAIL이 모두 있으면: 생성 성공 후 새로 만든 행을 같은 페이지 안에서 선택
//    상태로 만든다(Navigation 증분이 세운 "?selected=<id>" 관례를 재사용 — LIST_DETAIL은 목록+상세가
//    한 페이지라 별도 상세 라우트가 없고, NAVIGATE의 pageId는 항상 자기 자신).
// 2) COMMAND(kind=COMMAND, 예: vm.start)는 Capability.dependencies가 이미 가리키는 대상(보통
//    DETAIL)을 실행 후 새로고침한다 — dependencies는 CapabilityExtractor가 이미 채워둔 필드라
//    여기서 새로 추론하지 않는다.
//
// 의도적으로 POLL step은 만들지 않는다 — Capability 모델에 응답의 "상태" 필드 이름이나 종료 값이
// 전혀 없어서(LIST의 collectionPath/totalCountPath 같은 필드가 DETAIL 응답에는 없음), 결정론적
// 생성기가 폴링 종료 조건을 지어내면 §12 "AI는 API 오퍼레이션을 지어내면 안 된다"는 원칙과 같은
// 이유로 위험하다. AC-4(bounded polling)는 상태 필드 휴리스틱 추출이나 AI Planner가 생기는 다음
// 증분의 몫으로 명시적으로 미룬다.
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
        Result raw = generate(pages, capabilities);
        Set<String> knownPageIds = pages.stream().map(PagePlan::id).collect(Collectors.toSet());

        List<String> errors = new ArrayList<>();
        for (FlowBlueprint flow : raw.flows()) {
            errors.addAll(FlowBlueprintValidator.validate(flow, knownPageIds));
        }
        errors.addAll(ApiBindingValidator.validate(raw.bindings(), capabilities));

        if (!errors.isEmpty()) {
            return new ValidatedResult(new Result(List.of(), List.of()), errors);
        }
        return new ValidatedResult(raw, List.of());
    }

    public Result generate(List<PagePlan> pages, List<Capability> capabilities) {
        Map<String, Capability> capabilityById = new LinkedHashMap<>();
        for (Capability capability : capabilities) {
            capabilityById.put(capability.id(), capability);
        }

        List<FlowBlueprint> flows = new ArrayList<>();
        // capabilityId -> ApiBinding, "새로고침 전용"(outputMappings 없는) 바인딩을 재사용하기 위한
        // 누적 맵. 한 페이지에 vm.start/vm.stop처럼 같은 dependency(vm.detail)를 공유하는 COMMAND가
        // 여럿이면 각자 새 ApiBinding을 만들지 않고 이 맵에서 같은 것을 재사용한다 — 안 그러면 같은
        // id("vm.detail-binding")를 가진 ApiBinding이 중복 생성된다.
        Map<String, ApiBinding> refreshBindingsByCapabilityId = new LinkedHashMap<>();
        List<ApiBinding> ownBindings = new ArrayList<>();

        for (PagePlan page : pages) {
            List<Capability> pageCapabilities = page.capabilityIds().stream()
                    .map(capabilityById::get)
                    .filter(Objects::nonNull)
                    .toList();

            generateCreateFlow(page, pageCapabilities).ifPresent(result -> {
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

    private Optional<GeneratedFlow> generateCreateFlow(PagePlan page, List<Capability> pageCapabilities) {
        Capability create = findByType(pageCapabilities, CapabilityType.CREATE);
        Capability detail = findByType(pageCapabilities, CapabilityType.DETAIL);
        if (create == null || detail == null) {
            return Optional.empty();
        }

        ApiBinding createBinding = buildBinding(create, List.of(new ApiBinding.OutputMapping("data.id", "createdId")),
                List.of());

        FlowStep callStep = new FlowStep("submit-create", FlowStepType.API_CALL, createBinding.id(),
                null, null, null, null, null, null, null, null, null);
        FlowStep navigateStep = new FlowStep("select-created-row", FlowStepType.NAVIGATE, null,
                null, null, page.id(), Map.of("selected", "$context.createdId"),
                null, null, null, null, null);

        FlowBlueprint flow = new FlowBlueprint(
                page.id() + "-create-flow",
                new FlowBlueprint.FlowTrigger(page.id(), create.id()),
                List.of(callStep, navigateStep));

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
                    id -> buildBinding(dependency, List.of(), List.of()));
            refreshBindingIds.add(refreshBinding.id());
        }

        ApiBinding commandBinding = buildBinding(command, List.of(), refreshBindingIds);

        FlowStep callStep = new FlowStep("run-command", FlowStepType.API_CALL, commandBinding.id(),
                null, null, null, null, null, null, null, null, null);

        FlowBlueprint flow = new FlowBlueprint(
                page.id() + "-" + command.action() + "-flow",
                new FlowBlueprint.FlowTrigger(page.id(), command.id()),
                List.of(callStep));

        return Optional.of(new GeneratedFlow(flow, List.of(commandBinding)));
    }

    // capability.path()의 경로 파라미터마다 PATH InputMapping을, CREATE/UPDATE의 요청 본문 필드마다
    // BODY InputMapping을 만든다. 경로 파라미터 값의 출처는 항상 "현재 선택된 행"(Navigation 증분이
    // 세운 "?selected=<id>" 관례, $route.selected)으로 가정한다 — 중첩 리소스(경로 중간에 다른
    // {orgId} 같은 파라미터가 더 있는 경우)는 프론트 replaceLastPathPlaceholder와 동일하게 아직
    // 지원하지 않는 알려진 제약이다.
    private ApiBinding buildBinding(Capability capability, List<ApiBinding.OutputMapping> outputMappings,
                                     List<String> refreshBindingIds) {
        List<ApiBinding.InputMapping> inputMappings = new ArrayList<>();

        Matcher matcher = PATH_PARAM.matcher(capability.path());
        while (matcher.find()) {
            String paramName = matcher.group(1);
            inputMappings.add(new ApiBinding.InputMapping(paramName, ApiBinding.InputMapping.InputTarget.PATH,
                    "$route.selected"));
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

    private Capability findByType(List<Capability> capabilities, CapabilityType type) {
        return capabilities.stream().filter(c -> c.type() == type).findFirst().orElse(null);
    }

    private List<Capability> findCommands(List<Capability> capabilities) {
        return capabilities.stream().filter(c -> c.kind() == CapabilityKind.COMMAND).toList();
    }
}
