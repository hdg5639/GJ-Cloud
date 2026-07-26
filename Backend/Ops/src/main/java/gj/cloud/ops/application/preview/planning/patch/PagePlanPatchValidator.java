package gj.cloud.ops.application.preview.planning.patch;

import gj.cloud.ops.application.preview.ai.PagePlanOperation;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.binding.ApiBindingValidator;
import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.flow.FlowBlueprintValidator;
import gj.cloud.ops.application.preview.flow.FlowExpression;
import gj.cloud.ops.application.preview.flow.FlowStep;
import gj.cloud.ops.application.preview.layout.LayoutBlueprints;
import gj.cloud.ops.application.preview.planning.model.NavigationRule;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import gj.cloud.ops.application.preview.planning.model.PageType;
import gj.cloud.ops.application.preview.planning.model.RouteParameter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Workflow Composition Phase 2 WP-4의 정본 Patch 엔진.
//
// propose 단계에서는 operation을 순서대로 구조 검증만 한다. ADD_PAGE→MOVE_CAPABILITY,
// ADD_FLOW→ASSIGN_FLOW처럼 중간 상태만 보면 미완성인 정상 조합을 허용하기 위함이다.
// apply 단계에서는 모든 선택 operation을 적용한 뒤 페이지/네비게이션/Flow/Binding 전체를 최종 검증해
// 빈 페이지·미할당 Flow·깨진 참조가 배포 상태로 남지 못하게 한다.
public final class PagePlanPatchValidator {

    public static final int MAX_PAGES = 30;
    public static final int MAX_FLOWS = 50;
    public static final int MAX_OPERATIONS = 50;
    public static final int MAX_NAVIGATION_RULES_PER_PAGE = 20;

    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$");
    private static final Pattern CAPABILITY_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$");
    private static final Pattern FEATURE_KEY = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{0,79}$");
    private static final Pattern ROUTE = Pattern.compile("^/[A-Za-z0-9_:/.-]*$");
    private static final Pattern ROUTE_PLACEHOLDER = Pattern.compile(":([A-Za-z][A-Za-z0-9_-]{0,79})");
    private static final Set<String> SUPPORTED_NAVIGATION_TRIGGERS = Set.of(
            "row.select", "create.success", "command.success", "action.click"
    );

    public static PagePlanPatchApplyResult previewOperation(
            PlanPatchState state,
            List<Capability> capabilities,
            PagePlanOperation operation
    ) {
        List<String> inputErrors = validatePatchInput(state);
        if (!inputErrors.isEmpty()) {
            return new PagePlanPatchApplyResult(state, List.of(), inputErrors);
        }
        MutableState working = MutableState.from(state);
        List<String> errors = new ArrayList<>();
        List<String> decisions = new ArrayList<>();
        applyOperation(working, capabilities, operation, errors, decisions);
        if (!errors.isEmpty()) {
            return new PagePlanPatchApplyResult(state, List.of(), errors);
        }
        return new PagePlanPatchApplyResult(working.freeze(), decisions, List.of());
    }

    public static PagePlanPatchApplyResult apply(
            PlanPatchState original,
            List<Capability> capabilities,
            List<PagePlanOperation> operations
    ) {
        List<String> inputErrors = validatePatchInput(original);
        if (!inputErrors.isEmpty()) {
            return new PagePlanPatchApplyResult(original, List.of(), inputErrors);
        }
        if (operations == null) {
            operations = List.of();
        }
        if (operations.size() > MAX_OPERATIONS) {
            return new PagePlanPatchApplyResult(original, List.of(),
                    List.of("operation 개수가 상한(" + MAX_OPERATIONS + ")을 초과함: " + operations.size()));
        }

        MutableState working = MutableState.from(original);
        List<String> errors = new ArrayList<>();
        List<String> decisions = new ArrayList<>();
        for (PagePlanOperation operation : operations) {
            if (operation == null || operation.type() == null) {
                errors.add("operation 또는 operation.type이 비어있음");
                continue;
            }
            applyOperation(working, capabilities, operation, errors, decisions);
        }
        if (!errors.isEmpty()) {
            return new PagePlanPatchApplyResult(original, List.of(), errors);
        }

        PlanPatchState result = working.freeze();
        errors.addAll(validateFinal(result, capabilities));
        if (!errors.isEmpty()) {
            return new PagePlanPatchApplyResult(original, List.of(), errors);
        }
        return new PagePlanPatchApplyResult(result, List.copyOf(decisions), List.of());
    }

