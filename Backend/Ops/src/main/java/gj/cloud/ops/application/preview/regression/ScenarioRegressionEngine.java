package gj.cloud.ops.application.preview.regression;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.RiskLevel;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenario;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenarioStage;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageInputBinding;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.StageOutputBinding;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.VerificationContract;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ScenarioRegressionEngine {

    private final RegressionHttpTransport transport;

    public ScenarioResult execute(
            CompiledScenario scenario,
            Map<String, Capability> capabilities,
            String apiBaseUrl,
            Map<String, Object> initialState,
            Map<String, String> sharedHeaders,
            boolean allowStateChanging
    ) {
        Instant startedAt = Instant.now();
        Map<String, Object> state = new LinkedHashMap<>(
                initialState == null ? Map.of() : initialState);
        List<StageResult> stages = new ArrayList<>();
        Map<String, CompiledScenarioStage> stagesById = new LinkedHashMap<>();
        scenario.stages().forEach(stage -> stagesById.put(stage.id(), stage));

        String currentId = scenario.entryStageId();
        int guard = 0;
        while (currentId != null && guard++ <= scenario.stages().size()) {
            CompiledScenarioStage stage = stagesById.get(currentId);
            if (stage == null) {
                StageResult failure = StageResult.failed(
                        currentId, null, null, null, Map.of(), null, null, 0,
                        "다음 실행 단계를 찾지 못했습니다.");
                stages.add(failure);
                return failed(state, stages, failure, startedAt);
            }

            StageResult result = executeStage(
                    stage, capabilities, apiBaseUrl, state, sharedHeaders, allowStateChanging);
            stages.add(result);
            if (!result.success()) {
                if (stage.optional()) {
                    currentId = stage.nextStageIds().stream().findFirst().orElse(null);
                    continue;
                }
                return failed(state, stages, result, startedAt);
            }
            state.putAll(result.extractedOutputs());
            currentId = stage.nextStageIds().stream().findFirst().orElse(null);
        }

        if (guard > scenario.stages().size() + 1) {
            StageResult failure = StageResult.failed(
                    currentId, null, null, null, Map.of(), null, null, 0,
                    "시나리오 단계 순환으로 실행을 중단했습니다.");
            stages.add(failure);
            return failed(state, stages, failure, startedAt);
        }
        return new ScenarioResult(true, immutableMap(state), List.copyOf(stages), null, null,
                Duration.between(startedAt, Instant.now()).toMillis());
    }

    private StageResult executeStage(
            CompiledScenarioStage stage,
            Map<String, Capability> capabilities,
            String apiBaseUrl,
            Map<String, Object> state,
            Map<String, String> sharedHeaders,
            boolean allowStateChanging
    ) {
        Instant startedAt = Instant.now();
        if (!stage.executableOperation()) {
            List<String> missing = stage.inputs().stream()
                    .filter(input -> missing(state.get(input)))
                    .toList();
            if (!missing.isEmpty() && !stage.optional()) {
                return StageResult.failed(stage.id(), stage.operationId(), null, null,
                        Map.of("missingInputs", missing), null, null,
                        elapsed(startedAt), "자동 실행 입력이 부족합니다: " + String.join(", ", missing));
            }
            return StageResult.passed(stage.id(), stage.operationId(), null, null,
                    Map.of(), null, null, Map.of(), List.of(), elapsed(startedAt));
        }

        Capability capability = capabilities.get(stage.capabilityId());
        if (capability == null) {
            return StageResult.failed(stage.id(), stage.operationId(), null, null, Map.of(),
                    null, null, elapsed(startedAt), "컴파일된 capability를 현재 OpenAPI에서 찾지 못했습니다.");
        }
        if (!riskAllowed(stage.risk(), allowStateChanging)) {
            return StageResult.failed(stage.id(), stage.operationId(), capability.method(), null,
                    Map.of("risk", String.valueOf(stage.risk())), null, null, elapsed(startedAt),
                    "자동 실행 정책이 " + stage.risk() + " 작업을 차단했습니다.");
        }

        BuiltRequest request;
        try {
            request = buildRequest(stage, capability, apiBaseUrl, state, sharedHeaders);
        } catch (IllegalArgumentException error) {
            return StageResult.failed(stage.id(), stage.operationId(), capability.method(), null,
                    Map.of(), null, null, elapsed(startedAt), error.getMessage());
        }

        try {
            RegressionHttpTransport.Response response = transport.execute(
                    new RegressionHttpTransport.Request(
                            capability.method(), request.url(), request.headers(), request.body()));
            if (response.status() < 200 || response.status() >= 300) {
                return StageResult.failed(stage.id(), stage.operationId(), capability.method(), request.safeUrl(),
                        request.snapshot(), response.status(), response.body(), elapsed(startedAt),
                        "HTTP " + response.status() + " 응답으로 단계 실행에 실패했습니다.");
            }
            Map<String, Object> outputs = extractOutputs(stage.outputBindings(), response.body());
            Map<String, Object> nextState = new LinkedHashMap<>(state);
            nextState.putAll(outputs);
            List<AssertionResult> assertions =
                    verify(stage.verification(), response, nextState);
            boolean failed = stage.verification() != null
                    && stage.verification().required()
                    && assertions.stream().anyMatch(assertion -> !assertion.passed());
            if (failed) {
                return StageResult.failed(stage.id(), stage.operationId(), capability.method(), request.safeUrl(),
                        request.snapshot(), response.status(), response.body(), elapsed(startedAt),
                        assertions.stream().filter(assertion -> !assertion.passed())
                                .map(AssertionResult::message).findFirst().orElse("검증 실패"),
                        assertions);
            }
            return StageResult.passed(stage.id(), stage.operationId(), capability.method(), request.safeUrl(),
                    request.snapshot(), response.status(), response.body(), outputs, assertions, elapsed(startedAt));
        } catch (RuntimeException error) {
            return StageResult.failed(stage.id(), stage.operationId(), capability.method(), request.safeUrl(),
                    request.snapshot(), null, null, elapsed(startedAt), safeMessage(error));
        }
    }

    private BuiltRequest buildRequest(
            CompiledScenarioStage stage,
            Capability capability,
            String apiBaseUrl,
            Map<String, Object> state,
            Map<String, String> sharedHeaders
    ) {
        Map<String, String> path = new LinkedHashMap<>();
        Map<String, String> query = new LinkedHashMap<>();
        Map<String, String> headers = new LinkedHashMap<>(
                sharedHeaders == null ? Map.of() : sharedHeaders);
        Map<String, Object> body = new LinkedHashMap<>();

        for (StageInputBinding binding : stage.inputBindings()) {
            Object value = resolve(binding.source(), state);
            if (missing(value)) {
                if (binding.required()) {
                    throw new IllegalArgumentException(
                            "필수 입력값을 찾지 못했습니다: " + binding.source());
                }
                continue;
            }
            switch (binding.targetKind()) {
                case PATH -> path.put(binding.target(), String.valueOf(value));
                case QUERY -> query.put(binding.target(), String.valueOf(value));
                case HEADER -> headers.put(binding.target(), String.valueOf(value));
                case BODY -> body.put(binding.target(), value);
            }
        }
        Object authToken = state.get("authToken");
        if (authToken != null && !headers.containsKey("Authorization")) {
            headers.put("Authorization", "Bearer " + authToken);
        }

        String resolvedPath = capability.path();
        String safeResolvedPath = capability.path();
        for (Map.Entry<String, String> parameter : path.entrySet()) {
            resolvedPath = resolvedPath.replace(
                    "{" + parameter.getKey() + "}", encode(parameter.getValue()));
            safeResolvedPath = safeResolvedPath.replace(
                    "{" + parameter.getKey() + "}",
                    sensitiveName(parameter.getKey()) ? "%E2%80%A2%E2%80%A2%E2%80%A2%E2%80%A2%E2%80%A2%E2%80%A2"
                            : encode(parameter.getValue()));
        }
        if (resolvedPath.matches(".*\\{[^}]+}.*")) {
            throw new IllegalArgumentException("필수 경로 파라미터가 해결되지 않았습니다: " + resolvedPath);
        }
        StringBuilder url = new StringBuilder(apiBaseUrl.replaceAll("/+$", ""))
                .append('/').append(resolvedPath.replaceFirst("^/+", ""));
        StringBuilder safeUrl = new StringBuilder(apiBaseUrl.replaceAll("/+$", ""))
                .append('/').append(safeResolvedPath.replaceFirst("^/+", ""));
        if (!query.isEmpty()) {
            url.append('?');
            safeUrl.append('?');
            boolean first = true;
            for (Map.Entry<String, String> parameter : query.entrySet()) {
                if (!first) {
                    url.append('&');
                    safeUrl.append('&');
                }
                first = false;
                url.append(encode(parameter.getKey())).append('=').append(encode(parameter.getValue()));
                safeUrl.append(encode(parameter.getKey())).append('=')
                        .append(sensitiveName(parameter.getKey()) ? "%E2%80%A2%E2%80%A2%E2%80%A2%E2%80%A2%E2%80%A2%E2%80%A2"
                                : encode(parameter.getValue()));
            }
        }
        return new BuiltRequest(url.toString(), safeUrl.toString(), headers, body,
                Map.of("path", path, "query", query, "headers", headers, "body", body));
    }

    private Map<String, Object> extractOutputs(
            List<StageOutputBinding> bindings,
            Object response
    ) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        for (StageOutputBinding binding : bindings) {
            Object value = null;
            for (String candidate : binding.fromCandidates()) {
                value = readPath(response, candidate);
                if (value != null) break;
            }
            if (value == null && ("collection".equals(binding.to())
                    || "authenticatedCollection".equals(binding.to()))) {
                value = findCollection(response, 0);
            }
            if (value == null && ("selectedResource".equals(binding.to())
                    || "verifiedResource".equals(binding.to()))) {
                value = unwrapEnvelope(response);
            }
            if (value != null) outputs.put(binding.to(), value);
        }
        return outputs;
    }

    private List<AssertionResult> verify(
            VerificationContract contract,
            RegressionHttpTransport.Response response,
            Map<String, Object> state
    ) {
        if (contract == null) return List.of();
        Object expected = contract.expectedSource() == null
                ? null : resolve(contract.expectedSource(), state);
        Object actual = contract.responsePath() == null
                ? response.body() : readPath(response.body(), contract.responsePath());
        boolean passed;
        String message;
        switch (contract.type()) {
            case HTTP_STATUS_MATCH -> {
                passed = response.status() >= 200 && response.status() < 300;
                message = passed ? "성공 HTTP 상태 확인" : "HTTP " + response.status() + " 응답";
            }
            case RESPONSE_SCHEMA_VALID, RESOURCE_EXISTS -> {
                passed = response.status() >= 200 && response.status() < 300 && response.body() != null;
                message = passed ? "응답 데이터 확인" : "응답 데이터 없음";
            }
            case RESOURCE_NOT_EXISTS -> {
                passed = response.body() == null || response.status() == 404;
                message = passed ? "리소스 부재 확인" : "리소스가 여전히 존재함";
            }
            case FIELD_EQUALS -> {
                passed = equal(actual, expected);
                message = passed ? "필드 값 일치" : "필드 값 불일치";
            }
            case STATE_EQUALS -> {
                passed = !contract.acceptedValues().isEmpty()
                        ? contract.acceptedValues().stream().anyMatch(value -> equal(actual, value))
                        : expected != null ? equal(actual, expected) : actual != null;
                message = passed ? "상태 값 일치" : "상태 값 불일치";
            }
            case COLLECTION_CONTAINS -> {
                passed = collectionContains(actual, expected);
                message = passed ? "목록에 대상 포함" : "목록에 대상이 없음";
            }
            case COLLECTION_EXCLUDES -> {
                passed = !collectionContains(actual, expected);
                message = passed ? "목록에서 대상 제외" : "목록에 대상이 남아 있음";
            }
            case OUTPUT_EXTRACTABLE -> {
                passed = expected != null && !String.valueOf(expected).isBlank();
                message = passed ? "출력값 추출 확인" : "필수 출력값 추출 실패";
            }
            default -> {
                passed = false;
                message = "지원하지 않는 검증 규칙";
            }
        }
        return List.of(new AssertionResult(contract.type().name(), passed, message, actual, expected));
    }

    private Object resolve(String source, Map<String, Object> state) {
        if (source == null) return null;
        if (source.startsWith("$scenario.")) return readPath(state, source.substring(10));
        if (source.startsWith("$input.")) return readPath(state, source.substring(7));
        if ("$auth.token".equals(source)) return state.get("authToken");
        return null;
    }

    @SuppressWarnings("unchecked")
    static Object readPath(Object value, String path) {
        if (path == null || path.isBlank() || "$".equals(path)) return value;
        String normalized = path.replaceFirst("^\\$\\.?", "");
        Object current = value;
        for (String part : normalized.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else if (current instanceof List<?> list && part.matches("\\d+")) {
                int index = Integer.parseInt(part);
                current = index < list.size() ? list.get(index) : null;
            } else {
                return null;
            }
            if (current == null) return null;
        }
        return current;
    }

    private boolean collectionContains(Object actual, Object expected) {
        Object collectionValue = actual instanceof Collection<?> ? actual : findCollection(actual, 0);
        if (!(collectionValue instanceof Collection<?> collection) || expected == null) return false;
        return collection.stream().anyMatch(item -> {
            Object candidate = item instanceof Map<?, ?> map
                    ? first(map.get("id"), map.get("uuid"), map.get("key"), item)
                    : item;
            return equal(candidate, expected);
        });
    }

    private Object first(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private Object findCollection(Object value, int depth) {
        if (depth > 5 || value == null) return null;
        if (value instanceof Collection<?>) return value;
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("content", "items", "data", "results", "records", "rows", "payload")) {
                Object nested = map.get(key);
                Object found = findCollection(nested, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Object unwrapEnvelope(Object value) {
        Object current = value;
        for (int depth = 0; depth < 5 && current instanceof Map<?, ?> map; depth++) {
            Object nested = first(map.get("data"), map.get("result"), map.get("payload"));
            if (nested == null) break;
            current = nested;
        }
        return current;
    }

    private boolean equal(Object left, Object right) {
        return Objects.equals(left, right)
                || left != null && right != null && String.valueOf(left).equals(String.valueOf(right));
    }

    private boolean riskAllowed(RiskLevel risk, boolean allowStateChanging) {
        if (risk == null || risk == RiskLevel.SAFE) return true;
        return risk == RiskLevel.STATE_CHANGING && allowStateChanging;
    }

    private boolean missing(Object value) {
        return value == null || value instanceof String string && string.isBlank();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private long elapsed(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "회귀 테스트 요청 실패" : message;
    }

    private boolean sensitiveName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("credential")
                || normalized.contains("apikey");
    }

    private ScenarioResult failed(
            Map<String, Object> state,
            List<StageResult> stages,
            StageResult failure,
            Instant startedAt
    ) {
        return new ScenarioResult(false, immutableMap(state), List.copyOf(stages),
                failure.stageId(), failure.request(), elapsed(startedAt));
    }

    private Map<String, Object> immutableMap(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private record BuiltRequest(
            String url,
            String safeUrl,
            Map<String, String> headers,
            Map<String, Object> body,
            Map<String, Object> snapshot
    ) {
    }

    public record AssertionResult(
            String type,
            boolean passed,
            String message,
            Object actual,
            Object expected
    ) {
    }

    public record StageResult(
            String stageId,
            boolean success,
            String operationId,
            String method,
            String url,
            Map<String, Object> request,
            Integer responseStatus,
            Object response,
            Map<String, Object> extractedOutputs,
            List<AssertionResult> assertions,
            long durationMs,
            String error
    ) {
        public static StageResult passed(
                String stageId, String operationId, String method, String url,
                Map<String, Object> request, Integer responseStatus, Object response,
                Map<String, Object> outputs, List<AssertionResult> assertions, long durationMs
        ) {
            return new StageResult(stageId, true, operationId, method, url, request, responseStatus,
                    response, outputs, assertions, durationMs, null);
        }

        public static StageResult failed(
                String stageId, String operationId, String method, String url,
                Map<String, Object> request, Integer responseStatus, Object response,
                long durationMs, String error
        ) {
            return failed(stageId, operationId, method, url, request, responseStatus, response,
                    durationMs, error, List.of());
        }

        public static StageResult failed(
                String stageId, String operationId, String method, String url,
                Map<String, Object> request, Integer responseStatus, Object response,
                long durationMs, String error, List<AssertionResult> assertions
        ) {
            return new StageResult(stageId, false, operationId, method, url, request, responseStatus,
                    response, Map.of(), assertions, durationMs, error);
        }
    }

    public record ScenarioResult(
            boolean passed,
            Map<String, Object> finalState,
            List<StageResult> stages,
            String failureStageId,
            Map<String, Object> failureRequest,
            long durationMs
    ) {
    }
}
