package gj.cloud.ops.application.deployment.ai;

import com.openai.client.OpenAIClient;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputMessage;
import gj.cloud.ops.application.deployment.dto.GenerateDeploymentSpecRequest;
import gj.cloud.ops.application.deployment.dto.InfraSelection;
import gj.cloud.ops.application.deployment.dto.ServiceCard;
import gj.cloud.ops.application.deployment.repoanalysis.RepositoryEvidence;
import gj.cloud.ops.application.deployment.repoanalysis.RepositorySnapshotBuilder;
import gj.cloud.ops.application.deployment.repoanalysis.DiscoveredService;
import gj.cloud.ops.application.deployment.repoanalysis.ServiceDiscoveryResult;
import gj.cloud.ops.application.deployment.repoanalysis.RuleBasedSpecInferrer;
import gj.cloud.ops.application.deployment.repoanalysis.RuleBasedSpecInferrer.RuleBasedInferenceResult;
import gj.cloud.ops.application.deployment.spec.DeploymentSpec;
import gj.cloud.ops.application.deployment.spec.DeploymentSpecPolicyValidator;
import gj.cloud.ops.application.deployment.spec.DeploymentSpecValidator;
import gj.cloud.ops.application.deployment.spec.InfrastructureSpec;
import gj.cloud.ops.application.deployment.spec.ExposeSpec;
import gj.cloud.ops.application.deployment.spec.ServiceSpec;
import gj.cloud.ops.application.deployment.spec.ValidationError;
import gj.cloud.ops.domain.deployment.enums.AiCallKind;
import gj.cloud.ops.domain.deployment.entity.AiSpecGenerationLogEntity;
import gj.cloud.ops.domain.deployment.repository.AiSpecGenerationLogRepository;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// D-3 AI 자동생성 — AI-Deployment-Pipeline.md 3~11절 반영판.
// 흐름이 완전히 바뀜: (1) 저장소를 실제로 얕게 클론해 결정론적 증거를 수집 → (2) 규칙 기반으로 최대한 확정
// (정적 사이트 등은 여기서 AI 호출 없이 끝남) → (3) 그래도 모호한 서비스만 골라 AI에게 넘김, 이때도
// 자유 문자열이 아니라 구조화 출력(JSON Schema 강제)으로 ServiceSpec을 직접 받음 → (4) 전체를 합쳐 검증.
// 원문 프롬프트/응답/저장소 내용은 저장하지 않고, 모델명·토큰 수·재교정 횟수·ambiguity 점수·성공 여부만 감사 로그로 남긴다.
//
// 프롬프트(지시문+동적 컨텍스트)는 전부 영어로 작성한다 — 한국어는 GPT류 BPE 토크나이저에서 같은 의미라도
// 영어보다 토큰을 더 쓰기 때문에, 매 호출마다 반복 전송되는 지시문/컨텍스트를 영어로 바꾸면 입력 토큰이
// 줄어든다. 다만 포털에 그대로 노출되는 자유 텍스트 필드(unresolved reason, warnings)는 한국어로 쓰도록
// 프롬프트에서 명시적으로 지시한다 — 이건 실제 사용자가 읽는 문구라서.
@Slf4j
@Component
public class AiSpecGeneratorClient {

    private static final int MAX_CORRECTION_ATTEMPTS = 2;
    private static final String SCHEMA_VERSION = "2.0";
    private static final String NETWORK_NAME = "app-network";

    private static final String SYSTEM_PROMPT = """
            You are the ServiceSpec JSON generator for the gamjabox deployment pipeline. You are only asked about \
            services that deterministic rules could not already resolve — each service is given together with a \
            summary of its repository analysis (RepositoryEvidence).

            Mandatory rules:
            - Never invent facts that are not present in the repository evidence. Do not guess ports, health-check \
              paths, or start script names.
            - If there is no backend runtime manifest at all (package.json/pom.xml/build.gradle/requirements.txt etc.) \
              but only an index.html exists, you MUST treat it as a STATIC artifact — never classify it as nodejs or \
              any other backend runtime.
            - Build/run commands may only be chosen from the allowed strategy enum. Never invent an arbitrary shell \
              command outside that list.
            - If there isn't enough evidence to resolve a service, put it in the unresolved list with a reason instead \
              of forcing a complete spec. Set status to whichever of NEEDS_INPUT/UNSUPPORTED/CONFLICT applies.
            - If the user-selected service card contradicts the repository evidence (e.g. the card says java but there \
              is no pom.xml/build.gradle at all in the repository), set status to CONFLICT and explain why.
            - `customSubdomain` is a user-owned PRO plan setting. Copy the requested value exactly when exposure is \
              enabled; use null when the user did not request one. Never invent or modify a custom subdomain.
            - Once everything is resolved, set status to READY.

            Language requirement: this system is used by Korean-speaking users through a Korean-language portal. \
            Write the `reason` field inside `unresolved` entries, and every string in `warnings`, in Korean — those \
            are shown directly to the end user. Every other field (enum values, paths, numbers) must follow the \
            schema exactly regardless of language.
            """;