    public static List<String> validateFinal(PlanPatchState state, List<Capability> capabilities) {
        List<String> errors = new ArrayList<>();
        if (state == null) {
            return List.of("PlanPatchState가 null임");
        }
        List<PagePlan> pages = state.pagePlans();
        List<FlowBlueprint> flows = state.flows();
        List<ApiBinding> bindings = state.bindings();

        if (pages.isEmpty()) {
            errors.add("PagePlan이 하나도 없음");
            return errors;
        }
        if (pages.size() > MAX_PAGES) {
            errors.add("페이지 개수가 상한(" + MAX_PAGES + ")을 초과함: " + pages.size());
        }
        if (flows.size() > MAX_FLOWS) {
            errors.add("Flow 개수가 상한(" + MAX_FLOWS + ")을 초과함: " + flows.size());
        }

        Map<String, Capability> capabilityById = new LinkedHashMap<>();
        if (capabilities == null) {
            errors.add("Capability 목록이 null임");
        } else {
            for (Capability capability : capabilities) {
                if (capability == null) {
                    errors.add("null Capability는 허용되지 않음");
                } else if (!CAPABILITY_ID.matcher(capability.id()).matches()) {
                    errors.add("유효하지 않은 capability id: " + capability.id());
                } else if (capabilityById.putIfAbsent(capability.id(), capability) != null) {
                    errors.add("중복된 capability id: " + capability.id());
                }
            }
        }
        Set<String> pageIds = new LinkedHashSet<>();
        Set<String> routes = new LinkedHashSet<>();
        Map<String, Integer> nonDashboardAssignments = new HashMap<>();

        for (PagePlan page : pages) {
            if (page == null) {
                errors.add("null PagePlan은 허용되지 않음");
                continue;
            }
            if (!validId(page.id())) {
                errors.add("유효하지 않은 page id: " + page.id());
            } else if (!pageIds.add(page.id())) {
                errors.add("중복된 page id: " + page.id());
            }
            if (page.title() == null || page.title().isBlank()) {
                errors.add(page.id() + ": 페이지 제목이 비어있음");
            }
            if (page.route() == null || !ROUTE.matcher(page.route()).matches()) {
                errors.add(page.id() + ": 유효하지 않은 route(" + page.route() + ")");
            } else if (!routes.add(page.route())) {
                errors.add("중복된 route: " + page.route());
            }
            if (page.pageType() == null) {
                errors.add(page.id() + ": pageType이 비어있음");
            }
            errors.addAll(validateLayout(page));
            for (Map.Entry<String, Boolean> feature : safeMap(page.features()).entrySet()) {
                if (feature.getKey() == null || !FEATURE_KEY.matcher(feature.getKey()).matches()) {
                    errors.add(page.id() + ": 유효하지 않은 feature key(" + feature.getKey() + ")");
                }
                if (feature.getValue() == null) {
                    errors.add(page.id() + ": feature 값이 null임(" + feature.getKey() + ")");
                }
            }
            Set<String> routeParameterNames = new LinkedHashSet<>();
            for (RouteParameter parameter : safeList(page.routeParameters())) {
                if (parameter == null || !validId(parameter.name())) {
                    errors.add(page.id() + ": 유효하지 않은 route parameter(" + (parameter == null ? null : parameter.name()) + ")");
                    continue;
                }
                if (!routeParameterNames.add(parameter.name())) {
                    errors.add(page.id() + ": 중복된 route parameter(" + parameter.name() + ")");
                }
                if (page.route() == null || !page.route().contains(":" + parameter.name())) {
                    errors.add(page.id() + ": route에 선언되지 않은 route parameter(" + parameter.name() + ")");
                }
            }
            if (page.route() != null) {
                var placeholderMatcher = ROUTE_PLACEHOLDER.matcher(page.route());
                Set<String> placeholders = new LinkedHashSet<>();
                while (placeholderMatcher.find()) {
                    String placeholder = placeholderMatcher.group(1);
                    if (!placeholders.add(placeholder)) {
                        errors.add(page.id() + ": route placeholder가 중복됨(" + placeholder + ")");
                    }
                    if (!routeParameterNames.contains(placeholder)) {
                        errors.add(page.id() + ": route placeholder에 대응하는 RouteParameter가 없음(" + placeholder + ")");
                    }
                }
            }

            List<String> capabilityIds = safeList(page.capabilityIds());
            if (!isProtected(page) && capabilityIds.isEmpty()) {
                errors.add(page.id() + ": 배포 가능한 리소스 페이지는 capability가 하나 이상 필요함");
            }
            for (String capabilityId : capabilityIds) {
                if (!capabilityById.containsKey(capabilityId)) {
                    errors.add(page.id() + ": 존재하지 않는 capabilityId(" + capabilityId + ")");
                }
                if (page.pageType() != PageType.DASHBOARD) {
                    nonDashboardAssignments.merge(capabilityId, 1, Integer::sum);
                }
            }

            List<NavigationRule> rules = safeList(page.navigationRules());
            if (rules.size() > MAX_NAVIGATION_RULES_PER_PAGE) {
                errors.add(page.id() + ": navigation rule 개수가 상한(" + MAX_NAVIGATION_RULES_PER_PAGE + ") 초과");
            }
            Set<String> navigationTriggers = new LinkedHashSet<>();
            for (NavigationRule rule : rules) {
                if (rule != null && rule.trigger() != null && !navigationTriggers.add(rule.trigger())) {
                    errors.add(page.id() + ": 같은 trigger의 navigation rule이 중복됨(" + rule.trigger() + ")");
                }
            }
        }

        for (Map.Entry<String, Integer> assignment : nonDashboardAssignments.entrySet()) {
            if (assignment.getValue() > 1) {
                errors.add("capability가 여러 일반 페이지에 중복 배치됨: " + assignment.getKey());
            }
        }
        for (String capabilityId : capabilityById.keySet()) {
            if (nonDashboardAssignments.getOrDefault(capabilityId, 0) == 0) {
                errors.add("capability가 어떤 실행 페이지에도 배치되지 않음: " + capabilityId);
            }
        }

        for (PagePlan page : pages) {
            if (page == null) {
                continue;
            }
            for (NavigationRule rule : safeList(page.navigationRules())) {
                errors.addAll(validateNavigation(rule, page.id(), pageIds));
                if (rule != null && rule.type() != NavigationRule.NavigationType.GO_BACK) {
                    PagePlan target = findPage(pages, rule.targetPageId());
                    if (target != null) {
                        Set<String> supplied = safeMap(rule.parameters()).keySet();
                        for (RouteParameter parameter : safeList(target.routeParameters())) {
                            if (!supplied.contains(parameter.name())) {
                                errors.add(page.id() + ": navigation이 대상 route parameter를 제공하지 않음("
                                        + target.id() + "/" + parameter.name() + ")");
                            }
                        }
                    }
                }
            }
        }

        Set<String> flowIds = new LinkedHashSet<>();
        Set<String> flowTriggerKeys = new LinkedHashSet<>();
        Map<String, ApiBinding> bindingById = new LinkedHashMap<>();
        for (ApiBinding binding : bindings) {
            if (binding != null && binding.id() != null) {
                bindingById.putIfAbsent(binding.id(), binding);
            }
        }
        Set<String> bindingIds = bindingById.keySet();
        for (FlowBlueprint flow : flows) {
            if (flow == null || !validId(flow.id())) {
                errors.add("유효하지 않은 flow id: " + (flow == null ? null : flow.id()));
                continue;
            }
            if (!flowIds.add(flow.id())) {
                errors.add("중복된 flow id: " + flow.id());
            }
            if (flow.trigger() == null || flow.trigger().pageId() == null || flow.trigger().actionId() == null
                    || flow.trigger().actionId().isBlank()) {
                errors.add(flow.id() + ": Flow가 페이지 액션에 할당되지 않음(ASSIGN_FLOW 필요)");
            } else {
                PagePlan page = findPage(pages, flow.trigger().pageId());
                if (page == null) {
                    errors.add(flow.id() + ": trigger.pageId가 존재하지 않음(" + flow.trigger().pageId() + ")");
                } else if (!page.capabilityIds().contains(flow.trigger().actionId())) {
                    errors.add(flow.id() + ": trigger.actionId가 해당 페이지 capability가 아님(" + flow.trigger().actionId() + ")");
                } else {
                    String triggerKey = flow.trigger().pageId() + "::" + flow.trigger().actionId();
                    if (!flowTriggerKeys.add(triggerKey)) {
                        errors.add("같은 페이지 액션에 여러 Flow가 할당됨: " + triggerKey);
                    }
                }
            }
            errors.addAll(FlowBlueprintValidator.validate(flow, pageIds));
            for (FlowStep step : safeList(flow.steps())) {
                if (step == null) {
                    continue; // FlowBlueprintValidator가 사용자-facing 오류를 이미 추가한다.
                }
                if ((step.type() == gj.cloud.ops.application.preview.flow.FlowStepType.API_CALL
                        || step.type() == gj.cloud.ops.application.preview.flow.FlowStepType.REFRESH_BINDING
                        || step.type() == gj.cloud.ops.application.preview.flow.FlowStepType.POLL)
                        && step.bindingRef() != null) {
                    ApiBinding stepBinding = bindingById.get(step.bindingRef());
                    if (stepBinding == null) {
                        errors.add(flow.id() + "/" + step.id() + ": 존재하지 않는 bindingRef(" + step.bindingRef() + ")");
                    } else {
                        Capability stepCapability = capabilityById.get(stepBinding.capabilityId());
                        boolean directlyTriggered = flow.trigger() != null
                                && Objects.equals(flow.trigger().actionId(), stepBinding.capabilityId())
                                && step.type() == gj.cloud.ops.application.preview.flow.FlowStepType.API_CALL;
                        if (stepCapability != null && requiresExplicitTrigger(stepCapability.risk()) && !directlyTriggered) {
                            errors.add(flow.id() + "/" + step.id()
                                    + ": 파괴적·비가역·외부 부작용 capability를 자동 후속 step으로 실행할 수 없음("
                                    + stepCapability.id() + ")");
                        }
                    }
                }
            }
        }
        errors.addAll(ApiBindingValidator.validate(bindings, capabilities));
        return errors.stream().distinct().toList();
    }

