package gj.cloud.ops.application.preview.scenario;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityKind;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.OpenApiEvidence;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioPlan;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioStagePlan;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ServiceActor;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ServiceUnderstanding;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageRole;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.VerificationType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM Scenario Planner를 붙이기 전에도 scenario-first vertical slice를 실행할 수 있는 결정론적 planner.
 * UI/page/component id를 전혀 생성하지 않고 의미 stage와 capability requirement만 만든다.
 */
@Component
public class RuleBasedScenarioPlanner {

    private static final int MAX_SCENARIOS = 8;
    private static final Pattern PATH_PARAM = Pattern.compile("\\{([^}]+)}");

    public record PlanningResult(ServiceUnderstanding understanding, List<ScenarioPlan> plans, List<String> errors) {
    }

    public PlanningResult plan(
            OpenApiEvidence evidence,
            String serviceDescription,
            Purpose purpose,
            List<Capability> capabilities
    ) {
        List<Capability> safeCapabilities = capabilities == null ? List.of() : capabilities;
        ServiceUnderstanding understanding = understand(evidence, serviceDescription, purpose, safeCapabilities);
        List<ScenarioPlan> plans = new ArrayList<>();

        Map<String, List<Capability>> byResource = new LinkedHashMap<>();
        for (Capability capability : safeCapabilities) {
            if (capability != null) {
                byResource.computeIfAbsent(capability.resourceName(), ignored -> new ArrayList<>()).add(capability);
            }
        }

        Capability login = findType(safeCapabilities, CapabilityType.LOGIN);
        Capability protectedQuery = safeCapabilities.stream()
                .filter(capability -> capability.type() == CapabilityType.LIST)
                .findFirst().orElse(null);
        if (login != null && protectedQuery != null) {
            plans.add(authenticatedQuery(login, protectedQuery));
        }

        for (Map.Entry<String, List<Capability>> entry : byResource.entrySet()) {
            if (plans.size() >= MAX_SCENARIOS) break;
            Capability list = findType(entry.getValue(), CapabilityType.LIST);
            Capability detail = findType(entry.getValue(), CapabilityType.DETAIL);
            Capability create = findType(entry.getValue(), CapabilityType.CREATE);
            Capability update = findType(entry.getValue(), CapabilityType.UPDATE);

            if (list != null && detail != null) plans.add(queryAndInspect(entry.getKey(), list, detail));
            if (plans.size() >= MAX_SCENARIOS) break;
            if (create != null && (detail != null || list != null)) {
                plans.add(createAndVerify(entry.getKey(), create, detail != null ? detail : list));
            }
            if (plans.size() >= MAX_SCENARIOS) break;
            if (update != null && (detail != null || list != null)) {
                plans.add(updateAndVerify(entry.getKey(), update, detail != null ? detail : list));
            }
            if (plans.size() >= MAX_SCENARIOS) break;
            for (Capability command : entry.getValue().stream()
                    .filter(capability -> capability.kind() == CapabilityKind.COMMAND).toList()) {
                Capability verifier = command.dependencies().stream()
                        .map(id -> safeCapabilities.stream().filter(candidate -> Objects.equals(candidate.id(), id))
                                .findFirst().orElse(null))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(detail != null ? detail : list);
                if (verifier != null) plans.add(commandAndVerify(entry.getKey(), command, verifier));
                if (plans.size() >= MAX_SCENARIOS) break;
            }
        }

        List<String> errors = new ArrayList<>();
        List<ScenarioPlan> valid = new ArrayList<>();
        for (ScenarioPlan plan : plans) {
            List<String> planErrors = ScenarioValidator.validatePlan(plan);
            if (planErrors.isEmpty()) valid.add(plan);
            else errors.addAll(planErrors);
        }
        return new PlanningResult(understanding, List.copyOf(valid), List.copyOf(errors));
    }