    private final OpenAIClient client;
    private final String standardModel;
    private final String escalatedModel;
    private final DeploymentSpecValidator deploymentSpecValidator;
    private final DeploymentSpecPolicyValidator deploymentSpecPolicyValidator;
    private final AiSpecGenerationLogRepository logRepository;
    private final RepositorySnapshotBuilder repositorySnapshotBuilder;
    private final RuleBasedSpecInferrer ruleBasedSpecInferrer;
    private final AmbiguityScorer ambiguityScorer;
    private final AiGenerationCache generationCache;

    public AiSpecGeneratorClient(
            OpenAIClient client,
            @Value("${ai.model.standard}") String standardModel,
            @Value("${ai.model.escalated}") String escalatedModel,
            DeploymentSpecValidator deploymentSpecValidator,
            DeploymentSpecPolicyValidator deploymentSpecPolicyValidator,
            AiSpecGenerationLogRepository logRepository,
            RepositorySnapshotBuilder repositorySnapshotBuilder,
            RuleBasedSpecInferrer ruleBasedSpecInferrer,
            AmbiguityScorer ambiguityScorer,
            AiGenerationCache generationCache
    ) {
        this.client = client;
        this.standardModel = standardModel;
        this.escalatedModel = escalatedModel;
        this.deploymentSpecValidator = deploymentSpecValidator;
        this.deploymentSpecPolicyValidator = deploymentSpecPolicyValidator;
        this.logRepository = logRepository;
        this.repositorySnapshotBuilder = repositorySnapshotBuilder;
        this.ruleBasedSpecInferrer = ruleBasedSpecInferrer;
        this.ambiguityScorer = ambiguityScorer;
        this.generationCache = generationCache;
    }

    public AiGenerationResult generate(String vmId, GenerateDeploymentSpecRequest request) {
        ServiceDiscoveryResult discovery = repositorySnapshotBuilder.discoverServices(
                request.repoUrl(), request.branch(), request.patToken());
        List<ServiceCard> effectiveServices = mergeDiscoveredServices(request.services(), discovery.services());
        GenerateDeploymentSpecRequest effectiveRequest = new GenerateDeploymentSpecRequest(
                request.repoUrl(),
                request.branch(),
                request.patToken(),
                effectiveServices,
                request.infrastructure(),
                request.existingNetworkName(),
                request.githubInstallationId(),
                request.githubRepositoryId());

        Optional<AiGenerationResult> cached = generationCache.get(effectiveRequest);
        if (cached.isPresent()) {
            logRepository.save(AiSpecGenerationLogEntity.create(vmId, AiCallKind.GENERATION, "cache-hit",
                    0, 0, 0, true, false, null, true));
            return appendWarnings(cached.get(), discovery.warnings());
        }

        List<String> contexts = effectiveServices.stream().map(ServiceCard::context).distinct().toList();
        Map<String, RepositoryEvidence> evidenceByContext = repositorySnapshotBuilder.analyze(
                effectiveRequest.repoUrl(), effectiveRequest.branch(), effectiveRequest.patToken(), contexts);

        List<ServiceSpec> resolvedSpecs = new ArrayList<>();
        List<String> evidenceRefs = new ArrayList<>();
        List<UnresolvedField> earlyUnresolved = new ArrayList<>();
        List<ServiceCard> aiCards = new ArrayList<>();
        Map<String, RepositoryEvidence> aiEvidence = new LinkedHashMap<>();

        for (ServiceCard card : effectiveServices) {
            RepositoryEvidence evidence = evidenceByContext.get(card.context());
            RuleBasedInferenceResult inference = ruleBasedSpecInferrer.infer(evidence, card);
            if (inference.resolved()) {
                resolvedSpecs.add(inference.spec());
                evidenceRefs.add(card.context() + ":" + inference.detectedType() + ":" + inference.confidence());
            } else {
                aiCards.add(card);
                aiEvidence.put(card.context(), evidence);
                for (String reason : inference.unresolvedReasons()) {
                    earlyUnresolved.add(new UnresolvedField(card.context(), "RULE_UNRESOLVED", reason));
                }
            }
        }

        boolean externalNetwork = effectiveRequest.existingNetworkName() != null
                && !effectiveRequest.existingNetworkName().isBlank();
        String networkName = externalNetwork ? effectiveRequest.existingNetworkName() : NETWORK_NAME;

        AiGenerationResult result = aiCards.isEmpty()
                // 전부 결정론적으로 확정 — AI 호출 0회 (신고된 오분류 버그의 핵심 방지책)
                ? finalizeDeterministic(vmId, resolvedSpecs, effectiveRequest.infrastructure(), evidenceRefs, networkName, externalNetwork)
                : generateWithAi(vmId, effectiveRequest, resolvedSpecs, aiCards, aiEvidence, evidenceRefs,
                        earlyUnresolved, networkName, externalNetwork);
        result = appendWarnings(result, discovery.warnings());
        generationCache.put(effectiveRequest, result);
        return result;
    }