    private static void applyOperation(MutableState state, List<Capability> capabilities, PagePlanOperation op,
                                       List<String> errors, List<String> decisions) {
        if (op == null || op.type() == null) {
            errors.add("operation 또는 operation.type이 비어있음");
            return;
        }
        switch (op.type()) {
            case RENAME_PAGE -> renamePage(state, op, errors, decisions);
            case MERGE_PAGES -> mergePages(state, capabilities, op, errors, decisions);
            case MOVE_CAPABILITY -> moveCapability(state, capabilities, op, errors, decisions);
            case ADD_PAGE -> addPage(state, op, errors, decisions);
            case REMOVE_PAGE -> removePage(state, op, errors, decisions);
            case SPLIT_PAGE -> splitPage(state, capabilities, op, errors, decisions);
            case SET_PAGE_TYPE -> setPageType(state, op, errors, decisions);
            case SET_LAYOUT -> setLayout(state, op, errors, decisions);
            case SET_FEATURE -> setFeature(state, op, errors, decisions);
            case ADD_NAVIGATION -> addNavigation(state, op, errors, decisions);
            case ADD_FLOW -> addFlow(state, op, errors, decisions);
            case ASSIGN_FLOW -> assignFlow(state, op, errors, decisions);
        }
    }

    private static void renamePage(MutableState state, PagePlanOperation op, List<String> errors,
                                   List<String> decisions) {
        PagePlan page = state.pages.get(op.pageId());
        if (page == null) {
            errors.add("RENAME_PAGE: 존재하지 않는 pageId(" + op.pageId() + ")");
            return;
        }
        if (op.newTitle() == null || op.newTitle().isBlank()) {
            errors.add("RENAME_PAGE: 새 제목이 비어있음(" + op.pageId() + ")");
            return;
        }
        state.pages.put(page.id(), copyPage(page, op.newTitle(), null, null, null, null, null));
        decisions.add(reasonOrDefault(op, "\"" + page.title() + "\" → \"" + op.newTitle() + "\"로 이름 변경"));
    }

    private static void mergePages(MutableState state, List<Capability> capabilities, PagePlanOperation op,
                                   List<String> errors, List<String> decisions) {
        PagePlan target = state.pages.get(op.pageId());
        PagePlan other = state.pages.get(op.otherPageId());
        if (target == null || other == null) {
            errors.add("MERGE_PAGES: 존재하지 않는 pageId(" + op.pageId() + ", " + op.otherPageId() + ")");
            return;
        }
        if (Objects.equals(target.id(), other.id())) {
            errors.add("MERGE_PAGES: 같은 페이지를 병합할 수 없음(" + target.id() + ")");
            return;
        }
        if (isProtected(target) || isProtected(other)) {
            errors.add("MERGE_PAGES: AUTH/DASHBOARD 페이지는 병합할 수 없음");
            return;
        }

        List<String> merged = new ArrayList<>(safeList(target.capabilityIds()));
        for (String id : safeList(other.capabilityIds())) {
            if (!merged.contains(id)) {
                merged.add(id);
            }
        }
        List<NavigationRule> mergedRules = new ArrayList<>(safeList(target.navigationRules()));
        for (NavigationRule rule : safeList(other.navigationRules())) {
            mergedRules.add(rewriteNavigation(rule, other.id(), target.id()));
        }
        PagePlan mergedPage = withCapabilities(target, merged, capabilities);
        state.pages.put(target.id(), copyPage(mergedPage, null, null, null, null, mergedRules, null));
        state.pages.remove(other.id());
        rewritePageReferences(state, other.id(), target.id());
        decisions.add(reasonOrDefault(op, "\"" + other.title() + "\" 페이지를 \"" + target.title() + "\" 페이지로 병합"));
    }

