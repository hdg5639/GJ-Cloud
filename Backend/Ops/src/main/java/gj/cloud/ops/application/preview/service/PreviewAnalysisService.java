package gj.cloud.ops.application.preview.service;

import gj.cloud.ops.application.deployment.ai.GenerationStatus;
import gj.cloud.ops.application.deployment.ai.UnresolvedField;
import gj.cloud.ops.application.preview.analysis.AuthStrategy;
import gj.cloud.ops.application.preview.analysis.AuthStrategyDetector;
import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityExtractor;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.CompatibilityFinding;
import gj.cloud.ops.application.preview.analysis.CompatibilityValidator;
import gj.cloud.ops.application.preview.analysis.GenerationMode;
import gj.cloud.ops.application.preview.analysis.OpenApiEvidence;
import gj.cloud.ops.application.preview.analysis.OpenApiNormalizer;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.PageDraftGenerator;
import gj.cloud.ops.application.preview.analysis.PreviewBlockResolver;
import gj.cloud.ops.application.preview.analysis.SecuritySchemeEvidence;
import gj.cloud.ops.application.preview.analysis.ApiOperationEvidence;
import gj.cloud.ops.application.preview.dto.PreviewAnalysisResult;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest;
import gj.cloud.ops.application.preview.flow.RuleBasedFlowGenerator;
import gj.cloud.ops.application.preview.planning.RuleBasedPagePlanGenerator;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import gj.cloud.ops.application.preview.planning.model.PagePlanMapper;
import gj.cloud.ops.application.preview.scenario.ScenarioGenerationService;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioGenerationResult;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Phase A 오케스트레이션 — OpenApiNormalizer(결정론적 파싱) → CapabilityExtractor(규칙 기반 추론) →
// PageDraftGenerator(페이지 그룹핑) → 신뢰도/상태 매핑. 이 단계는 AI를 전혀 호출하지 않는다
// (GamjaBox_2.0_Key_Features.md 41절 MVP 분석 범위 그대로, AI 검수는 Phase B에서 추가).
@Service
@RequiredArgsConstructor
public class PreviewAnalysisService {

    private final OpenApiNormalizer openApiNormalizer;
    private final CapabilityExtractor capabilityExtractor;
    private final PageDraftGenerator pageDraftGenerator;
    private final RuleBasedPagePlanGenerator ruleBasedPagePlanGenerator;
    private final RuleBasedFlowGenerator ruleBasedFlowGenerator;
    private final AuthStrategyDetector authStrategyDetector;
    private final PreviewBlockResolver blockResolver;
    private final ScenarioGenerationService scenarioGenerationService;
    private final ServiceContextResolver serviceContextResolver;

    public PreviewAnalysisResult analyze(PreviewAnalyzeRequest request, String requesterUserId) {
        OpenApiEvidence evidence = normalize(request);
        List<Capability> allCapabilities = capabilityExtractor.extract(evidence);
        List<Capability> capabilities = selectCapabilities(allCapabilities, request.selectedCapabilityIds());
        OpenApiEvidence scopedEvidence = scopeEvidence(evidence, capabilities);
        ServiceContextResolver.ResolvedServiceContext serviceContext =
                serviceContextResolver.resolve(request, scopedEvidence);

        // Direction Recovery Change Request §4.1 — purpose가 실제로 페이지 구성에 반영되는지가 이
        // 서비스의 핵심 정체성이다(project_auto_preview_product_definition). purpose가 없으면(다른
        // API 클라이언트가 생략한 경우) 기존 PageDraftGenerator로 대체하고 FALLBACK_CRUD로 리포트한다.
        List<PageDraft> pages;
        GenerationMode generationMode;
        if (request.purpose() != null) {
            pages = ruleBasedPagePlanGenerator.generate(capabilities, request.purpose());
            generationMode = GenerationMode.RULE_BASED;
        } else {
            pages = pageDraftGenerator.generate(capabilities);
            generationMode = GenerationMode.FALLBACK_CRUD;
        }

        List<UnresolvedField> unresolved = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Capability loginCapability = capabilities.stream()
                .filter(c -> c.type() == CapabilityType.LOGIN)
                .findFirst()
                .orElse(null);
        boolean hasLoginCapability = loginCapability != null;
        boolean anySupportedScheme = scopedEvidence.securitySchemes().stream()
                .anyMatch(SecuritySchemeEvidence::isSupportedByMvp);
        boolean anySchemePresent = !scopedEvidence.securitySchemes().isEmpty();

        if (anySchemePresent && !anySupportedScheme) {
            unresolved.add(new UnresolvedField("auth.scheme", "AUTH_SCHEME_UNSUPPORTED",
                    "지원하지 않는 인증 방식입니다. Bearer Token 또는 API Key만 자동 인식합니다."));
        } else if (anySchemePresent && !hasLoginCapability) {
            unresolved.add(new UnresolvedField("auth.login", "AUTH_LOGIN_NOT_FOUND",
                    "인증이 필요한 API로 보이지만 로그인 오퍼레이션을 확인하지 못했습니다."));
        } else if (hasLoginCapability && loginCapability.accessTokenPath() == null) {
            unresolved.add(new UnresolvedField("auth.login.accessTokenPath", "ACCESS_TOKEN_PATH_UNKNOWN",
                    "로그인 응답에서 access token 위치를 확인하지 못했습니다. 아래에서 직접 지정해주세요."));
        }

        if (pages.isEmpty()) {
            unresolved.add(new UnresolvedField("pages", "NO_PAGES_GENERATED",
                    "문서에서 생성 가능한 페이지를 찾지 못했습니다."));
        }

        if (scopedEvidence.truncatedOperationCount() > 0) {
            warnings.add("API 개수가 많아 " + scopedEvidence.truncatedOperationCount()
                    + "개 오퍼레이션은 분석에서 제외되었습니다.");
        }

        for (PageDraft page : pages) {
            List<Block> blocks = blockResolver.resolve(page, capabilities);
            for (CompatibilityFinding finding : CompatibilityValidator.validate(page, blocks, capabilities)) {
                warnings.add(finding.message());
            }
        }

        List<String> evidenceRefs = capabilities.stream()
                .flatMap(c -> c.evidence().stream())
                .distinct()
                .toList();

        GenerationStatus status = pages.isEmpty() ? GenerationStatus.UNSUPPORTED
                : unresolved.isEmpty() ? GenerationStatus.READY
                : GenerationStatus.NEEDS_INPUT;

        AuthStrategy authStrategy = authStrategyDetector.detect(scopedEvidence.securitySchemes());
        List<PagePlan> pagePlans = PagePlanMapper.from(pages, capabilities);

        RuleBasedFlowGenerator.ValidatedResult flowResult = ruleBasedFlowGenerator.generateValidated(pagePlans, capabilities);
        if (!flowResult.errors().isEmpty()) {
            // §16 안전 폴백과 동일 원칙 — pages/capabilities는 이 실패와 무관하게 여전히 유효하므로
            // 전체 분석 자체를 실패시키지 않고, flows/bindings만 비운 채 사유를 warnings로 남긴다.
            warnings.add("생성된 workflow가 검증에 실패해 제외되었습니다: " + String.join("; ", flowResult.errors()));
        }

        ScenarioGenerationResult scenarioResult = scenarioGenerationService.generate(
                requesterUserId, scopedEvidence, serviceContext.description(), request.purpose(),
                capabilities, request.previewMode());
        scenarioResult.diagnostics().forEach(diagnostic -> {
            if (diagnostic.status() != gj.cloud.ops.application.preview.scenario.ScenarioModels.DiagnosticStatus.SUPPORTED) {
                warnings.add("Scenario " + (diagnostic.scenarioId() == null ? "" : diagnostic.scenarioId() + " ")
                        + "컴파일 진단: " + diagnostic.message());
            }
        });

        return new PreviewAnalysisResult(
                status, evidence.serverUrls(), capabilities, allCapabilities, pages, pagePlans, flowResult.result().flows(),
                flowResult.result().bindings(), unresolved, warnings, evidenceRefs, authStrategy, generationMode,
                scenarioResult.serviceUnderstanding(), scenarioResult.scenarios(),
                scenarioResult.diagnostics(), scenarioResult.previewMode(),
                scenarioResult.planningSource(), scenarioResult.promptVersion(),
                capabilities.stream().map(Capability::id).toList(),
                serviceContext.description(), serviceContext.sources());
    }