    List<ServiceCard> mergeDiscoveredServices(
            List<ServiceCard> hints,
            List<DiscoveredService> discovered
    ) {
        List<ServiceCard> safeHints = hints == null ? List.of() : hints;
        if (discovered == null || discovered.isEmpty()) return safeHints;

        List<ServiceCard> merged = new ArrayList<>();
        Set<ServiceCard> consumedHints = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (DiscoveredService service : discovered) {
            ServiceCard matchingHint = safeHints.stream()
                    .filter(hint -> normalizeContext(hint.context()).equals(normalizeContext(service.context()))
                            || hint.name().equalsIgnoreCase(service.name()))
                    .findFirst()
                    .orElse(null);
            if (matchingHint != null) {
                consumedHints.add(matchingHint);
                Integer containerPort = DiscoveredService.PORT_SOURCE_DOCKERFILE_EXPOSE.equals(service.portSource())
                        ? service.containerPort()
                        : matchingHint.containerPort();
                merged.add(new ServiceCard(
                        matchingHint.name(),
                        matchingHint.runtime(),
                        service.context(),
                        containerPort,
                        matchingHint.javaVersion(),
                        matchingHint.buildTool(),
                        matchingHint.nodeVersion(),
                        matchingHint.buildCommand(),
                        matchingHint.startCommand(),
                        matchingHint.pythonVersion(),
                        matchingHint.pythonFramework(),
                        matchingHint.expose(),
                        matchingHint.customSubdomain()));
            } else {
                merged.add(toServiceCard(service));
            }
        }
        boolean discoveredSubmodules = discovered.stream()
                .anyMatch(service -> !".".equals(normalizeContext(service.context())));
        safeHints.stream()
                .filter(hint -> !consumedHints.contains(hint))
                .filter(hint -> !(discoveredSubmodules && ".".equals(normalizeContext(hint.context()))))
                .forEach(merged::add);
        return List.copyOf(merged);
    }

    private ServiceCard toServiceCard(DiscoveredService service) {
        return new ServiceCard(
                service.name(),
                service.runtime(),
                service.context(),
                service.containerPort(),
                "java".equals(service.runtime()) ? 21 : null,
                "java".equals(service.runtime()) ? "maven" : null,
                "node".equals(service.runtime()) ? 22 : null,
                null,
                null,
                "python".equals(service.runtime()) ? "3.11" : null,
                null,
                service.expose(),
                null);
    }

    private String normalizeContext(String context) {
        if (context == null || context.isBlank() || ".".equals(context.trim())) return ".";
        String normalized = context.trim().replace('\\', '/');
        while (normalized.startsWith("./")) normalized = normalized.substring(2);
        return normalized.replaceAll("/+$", "");
    }

    private AiGenerationResult appendWarnings(AiGenerationResult result, List<String> additionalWarnings) {
        if (additionalWarnings == null || additionalWarnings.isEmpty()) return result;
        List<String> warnings = new ArrayList<>(result.warnings());
        additionalWarnings.stream().filter(warning -> !warnings.contains(warning)).forEach(warnings::add);
        return new AiGenerationResult(
                result.status(), result.spec(), result.unresolved(), List.copyOf(warnings), result.evidenceRefs());
    }

