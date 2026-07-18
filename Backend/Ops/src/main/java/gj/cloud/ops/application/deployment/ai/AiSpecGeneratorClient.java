package gj.cloud.ops.application.deployment.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import gj.cloud.ops.application.deployment.dto.GenerateDeploymentSpecRequest;
import gj.cloud.ops.application.deployment.dto.InfraSelection;
import gj.cloud.ops.application.deployment.dto.ServiceCard;
import gj.cloud.ops.application.deployment.spec.DeploymentSpec;
import gj.cloud.ops.application.deployment.spec.DeploymentSpecValidator;
import gj.cloud.ops.domain.deployment.enums.AiCallKind;
import gj.cloud.ops.domain.deployment.entity.AiSpecGenerationLogEntity;
import gj.cloud.ops.domain.deployment.repository.AiSpecGenerationLogRepository;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

// D-3 AI 자동생성 — 사용자가 입력한 경량 서비스 카드를 완전한 DeploymentSpec JSON으로 변환.
// AI는 raw compose를 직접 만들지 않고 검증 가능한 스펙(JSON)만 생성 → gamjabox가 기존 DeploymentSpecValidator/
// DeploymentSpecRenderer(D-1과 공유)로 compose를 결정적으로 렌더링함 (보안 위험 차단, D-3절 원칙).
//
// 모델 티어링 (비용 최소화):
//   표준 생성            → standard 모델 / LOW
//   검증 1회 실패(재교정) → standard 모델 / MEDIUM (이전 응답+오류를 그대로 전달)
//   복잡한 모노레포       → escalated 모델 / MEDIUM (처음부터)
//   반복 실패(재교정도 실패) → escalated 모델 / MEDIUM 로 최종 승격
// 최대 시도 횟수는 고정 상한(초기 1회 + 재교정 2회)이며, 무한 재시도는 하지 않는다.
// 원문 프롬프트/응답은 저장하지 않고 모델명·토큰 수·재교정 횟수·성공 여부만 감사 로그로 남긴다.
@Slf4j
@Component
public class AiSpecGeneratorClient {

    private static final int MAX_CORRECTION_ATTEMPTS = 2;
    // 서비스가 이 개수 이상이면 모노레포로 간주해 처음부터 escalated 모델 사용
    private static final int COMPLEX_SERVICE_THRESHOLD = 2;

    private static final String SYSTEM_PROMPT = """
            너는 gamjabox 배포 파이프라인의 DeploymentSpec JSON 생성기다. 사용자가 제공하는 서비스 카드와 \
            공유 인프라 선택을 받아 아래 스키마를 정확히 따르는 JSON만 출력한다. 설명, 마크다운 코드펜스, 그 외 \
            텍스트는 절대 포함하지 않는다.

            스키마:
            {
              "schemaVersion": "1.0",
              "services": [
                {
                  "name": string,
                  "runtime": "spring-boot" | "nextjs" | "react-nginx" | "nodejs" | "nestjs" | "python",
                  "javaVersion": number | null,
                  "buildTool": "gradle" | "maven" | null,
                  "nodeVersion": number | null,
                  "buildCommand": string | null,
                  "startCommand": string | null,
                  "pythonVersion": string | null,
                  "pythonFramework": "fastapi" | "django" | "flask" | null,
                  "context": string,
                  "containerPort": number,
                  "expose": { "enabled": boolean, "protocol": "http", "healthCheckPath": string } | null
                }
              ],
              "infrastructure": [
                { "type": "postgresql" | "mysql" | "redis" | "mongodb", "version": string, "expose": null }
              ],
              "network": string
            }

            규칙:
            - runtime별로 실제 쓰이는 필드만 채운다 (spring-boot→javaVersion/buildTool, nextjs/react-nginx/nodejs/nestjs→nodeVersion, python→pythonVersion/pythonFramework). 나머지는 null로 둔다.
            - 서비스 카드에 값이 없는 필드는 해당 런타임의 안정적인 LTS 기본값으로 채운다 (예: javaVersion 21, nodeVersion 20).
            - expose가 true인 서비스는 healthCheckPath를 실제로 존재할 법한 경로로 채운다(예: "/actuator/health", "/", "/api/health"). expose가 false면 expose 필드 전체를 null로 둔다.
            - infrastructure 항목의 version이 비어있으면 안정적인 기본 버전으로 채운다 (postgresql→"16", mysql→"8", redis→"7", mongodb→"7"). infrastructure의 expose는 항상 null.
            - network는 소문자 영숫자와 하이픈으로 구성된 하나의 문자열로 정한다 (예: "app-network").
            """;