    private static void moveCapability(MutableState state, List<Capability> capabilities, PagePlanOperation op,
                                       List<String> errors, List<String> decisions) {
        if (!capabilityMap(capabilities).containsKey(op.capabilityId())) {
            errors.add("MOVE_CAPABILITY: 존재하지 않는 capabilityId(" + op.capabilityId() + ")");
            return;
        }
        PagePlan destination = state.pages.get(op.destinationPageId());
        if (destination == null) {
            errors.add("MOVE_CAPABILITY: 존재하지 않는 destinationPageId(" + op.destinationPageId() + ")");
            return;
        }
        if (isProtected(destination)) {
            errors.add("MOVE_CAPABILITY: AUTH/DASHBOARD 페이지로는 이동할 수 없음(" + destination.id() + ")");
            return;
        }

        boolean found = false;
        for (PagePlan page : List.copyOf(state.pages.values())) {
            if (page.pageType() == PageType.DASHBOARD) {
                continue; // 대시보드 요약 참조는 원본 페이지 이동과 무관하게 유지한다.
            }
            if (safeList(page.capabilityIds()).contains(op.capabilityId())) {
                found = true;
                if (!page.id().equals(destination.id())) {
                    List<String> remaining = new ArrayList<>(page.capabilityIds());
                    remaining.remove(op.capabilityId());
                    state.pages.put(page.id(), withCapabilities(page, remaining, capabilities));
                }
            }
        }
        if (!found) {
            errors.add("MOVE_CAPABILITY: capability가 어떤 페이지에도 배치되어 있지 않음(" + op.capabilityId() + ")");
            return;
        }
        PagePlan refreshed = state.pages.get(destination.id());
        if (!safeList(refreshed.capabilityIds()).contains(op.capabilityId())) {
            List<String> updated = new ArrayList<>(safeList(refreshed.capabilityIds()));
            updated.add(op.capabilityId());
            state.pages.put(refreshed.id(), withCapabilities(refreshed, updated, capabilities));
        }
        decisions.add(reasonOrDefault(op, op.capabilityId() + "를 \"" + destination.title() + "\" 페이지로 이동"));
    }

    private static void addPage(MutableState state, PagePlanOperation op, List<String> errors,
                                List<String> decisions) {
        if (!validId(op.pageId())) {
            errors.add("ADD_PAGE: 유효하지 않은 pageId(" + op.pageId() + ")");
            return;
        }
        if (state.pages.containsKey(op.pageId())) {
            errors.add("ADD_PAGE: 이미 존재하는 pageId(" + op.pageId() + ")");
            return;
        }
        if (op.newTitle() == null || op.newTitle().isBlank()) {
            errors.add("ADD_PAGE: 제목이 비어있음(" + op.pageId() + ")");
            return;
        }
        PageType type = op.pageType() == null ? PageType.RESOURCE_LIST : op.pageType();
        if (type == PageType.AUTH || type == PageType.DASHBOARD) {
            errors.add("ADD_PAGE: AI Patch로 AUTH/DASHBOARD 페이지를 만들 수 없음");
            return;
        }
        String layout = op.layoutRef() == null ? defaultLayout(type) : op.layoutRef();
        PagePlan page = new PagePlan(op.pageId(), op.newTitle(), defaultRoute(op.pageId(), type), type, layout,
                List.of(), defaultRouteParameters(type), List.of(), List.of(), defaultFeatures(), "MEDIUM",
                reasonOrDefault(op, "AI 페이지 신설"), List.of());
        List<String> layoutErrors = validateLayout(page);
        if (!layoutErrors.isEmpty()) {
            errors.addAll(layoutErrors.stream().map(e -> "ADD_PAGE: " + e).toList());
            return;
        }
        state.pages.put(page.id(), page);
        decisions.add(reasonOrDefault(op, "\"" + op.newTitle() + "\" 페이지 신설"));
    }

    private static void removePage(MutableState state, PagePlanOperation op, List<String> errors,
                                   List<String> decisions) {
        PagePlan page = state.pages.get(op.pageId());
        if (page == null) {
            errors.add("REMOVE_PAGE: 존재하지 않는 pageId(" + op.pageId() + ")");
            return;
        }
        if (isProtected(page)) {
            errors.add("REMOVE_PAGE: AUTH/DASHBOARD 페이지는 삭제할 수 없음(" + page.id() + ")");
            return;
        }
        if (!safeList(page.capabilityIds()).isEmpty()) {
            errors.add("REMOVE_PAGE: capability가 남아있는 페이지는 삭제할 수 없음(" + page.id() + ")");
            return;
        }
        state.pages.remove(page.id());
        state.flows.removeIf(flow -> flow != null && flow.trigger() != null
                && page.id().equals(flow.trigger().pageId()));
        state.pages.replaceAll((id, p) -> copyPage(p, null, null, null, null,
                safeList(p.navigationRules()).stream()
                        .filter(rule -> !page.id().equals(rule.targetPageId()) && !page.id().equals(rule.sourcePageId()))
                        .toList(), null));
        decisions.add(reasonOrDefault(op, "\"" + page.title() + "\" 페이지 삭제"));
    }

