package gj.cloud.ops.application.preview.regression;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityExtractor;
import gj.cloud.ops.application.preview.analysis.OpenApiEvidence;
import gj.cloud.ops.application.preview.analysis.OpenApiNormalizer;
import gj.cloud.ops.application.preview.custom.CustomScenarioBuilderService;
import gj.cloud.ops.domain.preview.entity.RegressionSuiteEntity;
import gj.cloud.ops.domain.preview.entity.RegressionSuiteRunEntity;
import gj.cloud.ops.domain.preview.entity.ScenarioExecutionEntity;
import gj.cloud.ops.domain.preview.enums.ScenarioExecutionStatus;
import gj.cloud.ops.domain.preview.repository.RegressionSuiteRepository;
import gj.cloud.ops.domain.preview.repository.RegressionSuiteRunRepository;
import gj.cloud.ops.domain.preview.repository.ScenarioExecutionRepository;
import gj.cloud.ops.global.crypto.AesGcmCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class RegressionRunWorker {

    private static final int MAX_PERSISTED_RESULT_CHARS = 524_288;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final RegressionSuiteRepository suiteRepository;
    private final RegressionSuiteRunRepository runRepository;
    private final ScenarioExecutionRepository executionRepository;
    private final CustomScenarioBuilderService customScenarioBuilderService;
    private final OpenApiNormalizer openApiNormalizer;
    private final CapabilityExtractor capabilityExtractor;
    private final RegressionTargetSecurityValidator targetSecurityValidator;
    private final ScenarioRegressionEngine engine;
    private final RegressionDataRedactor redactor;
    private final AesGcmCipher cipher;
    private final ObjectMapper objectMapper;

    public void run(String runId) {
        RegressionSuiteRunEntity run = runRepository.findById(runId).orElse(null);
        if (run == null) return;
        try {
            RegressionSuiteEntity suite = suiteRepository.findById(run.getSuiteId())
                    .filter(RegressionSuiteEntity::isActive)
                    .orElseThrow(() -> new IllegalStateException("회귀 테스트 스위트가 비활성화되었습니다."));
            RegressionRunRequest request = readEncryptedRequest(run.getInputCiphertext());
            List<String> scenarioIds = readScenarioIds(suite.getScenarioIdsJson());
            run.start(scenarioIds.size());
            runRepository.save(run);

            targetSecurityValidator.validate(suite.getApiBaseUrl());
            OpenApiEvidence evidence = openApiNormalizer.normalize(suite.getApiDocsUrl());
            Map<String, Capability> capabilities = capabilityExtractor.extract(evidence).stream()
                    .collect(Collectors.toMap(
                            Capability::id, Function.identity(), (left, right) -> left, LinkedHashMap::new));

            int passed = 0;
            int failed = 0;
            List<Map<String, Object>> summaries = new ArrayList<>();
            for (String scenarioId : scenarioIds) {
                ScenarioExecutionEntity execution = executeOne(
                        run, suite, scenarioId, evidence, capabilities, request);
                executionRepository.save(execution);
                boolean success = execution.getExecutionStatus() == ScenarioExecutionStatus.PASSED;
                if (success) passed++;
                else failed++;
                summaries.add(Map.of(
                        "scenarioId", scenarioId,
                        "status", execution.getExecutionStatus(),
                        "failureStageId", execution.getFailureStageId() == null
                                ? "" : execution.getFailureStageId()));
                if (!success && request.failFast()) break;
            }
            run.complete(passed, failed, write(Map.of(
                    "passed", passed,
                    "failed", failed,
                    "scenarios", summaries
            )));
            runRepository.save(run);
        } catch (Exception error) {
            log.error("회귀 테스트 실행 실패: runId={}, error={}", runId, error.getMessage(), error);
            run.fail(write(Map.of("error", safeMessage(error))));
            runRepository.save(run);
        } finally {
            // 실행 중에만 필요한 원본 토큰·헤더는 이력에 장기 보관하지 않는다. 상세 이력에는 위에서
            // redaction한 스냅샷만 남고, 암호화 입력도 작업 종료 즉시 빈 자동실행 입력으로 교체한다.
            run.replaceSensitiveInput(cipher.encrypt(
                    write(RegressionRunRequest.automated(false)).getBytes(StandardCharsets.UTF_8)));
            runRepository.save(run);
        }
    }

    private ScenarioExecutionEntity executeOne(
            RegressionSuiteRunEntity run,
            RegressionSuiteEntity suite,
            String scenarioId,
            OpenApiEvidence evidence,
            Map<String, Capability> capabilities,
            RegressionRunRequest request
    ) {
        LocalDateTime startedAt = LocalDateTime.now();
        CustomScenarioBuilderService.ExecutableRevision revision =
                customScenarioBuilderService.ensureExecutableRevision(
                        suite.getOwnerId(), scenarioId, evidence, List.copyOf(capabilities.values()));
        if (!revision.valid()) {
            return ScenarioExecutionEntity.completed(
                    run.getId(),
                    scenarioId,
                    revision.revisionId(),
                    ScenarioExecutionStatus.FAILED,
                    write(redactor.redact(request.initialState())),
                    write(redactor.redact(request.initialState())),
                    write(Map.of(
                            "error", "OpenAPI 변경 후 시나리오 재검증에 실패했습니다.",
                            "validationErrors", revision.validationErrors())),
                    null,
                    null,
                    startedAt,
                    LocalDateTime.now()
            );
        }

        ScenarioRegressionEngine.ScenarioResult result = engine.execute(
                revision.compiledScenario(),
                capabilities,
                suite.getApiBaseUrl(),
                request.initialState(),
                request.headers(),
                request.allowStateChanging()
        );
        Object redactedInput = redactor.redact(Map.of(
                "initialState", request.initialState(),
                "headers", request.headers(),
                "allowStateChanging", request.allowStateChanging()));
        Object redactedState = redactor.redact(result.finalState());
        Object redactedResult = redactor.redact(
                objectMapper.convertValue(result, Object.class));
        Object redactedFailureRequest = redactor.redact(result.failureRequest());
        return ScenarioExecutionEntity.completed(
                run.getId(),
                scenarioId,
                revision.revisionId(),
                result.passed() ? ScenarioExecutionStatus.PASSED : ScenarioExecutionStatus.FAILED,
                write(redactedInput),
                write(redactedState),
                writeBounded(redactedResult),
                result.failureStageId(),
                result.failureRequest() == null ? null : write(redactedFailureRequest),
                startedAt,
                LocalDateTime.now()
        );
    }

    private RegressionRunRequest readEncryptedRequest(String ciphertext) {
        byte[] plain = cipher.decrypt(ciphertext);
        try {
            return objectMapper.readValue(plain, RegressionRunRequest.class);
        } catch (Exception error) {
            throw new IllegalStateException("회귀 테스트 실행 입력을 읽지 못했습니다.", error);
        } finally {
            java.util.Arrays.fill(plain, (byte) 0);
        }
    }

    private List<String> readScenarioIds(String json) {
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception error) {
            throw new IllegalStateException("회귀 테스트 스위트 시나리오 목록을 읽지 못했습니다.", error);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("회귀 테스트 결과를 직렬화하지 못했습니다.", error);
        }
    }

    private String writeBounded(Object value) {
        String json = write(value);
        if (json.length() <= MAX_PERSISTED_RESULT_CHARS) return json;
        return write(Map.of(
                "truncated", true,
                "message", "상세 응답이 저장 허용 크기를 초과했습니다.",
                "originalCharacters", json.length()));
    }

    private String safeMessage(Throwable error) {
        return error.getMessage() == null ? "회귀 테스트 실행 실패" : error.getMessage();
    }
}
