package gj.cloud.ops.application.preview.custom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityExtractor;
import gj.cloud.ops.application.preview.analysis.OpenApiEvidence;
import gj.cloud.ops.application.preview.analysis.OpenApiNormalizer;
import gj.cloud.ops.application.preview.scenario.ScenarioCompiler;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenario;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompilationStatus;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioPlan;
import gj.cloud.ops.application.preview.scenario.ScenarioValidator;
import gj.cloud.ops.application.preview.scenario.ai.AiScenarioPlanner;
import gj.cloud.ops.application.userclient.UserPlanClient;
import gj.cloud.ops.domain.preview.entity.CustomScenarioEntity;
import gj.cloud.ops.domain.preview.entity.CustomScenarioRevisionEntity;
import gj.cloud.ops.domain.preview.enums.CustomScenarioStatus;
import gj.cloud.ops.domain.preview.repository.CustomScenarioRepository;
import gj.cloud.ops.domain.preview.repository.CustomScenarioRevisionRepository;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomScenarioBuilderService {

    private final UserPlanClient userPlanClient;
    private final OpenApiNormalizer openApiNormalizer;
    private final CapabilityExtractor capabilityExtractor;
    private final AiScenarioPlanner aiScenarioPlanner;
    private final ScenarioCompiler scenarioCompiler;
    private final CustomScenarioRepository scenarioRepository;
    private final CustomScenarioRevisionRepository revisionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public CustomScenarioView generate(
            String bearerToken,
            String ownerId,
            CustomScenarioGenerateRequest request
    ) {
        requirePro(bearerToken);
        OpenApiEvidence evidence = openApiNormalizer.normalize(request.apiDocsUrl());
        List<Capability> capabilities = capabilityExtractor.extract(evidence);
        var attempt = aiScenarioPlanner.plan(
                ownerId,
                evidence,
                request.naturalLanguageSource(),
                request.purpose(),
                capabilities
        );
        ScenarioPlan definition = attempt.proposal().plans().stream()
                .filter(plan -> ScenarioValidator.validatePlan(plan).isEmpty())
                .findFirst()
                .orElseThrow(() -> new OpsException(OpsErrorCode.CUSTOM_SCENARIO_GENERATION_FAILED));

        ScenarioCompiler.CompilationResult compilation =
                scenarioCompiler.compile(List.of(definition), capabilities);
        CompiledScenario compiled = compilation.scenarios().stream()
                .findFirst()
                .orElseThrow(() -> new OpsException(OpsErrorCode.CUSTOM_SCENARIO_GENERATION_FAILED));
        List<String> validationErrors = new ArrayList<>(ScenarioValidator.validatePlan(definition));
        compilation.diagnostics().stream()
                .filter(diagnostic -> diagnostic.status()
                        == gj.cloud.ops.application.preview.scenario.ScenarioModels.DiagnosticStatus.UNSUPPORTED)
                .map(diagnostic -> diagnostic.message())
                .forEach(validationErrors::add);
        boolean valid = validationErrors.isEmpty()
                && compiled.status() == CompilationStatus.EXECUTABLE;
        String fingerprint = OpenApiFingerprint.calculate(evidence, capabilities);

        String effectiveName = blank(request.name()) ? definition.name() : request.name().trim();
        CustomScenarioEntity scenario = CustomScenarioEntity.generating(
                request.serviceId(),
                ownerId,
                effectiveName,
                request.description(),
                request.naturalLanguageSource().trim(),
                request.visibility()
        );
        scenario.markDraft(write(definition));
        if (valid) scenario.markValidated();
        scenarioRepository.saveAndFlush(scenario);

        CustomScenarioRevisionEntity revision = CustomScenarioRevisionEntity.create(
                scenario.getId(),
                1,
                fingerprint,
                write(compiled),
                write(new ValidationSnapshot(valid, validationErrors, attempt.promptVersion())),
                valid
        );
        revisionRepository.save(revision);
        return toView(scenario, revision, definition, compiled, validationErrors);
    }

    @Transactional(readOnly = true)
    public List<CustomScenarioView> list(String bearerToken, String ownerId, String serviceId) {
        requirePro(bearerToken);
        return scenarioRepository
                .findAllByServiceIdAndOwnerIdAndStatusNotOrderByUpdatedAtDesc(
                        serviceId, ownerId, CustomScenarioStatus.ARCHIVED)
                .stream()
                .map(this::latestView)
                .toList();
    }

    @Transactional
    public CustomScenarioView activate(String bearerToken, String ownerId, String scenarioId) {
        requirePro(bearerToken);
        CustomScenarioEntity scenario = ownedScenario(scenarioId, ownerId);
        CustomScenarioRevisionEntity revision = latestRevision(scenarioId);
        if (!revision.isValid()) {
            throw new OpsException(OpsErrorCode.CUSTOM_SCENARIO_INVALID);
        }
        scenario.activate();
        return toView(scenario, revision);
    }

    @Transactional
    public CustomScenarioView revalidate(
            String bearerToken,
            String ownerId,
            String scenarioId,
            CustomScenarioRevalidateRequest request
    ) {
        requirePro(bearerToken);
        CustomScenarioEntity scenario = ownedScenario(scenarioId, ownerId);
        CustomScenarioRevisionEntity previous = latestRevision(scenarioId);
        OpenApiEvidence evidence = openApiNormalizer.normalize(request.apiDocsUrl());
        List<Capability> capabilities = capabilityExtractor.extract(evidence);
        String fingerprint = OpenApiFingerprint.calculate(evidence, capabilities);
        if (fingerprint.equals(previous.getOpenapiFingerprint())) {
            return toView(scenario, previous);
        }

        ScenarioPlan definition = readDefinition(scenario);
        boolean wasActive = scenario.getStatus() == CustomScenarioStatus.ACTIVE;
        scenario.markValidating();
        ScenarioCompiler.CompilationResult compilation =
                scenarioCompiler.compile(List.of(definition), capabilities);
        CompiledScenario compiled = compilation.scenarios().stream()
                .findFirst()
                .orElseThrow(() -> new OpsException(OpsErrorCode.CUSTOM_SCENARIO_GENERATION_FAILED));
        List<String> validationErrors = new ArrayList<>(ScenarioValidator.validatePlan(definition));
        compilation.diagnostics().stream()
                .filter(diagnostic -> diagnostic.status()
                        == gj.cloud.ops.application.preview.scenario.ScenarioModels.DiagnosticStatus.UNSUPPORTED)
                .map(diagnostic -> diagnostic.message())
                .forEach(validationErrors::add);
        boolean valid = validationErrors.isEmpty()
                && compiled.status() == CompilationStatus.EXECUTABLE;
        if (valid) scenario.completeRevalidation(wasActive);
        else scenario.invalidate();

        CustomScenarioRevisionEntity revision = CustomScenarioRevisionEntity.create(
                scenarioId,
                previous.getRevision() + 1,
                fingerprint,
                write(compiled),
                write(new ValidationSnapshot(valid, validationErrors, "deterministic-recompile")),
                valid
        );
        revisionRepository.save(revision);
        return toView(scenario, revision, definition, compiled, validationErrors);
    }

    private CustomScenarioView latestView(CustomScenarioEntity scenario) {
        return toView(scenario, latestRevision(scenario.getId()));
    }

    private CustomScenarioView toView(
            CustomScenarioEntity scenario,
            CustomScenarioRevisionEntity revision
    ) {
        try {
            ScenarioPlan definition =
                    objectMapper.readValue(scenario.getScenarioDefinitionJson(), ScenarioPlan.class);
            CompiledScenario compiled =
                    objectMapper.readValue(revision.getCompiledScenarioJson(), CompiledScenario.class);
            ValidationSnapshot validation =
                    objectMapper.readValue(revision.getValidationResultJson(), ValidationSnapshot.class);
            return toView(scenario, revision, definition, compiled, validation.errors());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("저장된 Custom Scenario 리비전을 읽지 못했습니다.", error);
        }
    }

    private CustomScenarioView toView(
            CustomScenarioEntity scenario,
            CustomScenarioRevisionEntity revision,
            ScenarioPlan definition,
            CompiledScenario compiled,
            List<String> errors
    ) {
        return new CustomScenarioView(
                scenario.getId(),
                scenario.getServiceId(),
                scenario.getName(),
                scenario.getDescription(),
                scenario.getNaturalLanguageSource(),
                scenario.getStatus(),
                scenario.getVisibility(),
                definition,
                revision.getRevision(),
                revision.getOpenapiFingerprint(),
                compiled,
                revision.isValid(),
                errors,
                scenario.getCreatedAt(),
                scenario.getUpdatedAt()
        );
    }

    private CustomScenarioEntity ownedScenario(String scenarioId, String ownerId) {
        return scenarioRepository.findByIdAndOwnerId(scenarioId, ownerId)
                .orElseThrow(() -> new OpsException(OpsErrorCode.CUSTOM_SCENARIO_NOT_FOUND));
    }

    private CustomScenarioRevisionEntity latestRevision(String scenarioId) {
        return revisionRepository.findTopByScenarioIdOrderByRevisionDesc(scenarioId)
                .orElseThrow(() -> new OpsException(OpsErrorCode.CUSTOM_SCENARIO_NOT_FOUND));
    }

    private ScenarioPlan readDefinition(CustomScenarioEntity scenario) {
        try {
            return objectMapper.readValue(scenario.getScenarioDefinitionJson(), ScenarioPlan.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("저장된 Custom Scenario 정의를 읽지 못했습니다.", error);
        }
    }

    private void requirePro(String bearerToken) {
        if (!userPlanClient.isPro(bearerToken)) {
            throw new OpsException(OpsErrorCode.CUSTOM_SCENARIO_PRO_REQUIRED);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Custom Scenario를 직렬화하지 못했습니다.", error);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ValidationSnapshot(
            boolean valid,
            List<String> errors,
            String promptVersion
    ) {
        private ValidationSnapshot {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }
}