    private AiGenerationResult finalizeDeterministic(String vmId, List<ServiceSpec> resolvedSpecs,
                                                      List<InfraSelection> infrastructure, List<String> evidenceRefs,
                                                      String networkName, boolean externalNetwork) {
        DeploymentSpec spec = assembleSpec(resolvedSpecs, infrastructure, networkName, externalNetwork);
        List<ValidationError> errors = collectAllErrors(spec);
        boolean ok = errors.isEmpty();
        logRepository.save(AiSpecGenerationLogEntity.create(vmId, AiCallKind.GENERATION, "deterministic-rules",
                0, 0, 0, ok, true, 0, false));
        if (!ok) {
            return new AiGenerationResult(GenerationStatus.INVALID_RESPONSE, null,
                    errors.stream().map(e -> new UnresolvedField("spec", "VALIDATION_ERROR", e.userMessage())).toList(),
                    List.of(), evidenceRefs);
        }
        return new AiGenerationResult(GenerationStatus.READY, spec, List.of(), List.of(), evidenceRefs);
    }

    private AiGenerationResult generateWithAi(String vmId, GenerateDeploymentSpecRequest request,
                                               List<ServiceSpec> resolvedSpecs, List<ServiceCard> aiCards,
                                               Map<String, RepositoryEvidence> aiEvidence, List<String> evidenceRefs,
                                               List<UnresolvedField> earlyUnresolved, String networkName, boolean externalNetwork) {
        int ambiguity = ambiguityScorer.score(aiCards.stream().map(ServiceCard::context).toList(), aiEvidence, request);
        ModelChoice choice = initialChoiceFor(ambiguity);
        String prompt = buildUserPrompt(aiCards, aiEvidence, request.infrastructure());

        long totalInputTokens = 0;
        long totalOutputTokens = 0;
        int correctionAttempts = 0;
        boolean succeeded = false;
        String lastModelUsed = choice.model();
        try {
            AiCallResult call = callModel(choice, prompt);
            totalInputTokens += call.inputTokens();
            totalOutputTokens += call.outputTokens();

            AiServiceSpecOutput output = call.output();
            List<ValidationError> errors = validateAiOutput(output, aiCards);

            while (!errors.isEmpty() && correctionAttempts < MAX_CORRECTION_ATTEMPTS) {
                correctionAttempts++;
                choice = correctionChoice(choice, correctionAttempts);
                lastModelUsed = choice.model();
                call = callModel(choice, buildCorrectionPrompt(prompt, output, errors));
                totalInputTokens += call.inputTokens();
                totalOutputTokens += call.outputTokens();
                output = call.output();
                errors = validateAiOutput(output, aiCards);
            }

            if (!errors.isEmpty()) {
                log.error("AI 배포 스펙 생성 실패 (재교정 {}회 포함): {}", correctionAttempts,
                        errors.stream().map(ValidationError::aiMessage).toList());
                return new AiGenerationResult(GenerationStatus.INVALID_RESPONSE, null,
                        errors.stream().map(e -> new UnresolvedField("ai", "VALIDATION_ERROR", e.userMessage())).toList(),
                        List.of(), evidenceRefs);
            }

            if (output.status() != GenerationStatus.READY) {
                succeeded = true; // AI가 정직하게 "확정 불가"라고 답한 것 — 실패가 아니라 정상 동작
                List<UnresolvedField> combined = new ArrayList<>(earlyUnresolved);
                combined.addAll(output.unresolved());
                return new AiGenerationResult(output.status(), null, combined, output.warnings(), evidenceRefs);
            }

            List<ServiceSpec> allServices = new ArrayList<>(resolvedSpecs);
            allServices.addAll(applyRequestedExposure(output.services(), aiCards));
            DeploymentSpec spec = assembleSpec(allServices, request.infrastructure(), networkName, externalNetwork);
            List<ValidationError> specErrors = collectAllErrors(spec);
            if (!specErrors.isEmpty()) {
                return new AiGenerationResult(GenerationStatus.INVALID_RESPONSE, null,
                        specErrors.stream().map(e -> new UnresolvedField("spec", "VALIDATION_ERROR", e.userMessage())).toList(),
                        List.of(), evidenceRefs);
            }
            succeeded = true;
            return new AiGenerationResult(GenerationStatus.READY, spec, List.of(), output.warnings(), evidenceRefs);
        } finally {
            logRepository.save(AiSpecGenerationLogEntity.create(vmId, AiCallKind.GENERATION, lastModelUsed,
                    totalInputTokens, totalOutputTokens, correctionAttempts, succeeded, false, ambiguity, false));
        }
    }

