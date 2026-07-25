package gj.cloud.ops.application.preview.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputMessage;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.domain.deployment.enums.AiCallKind;
import gj.cloud.ops.domain.preview.entity.AiPreviewGenerationLogEntity;
import gj.cloud.ops.domain.preview.repository.AiPreviewGenerationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

// GamjaBox_2.0_Key_Features.md 9절 "AI의 역할" — AiComposeReviewer(D.5-1)와 같은 비차단 검수 패턴을
// 그대로 미러링. 결정론적으로 뽑은 capability/페이지 초안만(원본 OpenAPI 문서 전체가 아니라) 축소해서
// 보내고, AI는 코멘트만 반환한다 — 페이지를 직접 추가/삭제/수정하지 않는다. 실패해도 분석 결과 자체는
// 이미 확정돼 있으므로 빈 목록으로 대체하고 계속 진행한다.
@Slf4j
@Component
public class AiPageReviewer {

    private static final int MAX_FINDINGS = 6;

    private static final String SYSTEM_PROMPT = """
            You are a non-blocking AI reviewer for GamjaBox Auto Preview. You are given a compact, already-\
            normalized summary of an OpenAPI document analysis: the service description the user typed, the \
            capabilities that were deterministically extracted (CRUD/login patterns), and the page drafts they \
            were grouped into. This analysis already passed deterministic rule-based extraction.

            Review only for: pages that seem obviously missing given the service description, capabilities that \
            appear grouped into the wrong page, a page that probably should be split into two, or a page that \
            probably should be merged into one. Do not repeat what deterministic extraction already reports \
            (e.g. do not just restate low-confidence items). Leave findings empty if nothing stands out. Never \
            propose changes outside of commentary — you cannot add, remove, or rename pages or capabilities \
            directly. Report at most 6 findings.

            Language requirement: this system is used by Korean-speaking users through a Korean-language portal. \
            Write the `message` and `remediation` fields of every finding in Korean.
            """;

    private final OpenAIClient client;
    private final String model;
    private final ObjectMapper objectMapper;
    private final AiPreviewGenerationLogRepository logRepository;

    public AiPageReviewer(
            OpenAIClient client,
            @Value("${ai.model.standard}") String model,
            ObjectMapper objectMapper,
            AiPreviewGenerationLogRepository logRepository
    ) {
        this.client = client;
        this.model = model;
        this.objectMapper = objectMapper;
        this.logRepository = logRepository;
    }

    public List<PageReviewFinding> review(
            String requesterUserId, String serviceDescription, List<Capability> capabilities, List<PageDraft> pages
    ) {
        long inputTokens = 0;
        long outputTokens = 0;
        boolean succeeded = false;
        try {
            String input = objectMapper.writeValueAsString(
                    new ReviewInput(serviceDescription, capabilities, pages));

            StructuredResponseCreateParams<PageReviewResult> params = ResponseCreateParams.builder()
                    .model(model)
                    .reasoning(Reasoning.builder().effort(ReasoningEffort.LOW).build())
                    .instructions(SYSTEM_PROMPT)
                    .input(input)
                    .text(PageReviewResult.class)
                    .build();

            StructuredResponse<PageReviewResult> response = client.responses().create(params);

            PageReviewResult result = response.output().stream()
                    .filter(item -> item.message().isPresent())
                    .flatMap(item -> item.message().get().content().stream())
                    .filter(StructuredResponseOutputMessage.Content::isOutputText)
                    .map(StructuredResponseOutputMessage.Content::asOutputText)
                    .findFirst()
                    .orElse(new PageReviewResult(List.of()));

            inputTokens = response.usage().map(usage -> usage.inputTokens()).orElse(0L);
            outputTokens = response.usage().map(usage -> usage.outputTokens()).orElse(0L);

            List<PageReviewFinding> findings = result.findings() == null ? List.of() : result.findings();
            succeeded = true;
            return findings.size() > MAX_FINDINGS ? findings.subList(0, MAX_FINDINGS) : findings;
        } catch (Exception e) {
            log.warn("Auto Preview AI 페이지 검수 실패(분석 결과는 그대로 유지됨): {}", e.getMessage());
            return List.of();
        } finally {
            logRepository.save(AiPreviewGenerationLogEntity.create(
                    requesterUserId, AiCallKind.REVIEW, model, inputTokens, outputTokens, succeeded));
        }
    }

    private record ReviewInput(String serviceDescription, List<Capability> capabilities, List<PageDraft> pages) {
    }
}