    private OpenApiEvidence normalize(PreviewAnalyzeRequest request) {
        boolean hasUrl = request.apiDocsUrl() != null && !request.apiDocsUrl().isBlank();
        boolean hasContent = request.apiDocsContent() != null && !request.apiDocsContent().isBlank();
        if (hasUrl == hasContent) {
            throw new OpsException(OpsErrorCode.PREVIEW_API_SOURCE_REQUIRED);
        }
        return hasContent
                ? openApiNormalizer.normalizeContent(request.apiDocsContent())
                : openApiNormalizer.normalize(request.apiDocsUrl().trim());
    }

    /**
     * 태그로 고른 capability와 그 의존성을 생성 범위로 사용한다. 로그인 capability는 인증된
     * 사용자 흐름을 구성할 때 빠지지 않도록 자동 포함한다. 반환 순서는 원문 분석 순서를 유지한다.
     */
    private List<Capability> selectCapabilities(List<Capability> all, List<String> selectedIds) {
        if (selectedIds == null || selectedIds.isEmpty()) {
            return all;
        }
        Map<String, Capability> byId = all.stream()
                .collect(Collectors.toMap(Capability::id, Function.identity(), (left, right) -> left));
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        for (String id : selectedIds) {
            if (byId.containsKey(id)) expanded.add(id);
        }
        if (expanded.isEmpty()) {
            throw new OpsException(OpsErrorCode.PREVIEW_CAPABILITY_SELECTION_INVALID);
        }
        all.stream()
                .filter(capability -> capability.type() == CapabilityType.LOGIN)
                .map(Capability::id)
                .forEach(expanded::add);
        boolean changed;
        do {
            changed = false;
            for (String id : List.copyOf(expanded)) {
                Capability capability = byId.get(id);
                if (capability == null) continue;
                for (String dependency : capability.dependencies()) {
                    if (byId.containsKey(dependency) && expanded.add(dependency)) changed = true;
                }
            }
        } while (changed);
        Set<String> included = Set.copyOf(expanded);
        return all.stream().filter(capability -> included.contains(capability.id())).toList();
    }

    private OpenApiEvidence scopeEvidence(OpenApiEvidence evidence, List<Capability> capabilities) {
        Set<String> operationIds = capabilities.stream()
                .map(Capability::operationId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        Set<String> methodPaths = capabilities.stream()
                .map(capability -> capability.method() + " " + capability.path())
                .collect(Collectors.toSet());
        List<ApiOperationEvidence> operations = evidence.operations().stream()
                .filter(operation -> operation.operationId() != null && operationIds.contains(operation.operationId())
                        || methodPaths.contains(operation.method() + " " + operation.path()))
                .toList();
        return new OpenApiEvidence(
                evidence.title(), evidence.description(), evidence.version(), evidence.serverUrls(),
                evidence.securitySchemes(), operations, evidence.truncatedOperationCount());
    }
}