    private ServiceUnderstanding understand(
            OpenApiEvidence evidence,
            String serviceDescription,
            Purpose purpose,
            List<Capability> capabilities
    ) {
        List<String> entities = capabilities.stream()
                .map(Capability::resourceName)
                .filter(Objects::nonNull)
                .filter(name -> !"auth".equalsIgnoreCase(name))
                .distinct()
                .limit(12)
                .toList();
        String title = evidence.title() == null || evidence.title().isBlank()
                ? entities.stream().findFirst().orElse("API Service")
                : evidence.title();
        String domain = title.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (domain.isBlank()) domain = "GENERAL_API";
        String serviceType = switch (purpose == null ? Purpose.API_TEST : purpose) {
            case ADMIN -> "INTERNAL_ADMIN_TOOL";
            case PRODUCT_LIKE -> "PRODUCT_SERVICE";
            case API_TEST -> "DEVELOPER_API";
        };
        List<ServiceActor> actors = List.of(new ServiceActor(
                purpose == Purpose.ADMIN ? "service_admin" : "api_developer",
                purpose == Purpose.ADMIN ? "Service Administrator" : "API Developer"
        ));
        List<String> goals = capabilities.stream()
                .filter(capability -> capability.kind() == CapabilityKind.COMMAND
                        || capability.type() == CapabilityType.CREATE
                        || capability.type() == CapabilityType.UPDATE)
                .map(capability -> capability.action() != null
                        ? capability.action() + " " + capability.resourceName()
                        : capability.type().name().toLowerCase(Locale.ROOT) + " " + capability.resourceName())
                .distinct()
                .limit(8)
                .toList();
        List<String> evidenceRefs = new ArrayList<>();
        evidenceRefs.add("OpenAPI title: " + title);
        if (serviceDescription != null && !serviceDescription.isBlank()) {
            evidenceRefs.add("User service description supplied");
        }
        capabilities.stream().map(Capability::operationId).filter(Objects::nonNull).limit(8)
                .forEach(operationId -> evidenceRefs.add("Operation: " + operationId));
        double confidence = evidence.title() != null && !entities.isEmpty() ? 0.88 : !entities.isEmpty() ? 0.74 : 0.45;
        return new ServiceUnderstanding(domain, serviceType, actors, entities, goals, confidence, evidenceRefs);
    }

    private ScenarioPlan authenticatedQuery(Capability login, Capability query) {
        List<String> credentials = login.fields().isEmpty() ? List.of("email", "password") : login.fields();
        List<String> state = new ArrayList<>(credentials);
        state.add("authToken");
        state.add("authenticatedCollection");
        List<ScenarioStagePlan> stages = List.of(
                stage("prepare-credentials", StageRole.PREPARE, "서비스 인증 정보 입력",
                        null, true, List.of(), credentials, "authenticate", null),
                stage("authenticate", StageRole.AUTHENTICATE, "서비스에 인증하고 발급된 토큰을 다음 단계로 전달",
                        login.id(), true, credentials, List.of("authToken"), "discover-protected-data",
                        VerificationType.OUTPUT_EXTRACTABLE),
                stage("discover-protected-data", StageRole.DISCOVER, "전달된 인증 상태로 보호된 목록 조회",
                        query.id(), true, List.of("authToken"), List.of("authenticatedCollection"), "complete",
                        VerificationType.RESPONSE_SCHEMA_VALID),
                complete()
        );
        return plan("authenticate-and-query", "인증 후 보호 데이터 조회",
                "인증하고 보호된 백엔드 데이터를 실제로 조회할 수 있는지 확인", stages, state,
                List.of(login.id(), query.id()), 0.92);
    }