    private static void splitPage(MutableState state, List<Capability> capabilities, PagePlanOperation op,
                                  List<String> errors, List<String> decisions) {
        PagePlan source = state.pages.get(op.pageId());
        if (source == null) {
            errors.add("SPLIT_PAGE: 존재하지 않는 원본 pageId(" + op.pageId() + ")");
            return;
        }
        if (isProtected(source)) {
            errors.add("SPLIT_PAGE: AUTH/DASHBOARD 페이지는 나눌 수 없음");
            return;
        }
        if (!validId(op.destinationPageId()) || state.pages.containsKey(op.destinationPageId())) {
            errors.add("SPLIT_PAGE: 신규 destinationPageId가 유효하지 않거나 이미 존재함(" + op.destinationPageId() + ")");
            return;
        }
        if (op.newTitle() == null || op.newTitle().isBlank()) {
            errors.add("SPLIT_PAGE: 신규 페이지 제목이 비어있음");
            return;
        }
        List<String> moving = op.capabilityIds() == null ? List.of() : op.capabilityIds().stream().distinct().toList();
        if (moving.isEmpty()) {
            errors.add("SPLIT_PAGE: 옮길 capabilityIds가 비어있음");
            return;
        }
        Set<String> known = capabilityMap(capabilities).keySet();
        for (String id : moving) {
            if (!known.contains(id)) {
                errors.add("SPLIT_PAGE: 존재하지 않는 capabilityId(" + id + ")");
            } else if (!source.capabilityIds().contains(id)) {
                errors.add("SPLIT_PAGE: 원본 페이지에 없는 capabilityId(" + id + ")");
            }
        }
        if (!errors.isEmpty()) {
            return;
        }

        List<String> remaining = new ArrayList<>(source.capabilityIds());
        remaining.removeAll(moving);
        state.pages.put(source.id(), withCapabilities(source, remaining, capabilities));

        PageType type = op.pageType() == null
                ? inferPageType(moving, PageType.RESOURCE_LIST, capabilityMap(capabilities))
                : op.pageType();
        String layout = op.layoutRef() == null ? defaultLayout(type) : op.layoutRef();
        PagePlan destination = new PagePlan(op.destinationPageId(), op.newTitle(),
                defaultRoute(op.destinationPageId(), type), type, layout, moving, defaultRouteParameters(type),
                List.of(), List.of(), defaultFeatures(), "MEDIUM",
                reasonOrDefault(op, "페이지 분리"), List.of());
        List<String> layoutErrors = validateLayout(destination);
        if (!layoutErrors.isEmpty()) {
            errors.addAll(layoutErrors.stream().map(e -> "SPLIT_PAGE: " + e).toList());
            return;
        }
        state.pages.put(destination.id(), destination);
        decisions.add(reasonOrDefault(op, "\"" + source.title() + "\"에서 \"" + destination.title() + "\" 페이지 분리"));
    }

    private static void setPageType(MutableState state, PagePlanOperation op, List<String> errors,
                                    List<String> decisions) {
        PagePlan page = state.pages.get(op.pageId());
        if (page == null) {
            errors.add("SET_PAGE_TYPE: 존재하지 않는 pageId(" + op.pageId() + ")");
            return;
        }
        if (isProtected(page)) {
            errors.add("SET_PAGE_TYPE: AUTH/DASHBOARD 페이지 타입은 변경할 수 없음");
            return;
        }
        if (op.pageType() == null || op.pageType() == PageType.AUTH || op.pageType() == PageType.DASHBOARD) {
            errors.add("SET_PAGE_TYPE: 허용되지 않거나 비어있는 pageType(" + op.pageType() + ")");
            return;
        }
        String layout = isLayoutCompatible(op.pageType(), page.layoutRef()) ? page.layoutRef() : defaultLayout(op.pageType());
        state.pages.put(page.id(), copyPageForType(page, op.pageType(), layout));
        decisions.add(reasonOrDefault(op, page.title() + "의 페이지 타입을 " + op.pageType() + "로 변경"));
    }

    private static void setLayout(MutableState state, PagePlanOperation op, List<String> errors,
                                  List<String> decisions) {
        PagePlan page = state.pages.get(op.pageId());
        if (page == null) {
            errors.add("SET_LAYOUT: 존재하지 않는 pageId(" + op.pageId() + ")");
            return;
        }
        if (op.layoutRef() == null || !LayoutBlueprints.ALL.containsKey(op.layoutRef())) {
            errors.add("SET_LAYOUT: 등록되지 않은 layoutRef(" + op.layoutRef() + ")");
            return;
        }
        if (!isLayoutCompatible(page.pageType(), op.layoutRef())) {
            errors.add("SET_LAYOUT: " + page.pageType() + "와 호환되지 않는 layoutRef(" + op.layoutRef() + ")");
            return;
        }
        state.pages.put(page.id(), copyPage(page, null, null, op.layoutRef(), null, null, null));
        decisions.add(reasonOrDefault(op, page.title() + "의 레이아웃을 " + op.layoutRef() + "로 변경"));
    }

    private static void setFeature(MutableState state, PagePlanOperation op, List<String> errors,
                                   List<String> decisions) {
        PagePlan page = state.pages.get(op.pageId());
        if (page == null) {
            errors.add("SET_FEATURE: 존재하지 않는 pageId(" + op.pageId() + ")");
            return;
        }
        if (op.featureKey() == null || !FEATURE_KEY.matcher(op.featureKey()).matches()) {
            errors.add("SET_FEATURE: 유효하지 않은 featureKey(" + op.featureKey() + ")");
            return;
        }
        if (op.featureEnabled() == null) {
            errors.add("SET_FEATURE: featureEnabled가 비어있음");
            return;
        }
        Map<String, Boolean> features = new LinkedHashMap<>(safeMap(page.features()));
        features.put(op.featureKey(), op.featureEnabled());
        state.pages.put(page.id(), copyPage(page, null, null, null, null, null, features));
        decisions.add(reasonOrDefault(op, page.title() + "의 " + op.featureKey() + " 기능을 "
                + (op.featureEnabled() ? "활성화" : "비활성화")));
    }