    private final OpenAIClient client;
    private final String standardModel;
    private final String escalatedModel;
    private final ObjectMapper objectMapper;
    private final DeploymentSpecValidator deploymentSpecValidator;
    private final AiSpecGenerationLogRepository logRepository;

    public AiSpecGeneratorClient(
            OpenAIClient client,
            @Value("${ai.model.standard}") String standardModel,
            @Value("${ai.model.escalated}") String escalatedModel,
            ObjectMapper objectMapper,
            DeploymentSpecValidator deploymentSpecValidator,
            AiSpecGenerationLogRepository logRepository
    ) {
        this.client = client;
        this.standardModel = standardModel;
        this.escalatedModel = escalatedModel;
        this.objectMapper = objectMapper;
        this.deploymentSpecValidator = deploymentSpecValidator;
        this.logRepository = logRepository;
    }

    public DeploymentSpec generate(String vmId, GenerateDeploymentSpecRequest request) {
        String prompt = buildUserPrompt(request);
        ModelChoice choice = initialChoice(request);

        long totalInputTokens = 0;
        long totalOutputTokens = 0;
        int correctionAttempts = 0;
        boolean succeeded = false;
        String lastModelUsed = choice.model();
        try {
            AiCallResult call = callModel(choice, prompt);
            totalInputTokens += call.inputTokens();
            totalOutputTokens += call.outputTokens();

            ParsedSpec parsed = tryParse(call.json());
            List<String> errors = collectErrors(parsed);

            while (!errors.isEmpty() && correctionAttempts < MAX_CORRECTION_ATTEMPTS) {
                correctionAttempts++;
                choice = correctionChoice(choice, correctionAttempts);
                lastModelUsed = choice.model();
                call = callModel(choice, buildCorrectionPrompt(prompt, call.json(), errors));
                totalInputTokens += call.inputTokens();
                totalOutputTokens += call.outputTokens();
                parsed = tryParse(call.json());
                errors = collectErrors(parsed);
            }

            if (!errors.isEmpty()) {
                log.error("AI 배포 스펙 생성 실패 (재교정 {}회 포함): {}", correctionAttempts, errors);
                throw new OpsException(OpsErrorCode.AI_SPEC_INVALID_RESPONSE);
            }
            succeeded = true;
            return parsed.spec();
        } finally {
            logRepository.save(AiSpecGenerationLogEntity.create(
                    vmId, AiCallKind.GENERATION, lastModelUsed, totalInputTokens, totalOutputTokens,
                    correctionAttempts, succeeded));
        }
    }

    // 서비스가 여러 개면 모노레포로 간주해 처음부터 escalated 모델 사용
    private ModelChoice initialChoice(GenerateDeploymentSpecRequest request) {
        boolean complexMonorepo = request.services().size() >= COMPLEX_SERVICE_THRESHOLD;
        return complexMonorepo
                ? new ModelChoice(escalatedModel, ReasoningEffort.MEDIUM)
                : new ModelChoice(standardModel, ReasoningEffort.LOW);
    }

