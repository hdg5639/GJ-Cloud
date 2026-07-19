package gj.cloud.ops.application.deployment.ai;

import com.openai.client.OpenAIClient;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputMessage;
import gj.cloud.ops.domain.deployment.entity.AiSpecGenerationLogEntity;
import gj.cloud.ops.domain.deployment.enums.AiCallKind;
import gj.cloud.ops.domain.deployment.repository.AiSpecGenerationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// D.5-1 비차단 AI 검수 — AI-Deployment-Pipeline.md 13절 반영판.
// 이제 렌더링 전 스펙이 아니라 "최종 렌더링된 compose 원문"을 검토한다(실제 배포될 내용 그대로 검토해야
// 의미가 있음). 전송 전 환경변수 값 중 비밀값처럼 보이는 것들은 <REDACTED>로 치환해 AI에게 절대 원문
// 시크릿을 보내지 않는다. 코멘트만 제공하고 스펙을 수정하거나 배포를 승인/거부하지 않음 — 결정론적 검증을
// 절대 대체하지 않는다(원칙 그대로 유지). 단일 저비용 호출(재교정 없음)이며, 실패해도 배포를 막지 않고
// 빈 목록을 반환한다.
@Slf4j
@Component
public class AiComposeReviewer {

    private static final int MAX_FINDINGS = 8;

    // compose YAML의 "KEY: value" 환경변수 라인 중 이름이 비밀값처럼 보이는 것의 값만 치환
    private static final Pattern SECRET_ENV_LINE = Pattern.compile(
            "(?im)^(\\s*[\"']?[A-Z0-9_]*(?:PASSWORD|SECRET|TOKEN|API_KEY|PRIVATE_KEY)[A-Z0-9_]*[\"']?\\s*:\\s*).+$");

    private static final String SYSTEM_PROMPT = """
            너는 gamjabox 배포 스펙에 대한 비차단(non-blocking) AI 검수관이다. 이미 결정론적 검증을 통과하고
            실제로 렌더링된 최종 Docker Compose 내용(환경변수 비밀값은 <REDACTED>로 가려짐)을 검토하고 다음을
            짧은 코멘트 목록으로 제시한다: 운영상 위험, 누락된 헬스체크, 누락된 재시작 정책, 퍼시스턴스(볼륨)
            이슈 가능성, 의심스러운 환경변수 설정, 누락된 의존성 선언, 잘못됐을 가능성이 있는 시작 명령,
            리소스 배분 개선 여지.

            지적할 사항이 없으면 findings를 빈 배열로 둔다. 스펙을 수정하거나 배포를 승인/거부하지 않으며,
            코멘트만 제공한다. 이미 결정론적 검증이 확실히 잡아내는 문제(예: 포트 범위 오류, 스키마 위반)는
            다시 지적하지 마라 — 토큰 낭비이자 중복 경고다. 최대 8개까지만 제시한다.
            """;

    private final OpenAIClient client;
    private final String model;
    private final AiSpecGenerationLogRepository logRepository;

    public AiComposeReviewer(
            OpenAIClient client,
            @Value("${ai.model.standard}") String model,
            AiSpecGenerationLogRepository logRepository
    ) {
        this.client = client;
        this.model = model;
        this.logRepository = logRepository;
    }

    public List<ComposeReviewFinding> review(String vmId, String renderedComposeContent) {
        long inputTokens = 0;
        long outputTokens = 0;
        boolean succeeded = false;
        try {
            String redacted = redactSecrets(renderedComposeContent);

            StructuredResponseCreateParams<ComposeReviewResult> params = ResponseCreateParams.builder()
                    .model(model)
                    .reasoning(Reasoning.builder().effort(ReasoningEffort.LOW).build())
                    .instructions(SYSTEM_PROMPT)
                    .input(redacted)
                    .text(ComposeReviewResult.class)
                    .build();

            StructuredResponse<ComposeReviewResult> response = client.responses().create(params);

            ComposeReviewResult result = response.output().stream()
                    .filter(item -> item.message().isPresent())
                    .flatMap(item -> item.message().get().content().stream())
                    .filter(StructuredResponseOutputMessage.Content::isOutputText)
                    .map(StructuredResponseOutputMessage.Content::asOutputText)
                    .findFirst()
                    .orElse(new ComposeReviewResult(List.of()));

            inputTokens = response.usage().map(usage -> usage.inputTokens()).orElse(0L);
            outputTokens = response.usage().map(usage -> usage.outputTokens()).orElse(0L);

            List<ComposeReviewFinding> findings = result.findings() == null ? List.of() : result.findings();
            succeeded = true;
            return findings.size() > MAX_FINDINGS ? findings.subList(0, MAX_FINDINGS) : findings;
        } catch (Exception e) {
            // 검수는 부가 기능 — 실패해도 배포를 막지 않고 빈 목록으로 대체한다.
            log.warn("AI compose 검수 실패(배포는 계속 진행됨): {}", e.getMessage());
            return List.of();
        } finally {
            logRepository.save(AiSpecGenerationLogEntity.create(vmId, AiCallKind.REVIEW, model, inputTokens, outputTokens, 0, succeeded));
        }
    }

    private String redactSecrets(String composeContent) {
        Matcher matcher = SECRET_ENV_LINE.matcher(composeContent);
        return matcher.replaceAll(mr -> Matcher.quoteReplacement(mr.group(1) + "<REDACTED>"));
    }
}