    private static void addNavigation(MutableState state, PagePlanOperation op, List<String> errors,
                                      List<String> decisions) {
        NavigationRule rule = op.navigationRule();
        if (rule == null) {
            errors.add("ADD_NAVIGATION: navigationRule이 비어있음");
            return;
        }
        PagePlan source = state.pages.get(rule.sourcePageId());
        if (source == null) {
            errors.add("ADD_NAVIGATION: 존재하지 않는 sourcePageId(" + rule.sourcePageId() + ")");
            return;
        }
        Set<String> pageIds = state.pages.keySet();
        List<String> navErrors = validateNavigation(rule, source.id(), pageIds);
        if (!navErrors.isEmpty()) {
            errors.addAll(navErrors.stream().map(e -> "ADD_NAVIGATION: " + e).toList());
            return;
        }
        List<NavigationRule> rules = new ArrayList<>(safeList(source.navigationRules()));
        if (rules.stream().filter(Objects::nonNull)
                .anyMatch(existing -> Objects.equals(existing.trigger(), rule.trigger()))) {
            errors.add("ADD_NAVIGATION: 같은 trigger의 규칙이 이미 존재함(" + rule.trigger() + ")");
            return;
        }
        if (rules.size() >= MAX_NAVIGATION_RULES_PER_PAGE) {
            errors.add("ADD_NAVIGATION: 페이지당 navigation rule 상한 초과(" + source.id() + ")");
            return;
        }
        rules.add(rule);
        state.pages.put(source.id(), copyPage(source, null, null, null, null, rules, null));
        decisions.add(reasonOrDefault(op, source.title() + "에 " + rule.trigger() + " 네비게이션 추가"));
    }

    private static void addFlow(MutableState state, PagePlanOperation op, List<String> errors,
                                List<String> decisions) {
        FlowBlueprint flow = op.flow();
        if (flow == null || !validId(flow.id())) {
            errors.add("ADD_FLOW: flow 또는 flow.id가 유효하지 않음");
            return;
        }
        if (state.flows.stream().filter(Objects::nonNull).anyMatch(existing -> existing.id().equals(flow.id()))) {
            errors.add("ADD_FLOW: 이미 존재하는 flowId(" + flow.id() + ")");
            return;
        }
        // 미할당 trigger는 ASSIGN_FLOW가 뒤에서 채울 수 있으므로 여기서는 구조와 페이지 참조만 검사한다.
        Set<String> pageIds = state.pages.keySet();
        List<String> flowErrors = FlowBlueprintValidator.validate(flow, pageIds).stream()
                .filter(error -> !error.contains("trigger.pageId"))
                .toList();
        if (!flowErrors.isEmpty()) {
            errors.addAll(flowErrors.stream().map(e -> "ADD_FLOW: " + e).toList());
            return;
        }
        state.flows.add(flow);
        decisions.add(reasonOrDefault(op, flow.id() + " Flow 추가"));
    }

    private static void assignFlow(MutableState state, PagePlanOperation op, List<String> errors,
                                   List<String> decisions) {
        int index = indexOfFlow(state.flows, op.flowId());
        if (index < 0) {
            errors.add("ASSIGN_FLOW: 존재하지 않는 flowId(" + op.flowId() + ")");
            return;
        }
        PagePlan page = state.pages.get(op.pageId());
        if (page == null) {
            errors.add("ASSIGN_FLOW: 존재하지 않는 pageId(" + op.pageId() + ")");
            return;
        }
        if (op.actionId() == null || op.actionId().isBlank()) {
            errors.add("ASSIGN_FLOW: actionId가 비어있음");
            return;
        }
        if (!safeList(page.capabilityIds()).contains(op.actionId())) {
            errors.add("ASSIGN_FLOW: actionId가 해당 페이지 capability가 아님(" + op.actionId() + ")");
            return;
        }
        FlowBlueprint existing = state.flows.get(index);
        state.flows.set(index, new FlowBlueprint(existing.id(),
                new FlowBlueprint.FlowTrigger(page.id(), op.actionId()), existing.steps()));
        decisions.add(reasonOrDefault(op, existing.id() + " Flow를 " + page.title() + "/" + op.actionId() + "에 연결"));
    }

    private static List<String> validateLayout(PagePlan page) {
        List<String> errors = new ArrayList<>();
        if (page.layoutRef() == null || !LayoutBlueprints.ALL.containsKey(page.layoutRef())) {
            errors.add(page.id() + ": 등록되지 않은 layoutRef(" + page.layoutRef() + ")");
        } else if (!isLayoutCompatible(page.pageType(), page.layoutRef())) {
            errors.add(page.id() + ": " + page.pageType() + "와 호환되지 않는 layoutRef(" + page.layoutRef() + ")");
        }
        return errors;
    }