    private ScenarioPlan queryAndInspect(String resource, Capability list, Capability detail) {
        List<ScenarioStagePlan> stages = List.of(
                stage("discover", StageRole.DISCOVER, resource + " 목록 조회",
                        list.id(), true, List.of(), List.of("collection"), "select", VerificationType.RESPONSE_SCHEMA_VALID),
                stage("select", StageRole.SELECT, "목록에서 확인할 " + resource + " 선택",
                        null, true, List.of(), List.of("selectedId"), "inspect", null),
                stage("inspect", StageRole.INSPECT, "선택한 " + resource + " 상세 조회",
                        detail.id(), true, List.of("selectedId"), List.of("selectedResource"), "complete",
                        VerificationType.FIELD_EQUALS),
                complete()
        );
        return plan(resource + "-query-and-inspect", resource + " 탐색 및 상세 확인",
                "실제 목록에서 리소스를 선택하고 상세 API의 연결 상태를 검증", stages,
                List.of("collection", "selectedId", "selectedResource"),
                List.of(list.id(), detail.id()), 0.94);
    }

    private ScenarioPlan createAndVerify(String resource, Capability create, Capability verifier) {
        LinkedHashSet<String> prepared = new LinkedHashSet<>(pathParameters(create.path()));
        prepared.addAll(create.fields());
        List<String> outputs = new ArrayList<>(prepared);
        outputs.add("createdId");
        List<ScenarioStagePlan> stages = new ArrayList<>();
        stages.add(stage("prepare", StageRole.PREPARE, "새 " + resource + " 입력값 구성",
                null, true, List.of(), List.copyOf(prepared), "review", null));
        stages.add(stage("review", StageRole.REVIEW, "백엔드 상태를 변경하기 전에 요청 검토",
                null, true, List.copyOf(prepared), List.of(), "commit", null));
        stages.add(stage("commit", StageRole.COMMIT, resource + " 생성",
                create.id(), true, List.copyOf(prepared), List.of("createdId"),
                verifier.pollHint() != null ? "track" : "verify", VerificationType.OUTPUT_EXTRACTABLE));
        if (verifier.pollHint() != null) {
            stages.add(stage("track", StageRole.TRACK, "비동기 작업이 종료 상태에 도달할 때까지 추적",
                    verifier.id(), true, List.of("createdId"), List.of("trackedStatus"), "verify",
                    VerificationType.STATE_EQUALS));
            outputs.add("trackedStatus");
        }
        stages.add(stage("verify", StageRole.VERIFY, "생성된 리소스를 후속 API에서 다시 확인",
                verifier.id(), true, List.of("createdId"), List.of("verifiedResource"), "complete",
                verifier.type() == CapabilityType.LIST
                        ? VerificationType.COLLECTION_CONTAINS : VerificationType.RESOURCE_EXISTS));
        outputs.add("verifiedResource");
        stages.add(complete());
        return plan(resource + "-create-and-verify", resource + " 생성 및 검증",
                "리소스를 생성하고 후속 API를 통해 실제 상태 변경을 검증",
                stages, outputs, List.of(create.id(), verifier.id()), 0.96);
    }

    private ScenarioPlan updateAndVerify(String resource, Capability update, Capability verifier) {
        LinkedHashSet<String> prepared = new LinkedHashSet<>(pathParameters(update.path()));
        if (prepared.isEmpty()) prepared.add("selectedId");
        prepared.addAll(update.fields());
        List<String> state = new ArrayList<>(prepared);
        state.add("verifiedResource");
        List<ScenarioStagePlan> stages = List.of(
                stage("prepare", StageRole.PREPARE, "수정할 " + resource + " 및 변경값 입력",
                        null, true, List.of(), List.copyOf(prepared), "review", null),
                stage("review", StageRole.REVIEW, "수정 요청 검토",
                        null, true, List.copyOf(prepared), List.of(), "commit", null),
                stage("commit", StageRole.COMMIT, "선택한 " + resource + " 수정",
                        update.id(), true, List.copyOf(prepared), List.of(), "verify", VerificationType.HTTP_STATUS_MATCH),
                stage("verify", StageRole.VERIFY, "리소스를 다시 조회해 변경 결과 확인",
                        verifier.id(), true, idState(prepared), List.of("verifiedResource"), "complete",
                        verifier.type() == CapabilityType.LIST
                                ? VerificationType.COLLECTION_CONTAINS : VerificationType.RESOURCE_EXISTS),
                complete()
        );
        return plan(resource + "-update-and-verify", resource + " 수정 및 검증",
                "기존 리소스를 수정하고 백엔드에 반영된 상태를 재조회로 검증",
                stages, state, List.of(update.id(), verifier.id()), 0.93);
    }