    private DeploymentSpec assembleSpec(List<ServiceSpec> services, List<InfraSelection> infrastructure,
                                         String networkName, boolean externalNetwork) {
        List<InfrastructureSpec> infra = infrastructure == null ? List.of() : infrastructure.stream()
                .map(i -> new InfrastructureSpec(i.type(),
                        i.version() != null && !i.version().isBlank() ? i.version() : defaultInfraVersion(i.type()),
                        new ExposeSpec(false, null, null, null, null, null, null)))
                .toList();
        return new DeploymentSpec(SCHEMA_VERSION, services, infra, networkName, externalNetwork);
    }

    // 커스텀 CNAME은 저장소 분석이나 AI 추론 대상이 아니라 사용자가 명시적으로 고른 값이다. AI가 null로
    // 누락하거나 다른 문자열을 반환해도 요청 카드의 값을 최종 스펙에 덮어써서 배포 라우트까지 보존한다.
    List<ServiceSpec> applyRequestedExposure(List<ServiceSpec> services, List<ServiceCard> cards) {
        Map<String, ServiceCard> cardsByName = cards.stream()
                .collect(java.util.stream.Collectors.toMap(ServiceCard::name, card -> card, (first, ignored) -> first));
        return services.stream().map(service -> {
            ServiceCard card = cardsByName.get(service.name());
            if (card == null) {
                return service;
            }
            ExposeSpec current = service.expose();
            ExposeSpec exposure = card.expose()
                    ? new ExposeSpec(
                            true,
                            current != null && current.protocol() != null ? current.protocol() : "http",
                            current != null && current.healthCheckPath() != null ? current.healthCheckPath() : "/",
                            card.customSubdomain(),
                            current != null ? current.routePath() : null,
                            current != null ? current.stripPrefix() : null,
                            current != null ? current.routeMode() : null)
                    : null;
            return new ServiceSpec(service.name(), service.deploymentMode(), service.build(), service.artifact(),
                    service.run(), service.context(), exposure);
        }).toList();
    }

    private String defaultInfraVersion(String type) {
        return switch (type) {
            case "postgresql" -> "16";
            case "mysql" -> "8";
            case "redis" -> "7";
            case "mongodb" -> "7";
            default -> "latest";
        };
    }

    // 서비스가 여러 개면 모노레포로 간주해 처음부터 escalated 모델 사용하던 기존 방식 대신,
    // AmbiguityScorer가 계산한 점수 구간으로 라우팅 (9절)
    private ModelChoice initialChoiceFor(int ambiguityScore) {
        if (ambiguityScore >= 6) {
            return new ModelChoice(escalatedModel, ReasoningEffort.MEDIUM);
        }
        if (ambiguityScore >= 3) {
            return new ModelChoice(standardModel, ReasoningEffort.MEDIUM);
        }
        return new ModelChoice(standardModel, ReasoningEffort.LOW);
    }

    private ModelChoice correctionChoice(ModelChoice previous, int attemptNumber) {
        if (previous.model().equals(escalatedModel)) {
            return new ModelChoice(escalatedModel, ReasoningEffort.MEDIUM);
        }
        return attemptNumber == 1
                ? new ModelChoice(standardModel, ReasoningEffort.MEDIUM)
                : new ModelChoice(escalatedModel, ReasoningEffort.MEDIUM);
    }

    // 구조적 검증(DeploymentSpecValidator) + 보안/정책 검증(DeploymentSpecPolicyValidator)을 함께 수행 —
    // 최종적으로 확정된 스펙(결정론적 + AI 해결분 합산본)에 대해서만 호출한다 (12절 — 관심사 분리는 유지하되
    // 렌더링 직전엔 항상 둘 다 통과해야 함).
    private List<ValidationError> collectAllErrors(DeploymentSpec spec) {
        List<ValidationError> errors = new ArrayList<>(deploymentSpecValidator.collectErrors(spec));
        errors.addAll(deploymentSpecPolicyValidator.collectErrors(spec));
        return errors;
    }

