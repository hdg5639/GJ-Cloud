package gj.cloud.ops.application.deployment.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import gj.cloud.ops.application.deployment.spec.DeploymentSpec;
import gj.cloud.ops.domain.deployment.entity.AiSpecGenerationLogEntity;
import gj.cloud.ops.domain.deployment.enums.AiCallKind;
import gj.cloud.ops.domain.deployment.repository.AiSpecGenerationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

// D.5-1 비차단 AI 검수 — 결정론적 검증(DeploymentSpecValidator/ComposeValidator)을 이미 통과한 스펙에 대해서만 호출됨.
// 코멘트만 제공하고 스펙을 수정하거나 배포를 승인/거부하지 않음 — 결정론적 검증을 절대 대체하지 않는다(D.5-1절 원칙).
// 단일 저비용 호출(재교정 없음)이며, 실패해도 배포를 막지 않고 빈 목록을 반환한다.
@Slf4j
@Component
public class AiComposeReviewer {

    private static final String SYSTEM_PROMPT = """
            너는 gamjabox 배포 스펙에 대한 비차단(non-blocking) AI 검수관이다. 이미 결정론적 검증을 통과한 \
            DeploymentSpec JSON을 검토하고 다음을 짧은 코멘트 목록으로 제시한다: 운영상 위험, 누락된 \
            헬스체크, 누락된 재시작 정책, 퍼시스턴스(볼륨) 이슈 가능성, 의심스러운 환경변수 설정, 누락된 \
            의존성 선언, 잘못됐을 가능성이 있는 시작 명령, 리소스 배분 개선 여지.

            지적할 사항이 없으면 빈 배열을 반환한다. 스펙을 수정하거나 배포를 승인/거부하지 않으며, 코멘트만 \
            제공한다. JSON 문자열 배열만 출력한다(예: ["...", "..."] 또는 []). 그 외 설명, 마크다운 코드펜스, \
            텍스트는 절대 포함하지 않는다.
            """;

    private final OpenAIClient client;
    private final String model;
    private final ObjectMapper objectMapper;
    private final AiSpecGenerationLogRepository logRepository;

    public AiComposeReviewer(
            OpenAIClient client,
            @Value("${ai.model.standard}") String model,
            ObjectMapper objectMapper,
            AiSpecGenerationLogRepository logRepository
    ) {
        this.client = client;
        this.model = model;
        this.objectMapper = objectMapper;
        this.logRepository = logRepository;
    }

    public List<String> review(String vmId, DeploymentSpec spec) {
        long inputTokens = 0;
        long outputTokens = 0;
        boolean succeeded = false;
        try {
            String prompt = objectMapper.writeValueAsString(spec);

            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(model)
                    .reasoning(Reasoning.builder().effort(ReasoningEffort.LOW).build())
                    .instructions(SYSTEM_PROMPT)
                    .input(prompt)
                    .build();

            Response response = client.responses().create(params);

            String json = stripCodeFence(response.output().stream()
                    .filter(item -> item.isMessage())
                    .flatMap(item -> item.asMessage().content().stream())
                    .filter(content -> content.isOutputText())
                    .map(content -> content.asOutputText().text())
                    .reduce("", String::concat)
                    .trim());

            inputTokens = response.usage().map(usage -> usage.inputTokens()).orElse(0L);
            outputTokens = response.usage().map(usage -> usage.outputTokens()).orElse(0L);

            List<String> comments = parseComments(json);
            succeeded = true;
            return comments;
        } catch (Exception e) {
            // 검수는 부가 기능 — 실패해도 배포를 막지 않고 빈 목록으로 대체한다.
            log.warn("AI compose 검수 실패(배포는 계속 진행됨): {}", e.getMessage());
            return List.of();
        } finally {
            logRepository.save(AiSpecGenerationLogEntity.create(
                    vmId, AiCallKind.REVIEW, model, inputTokens, outputTokens, 0, succeeded));
        }
    }

    private List<String> parseComments(String json) {
        if (json.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            // 배열 형식이 아니면 원문을 단일 코멘트로 취급 — 검수는 비차단이므로 형식 오류로 버리지 않는다.
            return List.of(json);
        }
    }

    private String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline != -1) {
            trimmed = trimmed.substring(firstNewline + 1);
        }
        int lastFence = trimmed.lastIndexOf("```");
        if (lastFence != -1) {
            trimmed = trimmed.substring(0, lastFence);
        }
        return trimmed.trim();
    }
}
