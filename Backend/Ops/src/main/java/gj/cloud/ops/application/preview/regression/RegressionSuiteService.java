package gj.cloud.ops.application.preview.regression;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.preview.regression.RegressionViews.RunView;
import gj.cloud.ops.application.preview.regression.RegressionViews.ScenarioExecutionView;
import gj.cloud.ops.application.preview.regression.RegressionViews.SuiteView;
import gj.cloud.ops.application.preview.analysis.OpenApiDocumentSecurityValidator;
import gj.cloud.ops.application.userclient.UserPlanClient;
import gj.cloud.ops.domain.preview.entity.CustomScenarioEntity;
import gj.cloud.ops.domain.preview.entity.RegressionSuiteEntity;
import gj.cloud.ops.domain.preview.entity.RegressionSuiteRunEntity;
import gj.cloud.ops.domain.preview.entity.ScenarioExecutionEntity;
import gj.cloud.ops.domain.preview.enums.CustomScenarioStatus;
import gj.cloud.ops.domain.preview.enums.RegressionTriggerType;
import gj.cloud.ops.domain.preview.repository.CustomScenarioRepository;
import gj.cloud.ops.domain.preview.repository.CustomScenarioRevisionRepository;
import gj.cloud.ops.domain.preview.repository.RegressionSuiteRepository;
import gj.cloud.ops.domain.preview.repository.RegressionSuiteRunRepository;
import gj.cloud.ops.domain.preview.repository.ScenarioExecutionRepository;
import gj.cloud.ops.domain.deployment.repository.DeploymentTargetRepository;
import gj.cloud.ops.global.crypto.AesGcmCipher;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Slf4j
public class RegressionSuiteService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final UserPlanClient userPlanClient;
    private final RegressionSuiteRepository suiteRepository;
    private final RegressionSuiteRunRepository runRepository;
    private final ScenarioExecutionRepository executionRepository;
    private final CustomScenarioRepository scenarioRepository;
    private final CustomScenarioRevisionRepository revisionRepository;
    private final DeploymentTargetRepository deploymentTargetRepository;
    private final RegressionTargetSecurityValidator targetSecurityValidator;
    private final OpenApiDocumentSecurityValidator documentSecurityValidator;
    private final RegressionRunWorker runWorker;
    private final TaskExecutor regressionTaskExecutor;
    private final AesGcmCipher cipher;
    private final ObjectMapper objectMapper;

    public RegressionSuiteService(
            UserPlanClient userPlanClient,
            RegressionSuiteRepository suiteRepository,
            RegressionSuiteRunRepository runRepository,
            ScenarioExecutionRepository executionRepository,
            CustomScenarioRepository scenarioRepository,
            CustomScenarioRevisionRepository revisionRepository,
            DeploymentTargetRepository deploymentTargetRepository,
            RegressionTargetSecurityValidator targetSecurityValidator,
            OpenApiDocumentSecurityValidator documentSecurityValidator,
            RegressionRunWorker runWorker,
            @Qualifier("regressionTaskExecutor") TaskExecutor regressionTaskExecutor,
            AesGcmCipher cipher,
            ObjectMapper objectMapper
    ) {
        this.userPlanClient = userPlanClient;
        this.suiteRepository = suiteRepository;
        this.runRepository = runRepository;
        this.executionRepository = executionRepository;
        this.scenarioRepository = scenarioRepository;
        this.revisionRepository = revisionRepository;
        this.deploymentTargetRepository = deploymentTargetRepository;
        this.targetSecurityValidator = targetSecurityValidator;
        this.documentSecurityValidator = documentSecurityValidator;
        this.runWorker = runWorker;
        this.regressionTaskExecutor = regressionTaskExecutor;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
    }

    public SuiteView create(
            String bearerToken,
            String ownerId,
            RegressionSuiteCreateRequest request
    ) {
        requirePro(bearerToken);
        documentSecurityValidator.validate(request.apiDocsUrl());
        targetSecurityValidator.validate(request.apiBaseUrl());
        validateScenarios(ownerId, request.scenarioIds());
        validateDeploymentTrigger(ownerId, request.deploymentTargetId(), request.runOnDeployment());
        RegressionSuiteEntity suite = RegressionSuiteEntity.create(
                request.serviceId(),
                ownerId,
                request.name().trim(),
                request.description(),
                request.apiDocsUrl(),
                request.apiBaseUrl(),
                write(request.scenarioIds()),
                request.deploymentTargetId(),
                request.runOnDeployment(),
                request.allowStateChangingOnDeployment()
        );
        return toView(suiteRepository.save(suite));
    }

    @Transactional(readOnly = true)
    public List<SuiteView> list(String bearerToken, String ownerId, String serviceId) {
        requirePro(bearerToken);
        return suiteRepository
                .findAllByServiceIdAndOwnerIdAndActiveTrueOrderByUpdatedAtDesc(serviceId, ownerId)
                .stream().map(this::toView).toList();
    }

    public RunView enqueue(
            String bearerToken,
            String ownerId,
            String suiteId,
            RegressionRunRequest request,
            RegressionTriggerType triggerType
    ) {
        requirePro(bearerToken);
        RegressionSuiteEntity suite = ownedSuite(suiteId, ownerId);
        validateRunRequest(request);
        return enqueueInternal(suite, request, triggerType, null);
    }

    public void triggerForDeployment(String deploymentTargetId, String deploymentId) {
        for (RegressionSuiteEntity suite : suiteRepository
                .findAllByDeploymentTargetIdAndRunOnDeploymentTrueAndActiveTrue(deploymentTargetId)) {
            try {
                enqueueInternal(
                        suite,
                        RegressionRunRequest.automated(suite.isAllowStateChangingOnDeployment()),
                        RegressionTriggerType.DEPLOYMENT,
                        deploymentId);
            } catch (RuntimeException error) {
                log.warn("배포 연동 회귀 테스트 예약 실패: suiteId={}, deploymentId={}, error={}",
                        suite.getId(), deploymentId, error.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<RunView> listRuns(
            String bearerToken, String ownerId, String suiteId
    ) {
        requirePro(bearerToken);
        ownedSuite(suiteId, ownerId);
        return runRepository.findTop30BySuiteIdAndOwnerIdOrderByCreatedAtDesc(suiteId, ownerId)
                .stream().map(run -> toRunView(run, false)).toList();
    }

    @Transactional(readOnly = true)
    public RunView getRun(String bearerToken, String ownerId, String runId) {
        requirePro(bearerToken);
        RegressionSuiteRunEntity run = runRepository.findByIdAndOwnerId(runId, ownerId)
                .orElseThrow(() -> new OpsException(OpsErrorCode.REGRESSION_RUN_NOT_FOUND));
        return toRunView(run, true);
    }

    public void deactivate(String bearerToken, String ownerId, String suiteId) {
        requirePro(bearerToken);
        RegressionSuiteEntity suite = ownedSuite(suiteId, ownerId);
        suite.deactivate();
        suiteRepository.save(suite);
    }

    private RunView enqueueInternal(
            RegressionSuiteEntity suite,
            RegressionRunRequest request,
            RegressionTriggerType triggerType,
            String triggerReference
    ) {
        String inputCiphertext = cipher.encrypt(
                write(request).getBytes(StandardCharsets.UTF_8));
        RegressionSuiteRunEntity run = runRepository.save(
                RegressionSuiteRunEntity.queued(
                        suite.getId(), suite.getOwnerId(), triggerType, triggerReference, inputCiphertext));
        try {
            regressionTaskExecutor.execute(() -> runWorker.run(run.getId()));
        } catch (RuntimeException error) {
            run.fail(write(java.util.Map.of("error", "회귀 테스트 워커가 요청을 수락하지 못했습니다.")));
            runRepository.save(run);
            throw error;
        }
        return toRunView(run, false);
    }

    private void validateScenarios(String ownerId, List<String> scenarioIds) {
        for (String scenarioId : scenarioIds.stream().distinct().toList()) {
            CustomScenarioEntity scenario = scenarioRepository.findByIdAndOwnerId(scenarioId, ownerId)
                    .orElseThrow(() -> new OpsException(OpsErrorCode.REGRESSION_SUITE_INVALID));
            boolean active = scenario.getStatus() == CustomScenarioStatus.ACTIVE
                    || scenario.getStatus() == CustomScenarioStatus.VALIDATED;
            boolean validRevision = revisionRepository
                    .findTopByScenarioIdOrderByRevisionDesc(scenarioId)
                    .map(revision -> revision.isValid())
                    .orElse(false);
            if (!active || !validRevision) {
                throw new OpsException(OpsErrorCode.REGRESSION_SUITE_INVALID);
            }
        }
    }

    private void validateDeploymentTrigger(
            String ownerId,
            String deploymentTargetId,
            boolean runOnDeployment
    ) {
        if (!runOnDeployment && (deploymentTargetId == null || deploymentTargetId.isBlank())) return;
        if (deploymentTargetId == null || deploymentTargetId.isBlank()
                || deploymentTargetRepository
                        .findByIdAndOwnerUserIdAndActiveTrue(deploymentTargetId, ownerId)
                        .isEmpty()) {
            throw new OpsException(OpsErrorCode.REGRESSION_SUITE_INVALID);
        }
    }

    private void validateRunRequest(RegressionRunRequest request) {
        String json = write(request);
        boolean invalidHeader = request.headers().entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getKey().isBlank() || entry.getKey().length() > 100
                        || entry.getValue() == null || entry.getValue().length() > 8192
                        || entry.getValue().contains("\r") || entry.getValue().contains("\n"));
        if (invalidHeader || json.length() > 131_072) {
            throw new OpsException(OpsErrorCode.REGRESSION_SUITE_INVALID);
        }
    }

    private RegressionSuiteEntity ownedSuite(String suiteId, String ownerId) {
        return suiteRepository.findByIdAndOwnerIdAndActiveTrue(suiteId, ownerId)
                .orElseThrow(() -> new OpsException(OpsErrorCode.REGRESSION_SUITE_NOT_FOUND));
    }

    private SuiteView toView(RegressionSuiteEntity suite) {
        return new SuiteView(
                suite.getId(),
                suite.getServiceId(),
                suite.getName(),
                suite.getDescription(),
                suite.getApiDocsUrl(),
                suite.getApiBaseUrl(),
                readStringList(suite.getScenarioIdsJson()),
                suite.getDeploymentTargetId(),
                suite.isRunOnDeployment(),
                suite.isAllowStateChangingOnDeployment(),
                suite.getCreatedAt(),
                suite.getUpdatedAt()
        );
    }

    private RunView toRunView(RegressionSuiteRunEntity run, boolean includeExecutions) {
        List<ScenarioExecutionView> executions = includeExecutions
                ? executionRepository.findAllBySuiteRunIdOrderByStartedAtAsc(run.getId())
                        .stream().map(this::toExecutionView).toList()
                : List.of();
        return new RunView(
                run.getId(),
                run.getSuiteId(),
                run.getStatus(),
                run.getTriggerType(),
                run.getTriggerReference(),
                run.getTotalCount(),
                run.getPassedCount(),
                run.getFailedCount(),
                readJson(run.getSummaryJson()),
                executions,
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getCreatedAt()
        );
    }

    private ScenarioExecutionView toExecutionView(ScenarioExecutionEntity execution) {
        return new ScenarioExecutionView(
                execution.getId(),
                execution.getScenarioId(),
                execution.getScenarioRevisionId(),
                execution.getExecutionStatus(),
                readJson(execution.getInputSnapshotJson()),
                readJson(execution.getStateSnapshotJson()),
                readJson(execution.getResultSummaryJson()),
                execution.getFailureStageId(),
                readJson(execution.getFailureRequestJson()),
                execution.getStartedAt(),
                execution.getCompletedAt()
        );
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception error) {
            throw new IllegalStateException("회귀 테스트 스위트 정의를 읽지 못했습니다.", error);
        }
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception error) {
            return java.util.Map.of("unreadable", true);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("회귀 테스트 데이터를 직렬화하지 못했습니다.", error);
        }
    }

    private void requirePro(String bearerToken) {
        if (!userPlanClient.isPro(bearerToken)) {
            throw new OpsException(OpsErrorCode.CUSTOM_SCENARIO_PRO_REQUIRED);
        }
    }
}