    private List<ValidationError> validateAiOutput(AiServiceSpecOutput output, List<ServiceCard> requestedCards) {
        List<ValidationError> errors = new ArrayList<>();
        if (output.status() == GenerationStatus.READY) {
            if (output.services() == null || output.services().size() != requestedCards.size()) {
                errors.add(new ValidationError(
                        "요청한 서비스 수와 응답의 services 개수가 일치하지 않습니다",
                        "The number of requested services does not match the number of services in the response"));
            } else {
                List<String> requestedNames = requestedCards.stream().map(ServiceCard::name).sorted().toList();
                List<String> outputNames = output.services().stream().map(ServiceSpec::name).sorted().toList();
                if (!requestedNames.equals(outputNames)) {
                    errors.add(new ValidationError(
                            "요청한 서비스 이름과 응답의 services 이름이 일치하지 않습니다",
                            "The requested service names do not match the service names in the response"));
                }
                // 사용자 설정인 customSubdomain은 모델 응답을 신뢰하지 않고 덮어쓴 상태로 검증한다.
                DeploymentSpec probe = assembleSpec(
                        applyRequestedExposure(output.services(), requestedCards),
                        List.of(), NETWORK_NAME, false);
                errors.addAll(deploymentSpecValidator.collectErrors(probe));
            }
        }
        return errors;
    }

    private AiCallResult callModel(ModelChoice choice, String userPrompt) {
        StructuredResponseCreateParams<AiServiceSpecOutput> params = ResponseCreateParams.builder()
                .model(choice.model())
                .reasoning(Reasoning.builder().effort(choice.effort()).build())
                .instructions(SYSTEM_PROMPT)
                .input(userPrompt)
                .text(AiServiceSpecOutput.class)
                .build();

        StructuredResponse<AiServiceSpecOutput> response;
        try {
            response = client.responses().create(params);
        } catch (Exception e) {
            log.error("AI 배포 스펙 생성 요청 실패: {}", e.getMessage());
            throw new OpsException(OpsErrorCode.AI_SPEC_GENERATION_FAILED);
        }

        AiServiceSpecOutput output = response.output().stream()
                .filter(item -> item.message().isPresent())
                .flatMap(item -> item.message().get().content().stream())
                .filter(StructuredResponseOutputMessage.Content::isOutputText)
                .map(StructuredResponseOutputMessage.Content::asOutputText)
                .findFirst()
                .orElseThrow(() -> new OpsException(OpsErrorCode.AI_SPEC_INVALID_RESPONSE));

        long inputTokens = response.usage().map(usage -> usage.inputTokens()).orElse(0L);
        long outputTokens = response.usage().map(usage -> usage.outputTokens()).orElse(0L);

        return new AiCallResult(output, inputTokens, outputTokens);
    }

    private String buildUserPrompt(List<ServiceCard> cards, Map<String, RepositoryEvidence> evidenceByContext,
                                    List<InfraSelection> infrastructure) {
        StringBuilder sb = new StringBuilder("Services requiring resolution:\n");
        for (ServiceCard card : cards) {
            RepositoryEvidence evidence = evidenceByContext.get(card.context());
            sb.append("- name=").append(card.name())
                    .append(", context=").append(card.context())
                    .append(", containerPort=").append(card.containerPort())
                    .append(", expose=").append(card.expose())
                    .append(", customSubdomain=").append(card.customSubdomain())
                    .append(", user-selected category=").append(card.runtime())
                    .append("\n  repository analysis: ").append(describeEvidence(evidence))
                    .append("\n");
        }
        if (infrastructure != null && !infrastructure.isEmpty()) {
            sb.append("Shared infrastructure:\n");
            for (InfraSelection infra : infrastructure) {
                sb.append("- type=").append(infra.type()).append(", version=").append(infra.version()).append("\n");
            }
        }
        return sb.toString();
    }

    private String describeEvidence(RepositoryEvidence evidence) {
        if (evidence == null || evidence.files() == null) {
            return "no analysis result";
        }
        return "dockerfile=" + evidence.files().dockerfile()
                + ", packageJson=" + evidence.files().packageJson()
                + ", pomXml=" + evidence.files().pomXml()
                + ", gradleBuild=" + evidence.files().gradleBuild()
                + ", requirementsTxt=" + evidence.files().requirementsTxt()
                + ", pyprojectToml=" + evidence.files().pyprojectToml()
                + ", indexHtml=" + evidence.files().indexHtml();
    }

    private String buildCorrectionPrompt(String originalPrompt, AiServiceSpecOutput previous, List<ValidationError> errors) {
        return originalPrompt
                + "\n\nPrevious response:\n" + previous
                + "\n\nValidation errors:\n- " + errors.stream().map(ValidationError::aiMessage).reduce((a, b) -> a + "\n- " + b).orElse("")
                + "\n\nRe-output the full response, fixing only the fields related to the errors above. Keep the schema and all other rules unchanged.";
    }

    private record ModelChoice(String model, ReasoningEffort effort) {
    }

    private record AiCallResult(AiServiceSpecOutput output, long inputTokens, long outputTokens) {
    }
}
