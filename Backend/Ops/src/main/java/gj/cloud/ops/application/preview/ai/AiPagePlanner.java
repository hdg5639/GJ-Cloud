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
import gj.cloud.ops.application.preview.planning.PagePlanApplyResult;
import gj.cloud.ops.application.preview.planning.PagePlanValidator;
import gj.cloud.ops.domain.deployment.enums.AiCallKind;
import gj.cloud.ops.domain.preview.entity.AiPreviewGenerationLogEntity;
import gj.cloud.ops.domain.preview.repository.AiPreviewGenerationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// GamjaBox_Auto_Preview_Direction_Recovery_Change_Request.md Package B(Increment 3, 이후 Increment 5
// 2부에서 propose/apply로 분리) — AiPageReviewer와 달리 이 AI 호출은 실제로 페이지 구성에 반영될 수
// 있다. 다만 GamjaBox_2.0_Key_Features.md §9 "AI 출력도 구조화된 패치로 제한함" 원칙 그대로, AI는
// 고정된 operation 3종(RENAME_PAGE/MERGE_PAGES/MOVE_CAPABILITY)만 제안하고 PagePlanValidator가
// 검증한 것만 적용한다(§5.3 "Blueprint compiler remains deterministic"). Plan Review UI(Increment 5
// 2부) 도입 후에는 "전체가 깨지면 전부 버리고 1회 재교정"이 아니라, 오퍼레이션마다 개별 검증 결과를
// 사용자에게 보여주고 사용자가 고른 서브셋만 적용한다 — 그래서 AI 응답 자체를 교정 재시도하는 로직은
// 없앴다(사용자가 무효한 항목을 직접 걸러내므로 더 이상 필요 없음).
@Slf4j
@Component
public class AiPagePlanner {

