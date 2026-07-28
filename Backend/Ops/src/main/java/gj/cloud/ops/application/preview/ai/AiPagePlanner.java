package gj.cloud.ops.application.preview.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputMessage;
import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CompatibilityFinding;
import gj.cloud.ops.application.preview.analysis.CompatibilitySeverity;
import gj.cloud.ops.application.preview.analysis.CompatibilityValidator;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.PreviewBlockResolver;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import gj.cloud.ops.application.preview.planning.model.PagePlanMapper;
import gj.cloud.ops.application.preview.planning.patch.PagePlanPatchApplyResult;
import gj.cloud.ops.application.preview.planning.patch.PagePlanPatchValidator;
import gj.cloud.ops.application.preview.planning.patch.PlanPatchState;
import gj.cloud.ops.domain.deployment.enums.AiCallKind;
import gj.cloud.ops.domain.preview.entity.AiPreviewGenerationLogEntity;
import gj.cloud.ops.domain.preview.repository.AiPreviewGenerationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// AI는 의미와 사용자 흐름을 제안하지만, 실제 상태 변경은 고정 operation + 결정론적 Validator만 수행한다.
// propose는 각 operation이 앞선 유효 operation 결과에 구조적으로 적용 가능한지만 보여주고, apply는
// 선택 조합 전체의 PagePlan/Flow/Binding 완성도와 Component Compatibility까지 최종 검증한다.
@Slf4j
@Component
public class AiPagePlanner {

    private static final String SYSTEM_PROMPT = """
            You are the Page Plan and Workflow proposer for GamjaBox Auto Preview. You receive a compact,
            already-normalized plan consisting of pages, capabilities, existing flows and API bindings, plus the
            user's service description and generation purpose.

            Improve the plan using only the structured operation types below. Operations are applied in order and
            may depend on earlier operations in the same list.

            - RENAME_PAGE: pageId, newTitle.
            - MERGE_PAGES: pageId is kept; otherPageId is removed and its capabilities move to pageId.
            - MOVE_CAPABILITY: capabilityId, destinationPageId.
            - ADD_PAGE: pageId, newTitle, optional pageType and layoutRef. Always move capabilities onto it later.
            - REMOVE_PAGE: pageId. It must already be empty.
            - SPLIT_PAGE: pageId is the source; destinationPageId/newTitle create the new page; capabilityIds move.
              Optional pageType/layoutRef may describe the new page.
            - SET_PAGE_TYPE: pageId, pageType.
            - SET_LAYOUT: pageId, layoutRef. Use only registered layout ids from the input.
            - SET_FEATURE: pageId, featureKey, featureEnabled.
            - ADD_NAVIGATION: navigationRule. Prefer row.select from a list page to a RESOURCE_DETAIL page, mapping
              route parameters with restricted expressions such as $row.id.
            - ADD_FLOW: flow. Use only existing binding ids and restricted expressions. A newly added flow may have
              a null trigger until a later ASSIGN_FLOW operation.
            - ASSIGN_FLOW: flowId, pageId, actionId. actionId must be a capability assigned to that page.

            Never invent capability ids, binding ids, page ids, or layout ids unless the page/flow id is introduced
            by an earlier ADD_PAGE/SPLIT_PAGE/ADD_FLOW operation. Never modify AUTH or DASHBOARD structural roles.
            Never emit JavaScript, shell commands, HTML, package names, external URLs, or automatic destructive
            follow-up actions. Destructive capabilities must remain explicitly user-triggered.

            Keep proposals small and coherent. When a service benefits from a separate detail page, prefer a single
            SPLIT_PAGE + SET_PAGE_TYPE(RESOURCE_DETAIL) + ADD_NAVIGATION sequence rather than cosmetic renaming.
            Only propose ADD_FLOW when existing bindings are sufficient to execute every API/POLL step safely.
            If no material improvement exists, return an empty operations list.

            Every operation needs a short Korean reason grounded in the service description. newTitle and reason
            must be Korean. All enum values, ids, expressions and schema fields must follow the supplied schema.
            Fields unused by an operation must be null.
            """;

    private final OpenAIClient client;
    private final String model;
    private final ObjectMapper objectMapper;
    private final AiPreviewGenerationLogRepository logRepository;
    private final PreviewBlockResolver blockResolver;

    public AiPagePlanner(
            OpenAIClient client,
            @Value("${ai.model.standard}") String model,
            ObjectMapper objectMapper,
            AiPreviewGenerationLogRepository logRepository,
            PreviewBlockResolver blockResolver
    ) {
        this.client = client;
        this.model = model;
        this.objectMapper = objectMapper;
        this.logRepository = logRepository;
        this.blockResolver = blockResolver;
    }

