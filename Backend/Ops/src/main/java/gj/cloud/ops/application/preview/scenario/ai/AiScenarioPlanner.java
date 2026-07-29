package gj.cloud.ops.application.preview.scenario.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputMessage;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.OpenApiEvidence;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.application.preview.scenario.ai.ScenarioProposalNormalizer.NormalizedProposal;
import gj.cloud.ops.domain.deployment.enums.AiCallKind;
import gj.cloud.ops.domain.preview.entity.AiPreviewGenerationLogEntity;
import gj.cloud.ops.domain.preview.repository.AiPreviewGenerationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 의미 추론만 담당한다. 실제 endpoint 선택과 binding은 ScenarioCompiler의 권한이다.
 */
@Component
@Slf4j
public class AiScenarioPlanner {

    public static final String PROMPT_VERSION = "scenario-planner-v1";
    private static final int MAX_CAPABILITY_INPUTS = 120;
    private static final int MAX_OPERATION_INPUTS = 160;
    private static final String SYSTEM_PROMPT = """
            You are the semantic service-understanding and scenario-planning layer for GamjaBox Auto Preview.
            Your output is untrusted and will be compiled against a deterministic capability catalog.

            Infer the backend domain, service type, realistic actors, core entities, and high-value user goals.
            Then propose 1 to 6 concise scenarios that exercise meaningful multi-operation behavior rather than
            isolated CRUD screens. Prefer these patterns when supported by evidence: authenticate then access
            protected data; query then select and inspect; create then verify through a separate read; update then
            verify; state transition then verify; asynchronous command then track to a terminal state.

            Hard rules:
            - Use only the supplied capability ids as capabilityRequirement. Never emit an HTTP path, method,
              operationId, URL, component id, page id, JavaScript, shell command, or direct runtime binding.
            - A scenario is semantic. Do not choose UI components or layouts.
            - Each scenario must have 2..16 unique stages in topological order and exactly one reachable COMPLETE
              stage. nextStageIds must reference only stages in the same scenario. Do not create cycles.
            - Every state input must be listed in scenarioState and produced by an earlier stage. PREPARE,
              SELECT_CONTEXT, SELECT, and AUTHENTICATE may introduce user/session state.
            - State-changing COMMIT stages must be preceded by REVIEW. Verification must use a different observable
              read capability when one exists. Never auto-chain destructive behavior.
            - capabilityRequirement is null for PREPARE, REVIEW, SELECT, WAIT, and COMPLETE local stages.
            - For API stage outputs use the current compiler vocabulary only: AUTHENTICATE produces authToken;
              a collection query produces collection or authenticatedCollection; CREATE produces createdId;
              detail/verification reads produce selectedResource or verifiedResource; TRACK produces trackedStatus.
              Local PREPARE/SELECT stages may produce request fields and selectedId.
            - Confidence must be 0..1. Ground evidence in supplied schemas, summaries, tags, enums, and capability
              relationships. Low evidence means low confidence, not invented detail.
            - Stage intent, scenario name, goal, actor label, and primary goals must be written in Korean.
              Stable ids, enum values, and capability ids keep their machine-readable form.

            The first stage in each scenarios list is its entry stage. Keep the proposal small and prioritize the
            scenarios that best explain what this service is for.
            """;

    private final OpenAIClient client;
    private final String model;
    private final ObjectMapper objectMapper;
    private final AiPreviewGenerationLogRepository logRepository;
    private final ScenarioProposalNormalizer normalizer;

    public AiScenarioPlanner(
            OpenAIClient client,
            @Value("${ai.model.standard}") String model,
            ObjectMapper objectMapper,
            AiPreviewGenerationLogRepository logRepository,
            ScenarioProposalNormalizer normalizer
    ) {
        this.client = client;
        this.model = model;
        this.objectMapper = objectMapper;
        this.logRepository = logRepository;
        this.normalizer = normalizer;
    }

