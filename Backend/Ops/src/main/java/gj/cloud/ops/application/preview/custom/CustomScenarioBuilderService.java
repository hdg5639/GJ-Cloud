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

    @Transactional(readOnly = true)
    public CustomScenarioExport exportScenario(
            String bearerToken,
            String ownerId,
            String scenarioId
    ) {
        requirePro(bearerToken);
        CustomScenarioEntity scenario = ownedScenario(scenarioId, ownerId);
        return new CustomScenarioExport(
                CustomScenarioExport.FORMAT,
                scenario.getName(),
                scenario.getDescription(),
                scenario.getNaturalLanguageSource(),
                scenario.getVisibility(),
                readDefinition(scenario)
        );
    }

    @Transactional
    public CustomScenarioView importScenario(
            String bearerToken,
            String ownerId,
            CustomScenarioImportRequest request
    ) {
        requirePro(bearerToken);
        CustomScenarioExport exported = request.scenario();
        if (!CustomScenarioExport.FORMAT.equals(exported.format())
                || exported.definition() == null
                || blank(exported.name())
                || blank(exported.naturalLanguageSource())
                || exported.name().length() > 100
                || exported.naturalLanguageSource().length() > 4000
                || exported.description() != null && exported.description().length() > 1000
                || !ScenarioValidator.validatePlan(exported.definition()).isEmpty()) {
            throw new OpsException(OpsErrorCode.CUSTOM_SCENARIO_INVALID);
        }

        OpenApiEvidence evidence = openApiNormalizer.normalize(request.apiDocsUrl());
        List<Capability> capabilities = capabilityExtractor.extract(evidence);
        ScenarioCompiler.CompilationResult compilation =
                scenarioCompiler.compile(List.of(exported.definition()), capabilities);
        CompiledScenario compiled = compilation.scenarios().stream()
                .findFirst()
                .orElseThrow(() -> new OpsException(OpsErrorCode.CUSTOM_SCENARIO_GENERATION_FAILED));
        List<String> validationErrors = new ArrayList<>(
                ScenarioValidator.validatePlan(exported.definition()));
        compilation.diagnostics().stream()
                .filter(diagnostic -> diagnostic.status()
                        == gj.cloud.ops.application.preview.scenario.ScenarioModels.DiagnosticStatus.UNSUPPORTED)
                .map(diagnostic -> diagnostic.message())
                .forEach(validationErrors::add);
        boolean valid = validationErrors.isEmpty()
                && compiled.status() == CompilationStatus.EXECUTABLE;
        if (!valid) {
            throw new OpsException(OpsErrorCode.CUSTOM_SCENARIO_INVALID);
        }

        CustomScenarioEntity scenario = CustomScenarioEntity.generating(
                request.serviceId(),
                ownerId,
                exported.name(),
                exported.description(),
                exported.naturalLanguageSource(),
                exported.visibility()
        );
        scenario.markDraft(write(exported.definition()));
        scenario.markValidated();
        scenarioRepository.saveAndFlush(scenario);

        CustomScenarioRevisionEntity revision = CustomScenarioRevisionEntity.create(
                scenario.getId(),
                1,
                OpenApiFingerprint.calculate(evidence, capabilities),
                write(compiled),
                write(new ValidationSnapshot(true, List.of(), "scenario-import-v1")),
                true
        );
        revisionRepository.save(revision);
        return toView(scenario, revision, exported.definition(), compiled, List.of());
    }

    /**
     * 회귀 실행 전용 재검증 경계. 사용자 토큰 없이 호출되므로 owner 소유권을 DB에서 잠금 검증하고,
     * fingerprint가 바뀐 경우에만 의미 정의를 새 OpenAPI capability에 재컴파일한다.
     */
    @Transactional
    public ExecutableRevision ensureExecutableRevision(
            String ownerId,
            String scenarioId,
            OpenApiEvidence evidence,
            List<Capability> capabilities
    ) {
        CustomScenarioEntity scenario = scenarioRepository
                .findLockedByIdAndOwnerId(scenarioId, ownerId)
                .orElseThrow(() -> new OpsException(OpsErrorCode.CUSTOM_SCENARIO_NOT_FOUND));
        CustomScenarioRevisionEntity previous = latestRevision(scenarioId);
        String fingerprint = OpenApiFingerprint.calculate(evidence, capabilities);
        if (fingerprint.equals(previous.getOpenapiFingerprint())) {
            return new ExecutableRevision(
                    previous.getId(),
                    read(previous.getCompiledScenarioJson(), CompiledScenario.class),
                    previous.isValid(),
                    readValidation(previous).errors()
            );
        }

        ScenarioPlan definition = readDefinition(scenario);
        boolean wasActive = scenario.getStatus() == CustomScenarioStatus.ACTIVE;
        scenario.markValidating();
        ScenarioCompiler.CompilationResult compilation =
                scenarioCompiler.compile(List.of(definition), capabilities);
        CompiledScenario compiled = compilation.scenarios().stream()
                .findFirst()
                .orElseThrow(() -> new OpsException(OpsErrorCode.CUSTOM_SCENARIO_GENERATION_FAILED));
        List<String> errors = new ArrayList<>(ScenarioValidator.validatePlan(definition));
        compilation.diagnostics().stream()
                .filter(diagnostic -> diagnostic.status()
                        == gj.cloud.ops.application.preview.scenario.ScenarioModels.DiagnosticStatus.UNSUPPORTED)
                .map(diagnostic -> diagnostic.message())
                .forEach(errors::add);
        boolean valid = errors.isEmpty() && compiled.status() == CompilationStatus.EXECUTABLE;
        if (valid) scenario.completeRevalidation(wasActive);
        else scenario.invalidate();

        CustomScenarioRevisionEntity revision = CustomScenarioRevisionEntity.create(
                scenarioId,
                previous.getRevision() + 1,
                fingerprint,
                write(compiled),
                write(new ValidationSnapshot(valid, errors, "regression-auto-recompile")),
                valid
        );
        revisionRepository.save(revision);
        return new ExecutableRevision(revision.getId(), compiled, valid, errors);
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

    private ValidationSnapshot readValidation(CustomScenarioRevisionEntity revision) {
        return read(revision.getValidationResultJson(), ValidationSnapshot.class);
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("저장된 Custom Scenario 데이터를 읽지 못했습니다.", error);
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

    public record ExecutableRevision(
            String revisionId,
            CompiledScenario compiledScenario,
            boolean valid,
            List<String> validationErrors
    ) {
        public ExecutableRevision {
            validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
        }
    }
}