    public PagePlanProposalResult propose(
            String requesterUserId,
            String serviceDescription,
            Purpose purpose,
            List<Capability> capabilities,
            PlanPatchState candidateState
    ) {
        long totalInputTokens = 0;
        long totalOutputTokens = 0;
        boolean succeeded = false;
        try {
            String input = objectMapper.writeValueAsString(
                    toPlanningInput(serviceDescription, purpose, capabilities, candidateState));
            AiCallResult call = callModel(input);
            totalInputTokens += call.inputTokens();
            totalOutputTokens += call.outputTokens();

            List<PagePlanOperationView> views = new ArrayList<>();
            PlanPatchState working = candidateState;
            List<PagePlanOperation> operations = call.proposal().operations() == null
                    ? List.of() : call.proposal().operations();
            for (int i = 0; i < operations.size(); i++) {
                PagePlanOperation operation = operations.get(i);
                PagePlanPatchApplyResult applied =
                        PagePlanPatchValidator.previewOperation(working, capabilities, operation);
                boolean valid = applied.succeeded();
                views.add(PagePlanOperationView.from(String.valueOf(i), operation, valid,
                        valid ? null : String.join("; ", applied.errors())));
                if (valid) {
                    working = applied.state();
                }
            }
            succeeded = true;
            return new PagePlanProposalResult(views, true);
        } catch (Exception e) {
            log.warn("Auto Preview AI 페이지 계획 제안 실패: {}", e.getMessage());
            return new PagePlanProposalResult(List.of(), false);
        } finally {
            logRepository.save(AiPreviewGenerationLogEntity.create(
                    requesterUserId, AiCallKind.PLANNING, model, totalInputTokens, totalOutputTokens, succeeded));
        }
    }

    public PagePlanPatchApplyResult applySelected(
            PlanPatchState candidateState,
            List<Capability> capabilities,
            List<PagePlanOperation> operations
    ) {
        PagePlanPatchApplyResult applied = PagePlanPatchValidator.apply(candidateState, capabilities, operations);
        if (!applied.succeeded()) {
            return applied;
        }

        List<String> compatibilityErrors = new ArrayList<>();
        List<PageDraft> drafts = PagePlanMapper.toDrafts(applied.state().pagePlans());
        for (PageDraft page : drafts) {
            List<Block> blocks = blockResolver.resolve(page, capabilities);
            for (CompatibilityFinding finding : CompatibilityValidator.validate(page, blocks, capabilities)) {
                if (finding.severity() == CompatibilitySeverity.ERROR) {
                    compatibilityErrors.add(page.id() + ": " + finding.message());
                }
            }
        }
        if (!compatibilityErrors.isEmpty()) {
            return new PagePlanPatchApplyResult(candidateState, List.of(), compatibilityErrors);
        }
        return applied;
    }

    private AiCallResult callModel(String userPrompt) {
        StructuredResponseCreateParams<PagePlanProposal> params = ResponseCreateParams.builder()
                .model(model)
                .reasoning(Reasoning.builder().effort(ReasoningEffort.LOW).build())
                .instructions(SYSTEM_PROMPT)
                .input(userPrompt)
                .text(PagePlanProposal.class)
                .build();
        StructuredResponse<PagePlanProposal> response = client.responses().create(params);
        PagePlanProposal output = response.output().stream()
                .filter(item -> item.message().isPresent())
                .flatMap(item -> item.message().get().content().stream())
                .filter(StructuredResponseOutputMessage.Content::isOutputText)
                .map(StructuredResponseOutputMessage.Content::asOutputText)
                .findFirst()
                .orElse(new PagePlanProposal(List.of()));
        long inputTokens = response.usage().map(usage -> usage.inputTokens()).orElse(0L);
        long outputTokens = response.usage().map(usage -> usage.outputTokens()).orElse(0L);
        return new AiCallResult(output, inputTokens, outputTokens);
    }

    private PlanningInput toPlanningInput(String serviceDescription, Purpose purpose, List<Capability> capabilities,
                                           PlanPatchState state) {
        List<CapabilitySummary> capSummaries = capabilities.stream()
                .map(c -> new CapabilitySummary(c.id(), c.resourceName(), c.kind().name(), c.action(),
                        c.type() != null ? c.type().name() : null, c.dependencies()))
                .toList();
        List<PageSummary> pageSummaries = state.pagePlans().stream()
                .map(p -> new PageSummary(p.id(), p.title(), p.route(), p.pageType().name(), p.layoutRef(),
                        p.capabilityIds(), p.features(), p.navigationRules()))
                .toList();
        List<FlowSummary> flowSummaries = state.flows().stream()
                .map(flow -> new FlowSummary(flow.id(), flow.trigger(), flow.steps()))
                .toList();
        return new PlanningInput(serviceDescription, purpose != null ? purpose.name() : null,
                capSummaries, pageSummaries, flowSummaries,
                state.bindings().stream().map(binding -> binding.id() + "=" + binding.capabilityId()).toList(),
                gj.cloud.ops.application.preview.layout.LayoutBlueprints.ALL.keySet().stream().sorted().toList());
    }

    private record CapabilitySummary(String id, String resourceName, String kind, String action, String type,
                                     List<String> dependencies) {
    }

    private record PageSummary(String id, String title, String route, String pageType, String layoutRef,
                               List<String> capabilityIds, java.util.Map<String, Boolean> features,
                               List<gj.cloud.ops.application.preview.planning.model.NavigationRule> navigationRules) {
    }

    private record FlowSummary(String id, gj.cloud.ops.application.preview.flow.FlowBlueprint.FlowTrigger trigger,
                               List<gj.cloud.ops.application.preview.flow.FlowStep> steps) {
    }

    private record PlanningInput(String serviceDescription, String purpose,
                                 List<CapabilitySummary> capabilities,
                                 List<PageSummary> candidatePages,
                                 List<FlowSummary> existingFlows,
                                 List<String> availableBindings,
                                 List<String> availableLayouts) {
    }

    private record AiCallResult(PagePlanProposal proposal, long inputTokens, long outputTokens) {
    }
}