    public PlanningAttempt plan(
            String requesterUserId,
            OpenApiEvidence evidence,
            String serviceDescription,
            Purpose purpose,
            List<Capability> capabilities
    ) {
        long inputTokens = 0;
        long outputTokens = 0;
        boolean succeeded = false;
        long startedAt = System.nanoTime();
        try {
            String input = objectMapper.writeValueAsString(toInput(
                    evidence, serviceDescription, purpose, capabilities));
            StructuredResponseCreateParams<AiScenarioProposal> params = ResponseCreateParams.builder()
                    .model(model)
                    .reasoning(Reasoning.builder().effort(ReasoningEffort.MEDIUM).build())
                    .instructions(SYSTEM_PROMPT)
                    .input(input)
                    .text(AiScenarioProposal.class)
                    .build();
            StructuredResponse<AiScenarioProposal> response = client.responses().create(params);
            AiScenarioProposal proposal = response.output().stream()
                    .filter(item -> item.message().isPresent())
                    .flatMap(item -> item.message().get().content().stream())
                    .filter(StructuredResponseOutputMessage.Content::isOutputText)
                    .map(StructuredResponseOutputMessage.Content::asOutputText)
                    .findFirst()
                    .orElse(null);
            inputTokens = response.usage().map(usage -> usage.inputTokens()).orElse(0L);
            outputTokens = response.usage().map(usage -> usage.outputTokens()).orElse(0L);
            Set<String> capabilityIds = capabilities.stream().map(Capability::id).collect(Collectors.toSet());
            NormalizedProposal normalized = normalizer.normalize(proposal, capabilityIds);
            succeeded = normalized.understanding() != null && !normalized.plans().isEmpty();
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("EVENT scenario.planned source=LLM promptVersion={} succeeded={} scenarios={} errors={} "
                            + "durationMs={}", PROMPT_VERSION, succeeded, normalized.plans().size(),
                    normalized.errors().size(), durationMs);
            return new PlanningAttempt(normalized, succeeded, PROMPT_VERSION);
        } catch (Exception error) {
            log.warn("Auto Preview AI scenario planning 실패 — 규칙 기반으로 대체: {}", error.getMessage());
            return new PlanningAttempt(
                    new NormalizedProposal(null, List.of(), List.of("AI scenario planning 실패: " + error.getMessage())),
                    false, PROMPT_VERSION);
        } finally {
            saveAuditLog(requesterUserId, inputTokens, outputTokens, succeeded);
        }
    }

    private PlanningInput toInput(
            OpenApiEvidence evidence,
            String serviceDescription,
            Purpose purpose,
            List<Capability> capabilities
    ) {
        List<CapabilitySummary> capabilitySummaries = capabilities.stream()
                .limit(MAX_CAPABILITY_INPUTS)
                .map(capability -> new CapabilitySummary(
                        capability.id(), capability.resourceName(),
                        capability.kind() == null ? null : capability.kind().name(),
                        capability.type() == null ? null : capability.type().name(),
                        capability.action(), capability.fields(), capability.dependencies(),
                        capability.risk().name(),
                        capability.pollHint() == null ? null : capability.pollHint().statusPath(),
                        capability.pollHint() == null ? List.of() : capability.pollHint().terminalValues(),
                        capability.evidence()))
                .toList();
        List<OperationSummary> operations = evidence.operations().stream()
                .limit(MAX_OPERATION_INPUTS)
                .map(operation -> new OperationSummary(
                        operation.operationId(), operation.summary(), operation.tags(), operation.requiresAuth(),
                        operation.parameters().stream()
                                .map(parameter -> parameter.name() + ":" + parameter.in() + ":" + parameter.type()
                                        + (parameter.required() ? ":required" : ""))
                                .toList(),
                        operation.requestBodyFields(), operation.responseFieldPaths(),
                        operation.enumFields().stream()
                                .map(enumField -> enumField.path() + "=" + enumField.values())
                                .toList()))
                .toList();
        return new PlanningInput(
                PROMPT_VERSION, serviceDescription, purpose == null ? null : purpose.name(),
                evidence.title(), evidence.version(), capabilitySummaries, operations
        );
    }

    private void saveAuditLog(String requesterUserId, long inputTokens, long outputTokens, boolean succeeded) {
        try {
            logRepository.save(AiPreviewGenerationLogEntity.create(
                    requesterUserId == null ? "system" : requesterUserId,
                    AiCallKind.SCENARIO_PLANNING, model, inputTokens, outputTokens, succeeded));
        } catch (Exception auditError) {
            log.warn("AI scenario planning 감사 로그 저장 실패: {}", auditError.getMessage());
        }
    }

    public record PlanningAttempt(NormalizedProposal proposal, boolean succeeded, String promptVersion) {
    }

    private record PlanningInput(
            String promptVersion,
            String serviceDescription,
            String purpose,
            String apiTitle,
            String apiVersion,
            List<CapabilitySummary> capabilities,
            List<OperationSummary> operations
    ) {
    }

    private record CapabilitySummary(
            String id,
            String resource,
            String kind,
            String type,
            String action,
            List<String> requestFields,
            List<String> dependencies,
            String risk,
            String statusPath,
            List<String> terminalValues,
            List<String> evidence
    ) {
    }

    private record OperationSummary(
            String operationId,
            String summary,
            List<String> tags,
            boolean requiresAuth,
            List<String> parameters,
            List<String> requestFields,
            List<String> responseFields,
            List<String> enums
    ) {
    }
}