    private static final String SYSTEM_PROMPT = """
            You are the Page Plan proposer for GamjaBox Auto Preview. You are given a compact summary of a
            deterministically-generated candidate page plan (already grouped by resource and adjusted for the \
            generation purpose), the capabilities available, and the user's service description.

            Propose a small number of structured operations that make the candidate plan better reflect how a \
            real user of the described service would navigate it. You may only use these operation types:
            - RENAME_PAGE: change a page's title to something more domain-appropriate. Requires pageId, newTitle.
            - MERGE_PAGES: combine two pages into one coherent user flow. Requires pageId (the page to keep) and \
              otherPageId (the page to remove — its capabilities move onto pageId).
            - MOVE_CAPABILITY: move a single capability from whatever page it is currently on to a different \
              page. Requires capabilityId and destinationPageId.

            Never invent a pageId or capabilityId that is not present in the input. Never target, merge, or move \
            a capability into/out of a page whose skeleton is AUTH_PAGE or DASHBOARD — those are structurally \
            protected and any such operation will be rejected outright. If nothing meaningfully improves the \
            candidate plan, return an empty operations list — do not propose changes just to have something to say.

            Every operation must include a short `reason` field explaining why, grounded in the service \
            description. Leave fields that don't apply to the operation's type as null.

            Language requirement: this system is used by Korean-speaking users through a Korean-language portal. \
            Write `newTitle` and `reason` in Korean. Every other field must follow the schema exactly.
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

    // Plan Review UI(Increment 5 2부) — AI가 제안한 오퍼레이션을 적용하지 않고, 오퍼레이션마다 원본
    // 후보 페이지 기준으로 개별 검증한 valid/validationError만 매겨 돌려준다. 사용자가 화면에서 고른
    // 서브셋은 applySelected로 실제 적용한다.
    public PagePlanProposalResult propose(
            String requesterUserId, String serviceDescription, Purpose purpose,
            List<Capability> capabilities, List<PageDraft> candidatePages
    ) {
        long totalInputTokens = 0;
        long totalOutputTokens = 0;
        boolean succeeded = false;
        try {
            String input = objectMapper.writeValueAsString(
                    toPlanningInput(serviceDescription, purpose, capabilities, candidatePages));

            AiCallResult call = callModel(input);
            totalInputTokens += call.inputTokens();
            totalOutputTokens += call.outputTokens();

            List<PagePlanOperationView> views = new ArrayList<>();
            List<PagePlanOperation> operations = call.proposal().operations();
            for (int i = 0; i < operations.size(); i++) {
                views.add(toView(String.valueOf(i), operations.get(i), capabilities, candidatePages));
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

    // 사용자가 review 화면에서 고른 서브셋을 실제로 적용한다 — AI를 다시 부르지 않고 validateAndApply만
    // 태우므로 순수 결정론적이다.
    public PagePlanApplyResult applySelected(
            List<PageDraft> candidatePages, List<Capability> capabilities, List<PagePlanOperation> operations
    ) {
        return validateAndApply(new PagePlanProposal(operations), capabilities, candidatePages);
    }

    private PagePlanOperationView toView(String id, PagePlanOperation op, List<Capability> capabilities,
                                          List<PageDraft> candidatePages) {
        PagePlanApplyResult applied = validateAndApply(new PagePlanProposal(List.of(op)), capabilities, candidatePages);
        boolean valid = applied.errors().isEmpty();
        String validationError = valid ? null : String.join("; ", applied.errors());
        return new PagePlanOperationView(id, op.type(), op.pageId(), op.otherPageId(), op.newTitle(),
                op.capabilityId(), op.destinationPageId(), op.reason(), valid, validationError);
    }

    // PagePlanValidator(구조 검증) 통과 후, 결과 페이지가 실제로 Block/Compatibility 관점에서도 문제
    // 없는지 한 번 더 확인한다(이중 안전망) — CompatibilityValidator는 이미 분석 파이프라인에서 검증된
    // 코드라 그대로 재사용한다.
    private PagePlanApplyResult validateAndApply(PagePlanProposal proposal, List<Capability> capabilities,
                                                  List<PageDraft> candidatePages) {
        PagePlanApplyResult applied = PagePlanValidator.apply(candidatePages, capabilities, proposal);
        if (!applied.errors().isEmpty()) {
            return applied;
        }
        List<String> compatibilityErrors = new ArrayList<>();
        for (PageDraft page : applied.pages()) {
            List<Block> blocks = blockResolver.resolve(page, capabilities);
            for (CompatibilityFinding finding : CompatibilityValidator.validate(page, blocks, capabilities)) {
                if (finding.severity() == CompatibilitySeverity.ERROR) {
                    compatibilityErrors.add(finding.message());
                }
            }
        }
        if (!compatibilityErrors.isEmpty()) {
            return new PagePlanApplyResult(candidatePages, List.of(), compatibilityErrors);
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

    // Key Features §9 "AI 입력도 축소함" — accessTokenPath/collectionPath/evidence 같은 내부 필드는
    // 페이지 계획과 무관하므로 보내지 않고, 계획에 실제로 필요한 축만 추린다.
    private PlanningInput toPlanningInput(String serviceDescription, Purpose purpose, List<Capability> capabilities,
                                           List<PageDraft> candidatePages) {
        List<CapabilitySummary> capSummaries = capabilities.stream()
                .map(c -> new CapabilitySummary(c.id(), c.resourceName(), c.kind().name(), c.action(),
                        c.type() != null ? c.type().name() : null))
                .toList();
        List<PageSummary> pageSummaries = candidatePages.stream()
                .map(p -> new PageSummary(p.id(), p.title(), p.skeleton().name(), p.capabilityIds()))
                .toList();
        return new PlanningInput(serviceDescription, purpose != null ? purpose.name() : null, capSummaries, pageSummaries);
    }

    private record CapabilitySummary(String id, String resourceName, String kind, String action, String type) {
    }

    private record PageSummary(String id, String title, String skeleton, List<String> capabilityIds) {
    }

    private record PlanningInput(String serviceDescription, String purpose, List<CapabilitySummary> capabilities,
                                  List<PageSummary> candidatePages) {
    }

    private record AiCallResult(PagePlanProposal proposal, long inputTokens, long outputTokens) {
    }
}