    // 이미 escalated 모델로 시작했다면 유지, standard였다면 1차는 같은 모델의 effort만 올리고
    // 그래도 실패(반복 실패)하면 escalated 모델로 최종 승격
    private ModelChoice correctionChoice(ModelChoice previous, int attemptNumber) {
        if (previous.model().equals(escalatedModel)) {
            return new ModelChoice(escalatedModel, ReasoningEffort.MEDIUM);
        }
        return attemptNumber == 1
                ? new ModelChoice(standardModel, ReasoningEffort.MEDIUM)
                : new ModelChoice(escalatedModel, ReasoningEffort.MEDIUM);
    }

    private List<String> collectErrors(ParsedSpec parsed) {
        return parsed.spec() != null ? deploymentSpecValidator.collectErrors(parsed.spec()) : List.of(parsed.parseError());
    }

    private AiCallResult callModel(ModelChoice choice, String userPrompt) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(choice.model())
                .reasoning(Reasoning.builder().effort(choice.effort()).build())
                .instructions(SYSTEM_PROMPT)
                .input(userPrompt)
                .build();

        Response response;
        try {
            response = client.responses().create(params);
        } catch (Exception e) {
            log.error("AI 배포 스펙 생성 요청 실패: {}", e.getMessage());
            throw new OpsException(OpsErrorCode.AI_SPEC_GENERATION_FAILED);
        }

        String json = stripCodeFence(response.output().stream()
                .filter(item -> item.isMessage())
                .flatMap(item -> item.asMessage().content().stream())
                .filter(content -> content.isOutputText())
                .map(content -> content.asOutputText().text())
                .reduce("", String::concat)
                .trim());

        long inputTokens = response.usage().map(usage -> usage.inputTokens()).orElse(0L);
        long outputTokens = response.usage().map(usage -> usage.outputTokens()).orElse(0L);

        return new AiCallResult(json, inputTokens, outputTokens);
    }

    private ParsedSpec tryParse(String json) {
        try {
            return new ParsedSpec(objectMapper.readValue(json, DeploymentSpec.class), null);
        } catch (Exception e) {
            return new ParsedSpec(null, "JSON 파싱 실패: " + e.getMessage());
        }
    }

    private String buildUserPrompt(GenerateDeploymentSpecRequest request) {
        StringBuilder sb = new StringBuilder("서비스 카드:\n");
        for (ServiceCard service : request.services()) {
            sb.append("- name=").append(service.name())
                    .append(", runtime=").append(service.runtime())
                    .append(", context=").append(service.context())
                    .append(", containerPort=").append(service.containerPort())
                    .append(", javaVersion=").append(service.javaVersion())
                    .append(", buildTool=").append(service.buildTool())
                    .append(", nodeVersion=").append(service.nodeVersion())
                    .append(", buildCommand=").append(service.buildCommand())
                    .append(", startCommand=").append(service.startCommand())
                    .append(", pythonVersion=").append(service.pythonVersion())
                    .append(", pythonFramework=").append(service.pythonFramework())
                    .append(", expose=").append(service.expose())
                    .append("\n");
        }
        if (request.infrastructure() != null && !request.infrastructure().isEmpty()) {
            sb.append("공유 인프라:\n");
            for (InfraSelection infra : request.infrastructure()) {
                sb.append("- type=").append(infra.type())
                        .append(", version=").append(infra.version())
                        .append("\n");
            }
        }
        return sb.toString();
    }

    private String buildCorrectionPrompt(String originalPrompt, String previousJson, List<String> errors) {
        return originalPrompt
                + "\n\n이전 응답:\n" + previousJson
                + "\n\n검증 오류:\n- " + String.join("\n- ", errors)
                + "\n\n위 오류에 해당하는 필드만 수정한 전체 JSON을 다시 출력하라. 스키마와 규칙은 이전과 동일하게 유지한다.";
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

    private record ModelChoice(String model, ReasoningEffort effort) {
    }

    private record AiCallResult(String json, long inputTokens, long outputTokens) {
    }

    private record ParsedSpec(DeploymentSpec spec, String parseError) {
    }
}
