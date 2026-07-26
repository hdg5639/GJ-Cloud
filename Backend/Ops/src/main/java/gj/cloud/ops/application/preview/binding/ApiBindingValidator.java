package gj.cloud.ops.application.preview.binding;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.flow.FlowExpression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §8 "Required validation" —
// PagePlanValidator/FlowBlueprintValidator와 같은 static 유틸리티 관례. 아직 "적용"할 대상이 없어
// 에러 문자열 목록만 반환한다.
public final class ApiBindingValidator {

    private static final Pattern DOT_PATH = Pattern.compile("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$");
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Pattern PATH_PARAM = Pattern.compile("\\{([^}]+)}");

    // §17 "secret field redaction" — 프론트 CreateEditModal 등의 isPasswordLikeField와 같은 이름
    // 힌트 관례. 대소문자 무시하고 마지막 세그먼트에 포함되는지만 본다(완벽한 스키마 기반 판별이 아니라
    // Phase A 전체가 따르는 "이름 힌트" 수준).
    private static final Set<String> SENSITIVE_FIELD_HINTS = Set.of(
            "password", "pw", "pwd", "token", "secret", "apikey", "accesstoken", "refreshtoken", "ssn", "creditcard", "cvv");

    public static List<String> validate(List<ApiBinding> bindings, List<Capability> capabilities) {
        List<String> errors = new ArrayList<>();
        Map<String, Capability> capabilityById = new LinkedHashMap<>();
        for (Capability capability : capabilities) {
            capabilityById.put(capability.id(), capability);
        }
        Set<String> bindingIds = bindings.stream().map(ApiBinding::id).collect(java.util.stream.Collectors.toSet());

        for (ApiBinding binding : bindings) {
            errors.addAll(validateBinding(binding, capabilityById, bindingIds));
        }
        errors.addAll(detectRefreshCycles(bindings));
        return errors;
    }

    private static List<String> validateBinding(ApiBinding binding, Map<String, Capability> capabilityById,
                                                 Set<String> bindingIds) {
        List<String> errors = new ArrayList<>();

        Capability capability = capabilityById.get(binding.capabilityId());
        if (capability == null) {
            errors.add(binding.id() + ": 존재하지 않는 capabilityId(" + binding.capabilityId() + ")");
        } else {
            errors.addAll(validateRequiredPathMappings(binding, capability));
        }

        for (ApiBinding.InputMapping mapping : binding.inputMappings()) {
            if (mapping.target() == null || mapping.target().isBlank()) {
                errors.add(binding.id() + ": inputMapping target이 비어있음");
            }
            if (FlowExpression.isExpressionLike(mapping.from()) && FlowExpression.parse(mapping.from()).isEmpty()) {
                errors.add(binding.id() + ": inputMapping(" + mapping.target() + ")의 from이 허용되지 않는 표현식 형식임("
                        + mapping.from() + ")");
            }
        }

        for (ApiBinding.OutputMapping mapping : binding.outputMappings()) {
            if (mapping.from() == null || !DOT_PATH.matcher(mapping.from()).matches()) {
                errors.add(binding.id() + ": outputMapping의 from이 유효한 점경로가 아님(" + mapping.from() + ")");
            }
            if (mapping.to() == null || !IDENTIFIER.matcher(mapping.to()).matches()) {
                errors.add(binding.id() + ": outputMapping의 to가 유효한 식별자가 아님(" + mapping.to() + ")");
            }
            if (mapping.from() != null && isSensitiveField(mapping.from())) {
                errors.add(binding.id() + ": outputMapping이 민감한 필드로 보이는 값을 context에 저장하려 함("
                        + mapping.from() + ")");
            }
        }

        for (String refreshTarget : binding.refreshBindingIds()) {
            if (!bindingIds.contains(refreshTarget)) {
                errors.add(binding.id() + ": 존재하지 않는 refreshBindingIds 대상(" + refreshTarget + ")");
            }
        }

        return errors;
    }

    // §8 "a required mapping is not missing" — capability.path()의 {paramName} 경로 파라미터마다
    // targetKind=PATH인 InputMapping이 반드시 있어야 한다.
    private static List<String> validateRequiredPathMappings(ApiBinding binding, Capability capability) {
        List<String> errors = new ArrayList<>();
        Set<String> mappedPathTargets = binding.inputMappings().stream()
                .filter(m -> m.targetKind() == ApiBinding.InputMapping.InputTarget.PATH)
                .map(ApiBinding.InputMapping::target)
                .collect(java.util.stream.Collectors.toSet());

        Matcher matcher = PATH_PARAM.matcher(capability.path());
        while (matcher.find()) {
            String paramName = matcher.group(1);
            if (!mappedPathTargets.contains(paramName)) {
                errors.add(binding.id() + ": 경로 파라미터(" + paramName + ")에 대한 PATH inputMapping이 없음");
            }
        }
        return errors;
    }

    private static boolean isSensitiveField(String dotPath) {
        String[] segments = dotPath.split("\\.");
        String lastSegment = segments[segments.length - 1].toLowerCase();
        return SENSITIVE_FIELD_HINTS.stream().anyMatch(lastSegment::contains);
    }

    // §17 "references do not create invalid cycles" — refreshBindingIds가 만드는 바인딩→바인딩
    // 그래프에서 DFS로 순환을 찾는다(WP-2 FlowBlueprintValidator가 "지금은 그래프가 없어 범위 밖"이라
    // 미뤘던 것 — refreshBindingIds가 이 CR에서 처음 생기는 실제 그래프라 여기서 구현한다).
    private static List<String> detectRefreshCycles(List<ApiBinding> bindings) {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (ApiBinding binding : bindings) {
            graph.put(binding.id(), binding.refreshBindingIds());
        }

        List<String> errors = new ArrayList<>();
        Set<String> visited = new java.util.HashSet<>();
        for (String start : graph.keySet()) {
            if (!visited.contains(start)) {
                List<String> path = new ArrayList<>();
                if (hasCycle(start, graph, visited, new java.util.LinkedHashSet<>(), path)) {
                    errors.add("refreshBindingIds가 순환을 만듦: " + String.join(" -> ", path));
                }
            }
        }
        return errors;
    }

    private static boolean hasCycle(String current, Map<String, List<String>> graph, Set<String> visited,
                                     Set<String> onStack, List<String> path) {
        visited.add(current);
        onStack.add(current);
        path.add(current);

        for (String next : graph.getOrDefault(current, List.of())) {
            if (onStack.contains(next)) {
                path.add(next);
                return true;
            }
            if (!visited.contains(next) && hasCycle(next, graph, visited, onStack, path)) {
                return true;
            }
        }

        onStack.remove(current);
        path.remove(path.size() - 1);
        return false;
    }

    private ApiBindingValidator() {
    }
}