    private static List<String> validateNavigation(NavigationRule rule, String ownerPageId, Set<String> pageIds) {
        List<String> errors = new ArrayList<>();
        if (rule == null) {
            return List.of(ownerPageId + ": null NavigationRule은 허용되지 않음");
        }
        if (rule.sourcePageId() == null || !rule.sourcePageId().equals(ownerPageId)) {
            errors.add(ownerPageId + ": navigation sourcePageId가 소유 페이지와 다름(" + rule.sourcePageId() + ")");
        }
        if (rule.trigger() == null || rule.trigger().isBlank()) {
            errors.add(ownerPageId + ": navigation trigger가 비어있음");
        } else if (!SUPPORTED_NAVIGATION_TRIGGERS.contains(rule.trigger()) && !rule.trigger().startsWith("action.")) {
            errors.add(ownerPageId + ": 지원하지 않는 navigation trigger(" + rule.trigger() + ")");
        }
        if (rule.type() == null) {
            errors.add(ownerPageId + ": navigation type이 비어있음");
        } else if (rule.type() != NavigationRule.NavigationType.GO_BACK) {
            if (rule.targetPageId() == null || !pageIds.contains(rule.targetPageId())) {
                errors.add(ownerPageId + ": 존재하지 않는 navigation targetPageId(" + rule.targetPageId() + ")");
            }
        }
        for (Map.Entry<String, String> entry : safeMap(rule.parameters()).entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                errors.add(ownerPageId + ": navigation parameter 이름이 비어있음");
            }
            String value = entry.getValue();
            if (value == null) {
                errors.add(ownerPageId + ": navigation parameter 값이 null임(" + entry.getKey() + ")");
            } else if (FlowExpression.isExpressionLike(value) && FlowExpression.parse(value).isEmpty()) {
                errors.add(ownerPageId + ": navigation parameter가 허용되지 않는 표현식(" + value + ")");
            }
        }
        return errors;
    }

    private static void rewritePageReferences(MutableState state, String fromPageId, String toPageId) {
        state.pages.replaceAll((id, page) -> copyPage(page, null, null, null, null,
                safeList(page.navigationRules()).stream()
                        .map(rule -> rewriteNavigation(rule, fromPageId, toPageId))
                        .toList(), null));
        for (int i = 0; i < state.flows.size(); i++) {
            FlowBlueprint flow = state.flows.get(i);
            if (flow == null) {
                continue;
            }
            FlowBlueprint.FlowTrigger trigger = flow.trigger();
            if (trigger != null && fromPageId.equals(trigger.pageId())) {
                trigger = new FlowBlueprint.FlowTrigger(toPageId, trigger.actionId());
            }
            List<FlowStep> steps = safeList(flow.steps()).stream()
                    .map(step -> rewriteFlowStepPage(step, fromPageId, toPageId))
                    .toList();
            state.flows.set(i, new FlowBlueprint(flow.id(), trigger, steps));
        }
    }

    private static NavigationRule rewriteNavigation(NavigationRule rule, String from, String to) {
        if (rule == null) {
            return null;
        }
        String source = from.equals(rule.sourcePageId()) ? to : rule.sourcePageId();
        String target = from.equals(rule.targetPageId()) ? to : rule.targetPageId();
        return new NavigationRule(source, rule.trigger(), rule.type(), target, safeMap(rule.parameters()));
    }

    private static FlowStep rewriteFlowStepPage(FlowStep step, String from, String to) {
        if (step == null || step.pageId() == null || !step.pageId().equals(from)) {
            return step;
        }
        return new FlowStep(step.id(), step.type(), step.bindingRef(), step.input(), step.values(), to,
                step.parameters(), step.until(), step.intervalMs(), step.timeoutSeconds(), step.condition(), step.message());
    }

    private static PagePlan copyPageForType(PagePlan page, PageType pageType, String layoutRef) {
        boolean detail = pageType == PageType.RESOURCE_DETAIL;
        String route = detail ? defaultRoute(page.id(), pageType) : "/" + page.id();
        List<RouteParameter> routeParameters = detail ? defaultRouteParameters(pageType) : List.of();
        return new PagePlan(page.id(), page.title(), route, pageType, layoutRef, safeList(page.capabilityIds()),
                routeParameters, safeList(page.queryParameters()), safeList(page.navigationRules()),
                safeMap(page.features()), page.confidence(), page.reason(), safeList(page.unsupportedCapabilityWarnings()));
    }

    private static String defaultRoute(String pageId, PageType pageType) {
        return pageType == PageType.RESOURCE_DETAIL ? "/" + pageId + "/:id" : "/" + pageId;
    }

    private static List<RouteParameter> defaultRouteParameters(PageType pageType) {
        return pageType == PageType.RESOURCE_DETAIL
                ? List.of(new RouteParameter("id", "navigation"))
                : List.of();
    }

    private static PagePlan copyPage(PagePlan page, String title, PageType pageType, String layoutRef,
                                     List<String> capabilityIds, List<NavigationRule> navigationRules,
                                     Map<String, Boolean> features) {
        PageType resolvedType = pageType == null ? page.pageType() : pageType;
        String resolvedLayout = layoutRef == null ? page.layoutRef() : layoutRef;
        return new PagePlan(
                page.id(),
                title == null ? page.title() : title,
                page.route(),
                resolvedType,
                resolvedLayout,
                capabilityIds == null ? safeList(page.capabilityIds()) : List.copyOf(capabilityIds),
                safeList(page.routeParameters()),
                safeList(page.queryParameters()),
                navigationRules == null ? safeList(page.navigationRules()) : List.copyOf(navigationRules),
                features == null ? safeMap(page.features()) : Map.copyOf(features),
                page.confidence(),
                page.reason(),
                safeList(page.unsupportedCapabilityWarnings())
        );
    }

    private static PagePlan withCapabilities(PagePlan page, List<String> capabilityIds,
                                             List<Capability> capabilities) {
        PageType inferred = inferPageType(capabilityIds, page.pageType(), capabilityMap(capabilities));
        PagePlan typed = page;
        if (inferred != page.pageType()) {
            // 타입이 재추론으로 바뀌면 새 역할에 맞는 기본 레이아웃으로 스냅한다. 이전 타입의
            // 레이아웃(예: list-detail-layout)이 새 타입과 "호환"으로 판정되더라도 그대로 두면
            // 분리된 목록 페이지가 계속 상세용 레이아웃을 쓰게 되어 부적절하다.
            typed = copyPageForType(page, inferred, defaultLayout(inferred));
        }
        return copyPage(typed, null, null, null, capabilityIds, null, null);
    }

    private static PageType inferPageType(List<String> capabilityIds, PageType fallback,
                                          Map<String, Capability> capabilityById) {
        // SETTINGS/WORKFLOW 같은 의미 기반 타입은 CRUD 구성 변화로 덮어쓰지 않는다. 일반 리소스
        // 타입만 실제 LIST/DETAIL 조합으로 재계산해, 상세 capability를 분리했는데 원본이 계속
        // LIST_DETAIL로 남거나 새 상세 페이지가 RESOURCE_LIST로 남는 명목상 Patch를 방지한다.
        if (fallback != null && fallback != PageType.RESOURCE_LIST && fallback != PageType.RESOURCE_DETAIL
                && fallback != PageType.LIST_DETAIL && fallback != PageType.RESOURCE_OVERVIEW) {
            return fallback;
        }
        boolean hasList = false;
        boolean hasDetail = false;
        for (String id : safeList(capabilityIds)) {
            Capability capability = capabilityById.get(id);
            if (capability == null || capability.type() == null) {
                continue;
            }
            hasList |= capability.type() == gj.cloud.ops.application.preview.analysis.CapabilityType.LIST;
            hasDetail |= capability.type() == gj.cloud.ops.application.preview.analysis.CapabilityType.DETAIL;
        }
        if (hasList && hasDetail) {
            return PageType.LIST_DETAIL;
        }
        if (hasDetail) {
            return PageType.RESOURCE_DETAIL;
        }
        return fallback == PageType.RESOURCE_OVERVIEW ? PageType.RESOURCE_OVERVIEW : PageType.RESOURCE_LIST;
    }

    public static String defaultLayout(PageType pageType) {
        if (pageType == null) {
            return "resource-list-layout";
        }
        return switch (pageType) {
            case AUTH -> "auth-layout";
            case DASHBOARD -> "dashboard-layout";
            case RESOURCE_DETAIL -> "resource-detail-layout";
            case LIST_DETAIL -> "list-detail-layout";
            case WORKFLOW -> "workflow-layout";
            case SETTINGS -> "settings-layout";
            case RESOURCE_LIST, RESOURCE_OVERVIEW, ACTIVITY, FILE_MANAGER, ORGANIZATION -> "resource-list-layout";
        };
    }

    public static boolean isLayoutCompatible(PageType pageType, String layoutRef) {
        if (pageType == null || layoutRef == null || !LayoutBlueprints.ALL.containsKey(layoutRef)) {
            return false;
        }
        return switch (pageType) {
            case AUTH -> layoutRef.equals("auth-layout");
            case DASHBOARD -> layoutRef.equals("dashboard-layout");
            case RESOURCE_DETAIL -> layoutRef.equals("resource-detail-layout") || layoutRef.equals("list-detail-layout");
            case LIST_DETAIL -> layoutRef.equals("list-detail-layout") || layoutRef.equals("resource-detail-layout");
            case WORKFLOW -> layoutRef.equals("workflow-layout");
            case SETTINGS -> layoutRef.equals("settings-layout");
            case RESOURCE_LIST, RESOURCE_OVERVIEW, ACTIVITY, FILE_MANAGER, ORGANIZATION ->
                    layoutRef.equals("resource-list-layout") || layoutRef.equals("list-detail-layout");
        };
    }

    private static boolean requiresExplicitTrigger(RiskLevel risk) {
        return risk == RiskLevel.DESTRUCTIVE
                || risk == RiskLevel.IRREVERSIBLE
                || risk == RiskLevel.EXTERNAL_SIDE_EFFECT;
    }

    private static boolean isProtected(PagePlan page) {
        return page.pageType() == PageType.AUTH || page.pageType() == PageType.DASHBOARD;
    }

    private static int indexOfFlow(List<FlowBlueprint> flows, String id) {
        for (int i = 0; i < flows.size(); i++) {
            if (flows.get(i) != null && Objects.equals(flows.get(i).id(), id)) {
                return i;
            }
        }
        return -1;
    }

    private static PagePlan findPage(List<PagePlan> pages, String id) {
        return pages.stream().filter(Objects::nonNull)
                .filter(page -> Objects.equals(page.id(), id)).findFirst().orElse(null);
    }

    private static Map<String, Capability> capabilityMap(List<Capability> capabilities) {
        Map<String, Capability> result = new LinkedHashMap<>();
        if (capabilities != null) {
            for (Capability capability : capabilities) {
                if (capability != null && capability.id() != null) {
                    result.put(capability.id(), capability);
                }
            }
        }
        return result;
    }

    private static Map<String, Boolean> defaultFeatures() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put("quickActions", false);
        result.put("statusSummary", false);
        result.put("childResourceTabs", false);
        return result;
    }

    private static List<String> validatePatchInput(PlanPatchState state) {
        if (state == null) {
            return List.of("PlanPatchState가 null임");
        }
        List<String> errors = new ArrayList<>();
        Set<String> pageIds = new LinkedHashSet<>();
        for (PagePlan page : state.pagePlans()) {
            if (page == null) {
                errors.add("기존 상태에 null PagePlan이 있음");
            } else if (page.id() == null || !pageIds.add(page.id())) {
                errors.add("기존 상태의 page id가 비어있거나 중복됨: " + page.id());
            }
        }
        Set<String> flowIds = new LinkedHashSet<>();
        for (FlowBlueprint flow : state.flows()) {
            if (flow == null) {
                errors.add("기존 상태에 null FlowBlueprint가 있음");
            } else if (flow.id() == null || !flowIds.add(flow.id())) {
                errors.add("기존 상태의 flow id가 비어있거나 중복됨: " + flow.id());
            }
        }
        Set<String> bindingIds = new LinkedHashSet<>();
        for (ApiBinding binding : state.bindings()) {
            if (binding == null) {
                errors.add("기존 상태에 null ApiBinding이 있음");
            } else if (binding.id() == null || !bindingIds.add(binding.id())) {
                errors.add("기존 상태의 binding id가 비어있거나 중복됨: " + binding.id());
            }
        }
        return errors;
    }

    private static boolean validId(String value) {
        return value != null && ID.matcher(value).matches();
    }

    private static String reasonOrDefault(PagePlanOperation op, String fallback) {
        return op.reason() != null && !op.reason().isBlank() ? op.reason() : fallback;
    }

    private static <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private static <K, V> Map<K, V> safeMap(Map<K, V> value) {
        return value == null ? Map.of() : value;
    }

    private static final class MutableState {
        private final LinkedHashMap<String, PagePlan> pages = new LinkedHashMap<>();
        private final List<FlowBlueprint> flows = new ArrayList<>();
        private final List<ApiBinding> bindings = new ArrayList<>();

        private static MutableState from(PlanPatchState state) {
            MutableState mutable = new MutableState();
            for (PagePlan page : state.pagePlans()) {
                if (page != null) {
                    mutable.pages.put(page.id(), page);
                }
            }
            mutable.flows.addAll(state.flows());
            mutable.bindings.addAll(state.bindings());
            return mutable;
        }

        private PlanPatchState freeze() {
            return new PlanPatchState(List.copyOf(pages.values()), List.copyOf(flows), List.copyOf(bindings));
        }
    }

    private PagePlanPatchValidator() {
    }
}