    private ScenarioPlan commandAndVerify(String resource, Capability command, Capability verifier) {
        LinkedHashSet<String> prepared = new LinkedHashSet<>(pathParameters(command.path()));
        if (!command.path().contains("{")) prepared.add("selectedId");
        prepared.addAll(command.fields());
        List<String> state = new ArrayList<>(prepared);
        state.add("verifiedResource");
        List<ScenarioStagePlan> stages = List.of(
                stage("prepare", StageRole.PREPARE, resource + "의 " + command.action() + " 작업 구성",
                        null, true, List.of(), List.copyOf(prepared), "review", null),
                stage("review", StageRole.REVIEW, "상태 전이 대상과 위험도 검토",
                        null, true, List.copyOf(prepared), List.of(), "commit", null),
                stage("commit", StageRole.COMMIT, command.action() + " 작업 실행",
                        command.id(), true, List.copyOf(prepared), List.of(), "verify", VerificationType.HTTP_STATUS_MATCH),
                stage("verify", StageRole.VERIFY, command.action() + " 이후 백엔드 상태 검증",
                        verifier.id(), true, idState(prepared), List.of("verifiedResource"), "complete",
                        VerificationType.STATE_EQUALS),
                complete()
        );
        return plan(resource + "-" + command.action() + "-and-verify",
                humanize(command.action()) + " 실행 및 " + resource + " 검증",
                "백엔드 상태 전이를 실행하고 관찰 가능한 후속 결과를 검증",
                stages, state, List.of(command.id(), verifier.id()), 0.91);
    }

    private List<String> idState(Set<String> prepared) {
        return prepared.stream().filter(value -> value.toLowerCase(Locale.ROOT).contains("id")).limit(1).toList();
    }

    private ScenarioPlan plan(
            String id,
            String name,
            String goal,
            List<ScenarioStagePlan> stages,
            List<String> state,
            List<String> evidence,
            double confidence
    ) {
        return new ScenarioPlan(id, name, "api_developer", goal,
                List.of("api_server_available"), stages, List.copyOf(new LinkedHashSet<>(state)),
                confidence, evidence);
    }

    private ScenarioStagePlan stage(
            String id,
            StageRole role,
            String intent,
            String capability,
            boolean required,
            List<String> inputs,
            List<String> outputs,
            String next,
            VerificationType verification
    ) {
        return new ScenarioStagePlan(id, role, intent, capability, required, inputs, outputs,
                next == null ? List.of() : List.of(next), verification);
    }

    private ScenarioStagePlan complete() {
        return stage("complete", StageRole.COMPLETE, "시나리오 완료", null,
                true, List.of(), List.of(), null, null);
    }

    private Capability findType(List<Capability> capabilities, CapabilityType type) {
        return capabilities.stream().filter(Objects::nonNull).filter(capability -> capability.type() == type)
                .findFirst().orElse(null);
    }

    private List<String> pathParameters(String path) {
        List<String> parameters = new ArrayList<>();
        Matcher matcher = PATH_PARAM.matcher(path == null ? "" : path);
        while (matcher.find()) parameters.add(matcher.group(1));
        return parameters;
    }

    private String humanize(String value) {
        if (value == null || value.isBlank()) return "Execute command";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).replace('-', ' ').replace('_', ' ');
    }
}
