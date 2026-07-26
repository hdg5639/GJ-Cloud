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
import gj.cloud.ops.application.preview.dto.PreviewAnalysisResult;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest;
import gj.cloud.ops.application.preview.planning.RuleBasedPagePlanGenerator;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import gj.cloud.ops.application.preview.planning.model.PagePlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
    private final AuthStrategyDetector authStrategyDetector;
    private final PreviewBlockResolver blockResolver;

    public PreviewAnalysisResult analyze(PreviewAnalyzeRequest request) {
        OpenApiEvidence evidence = openApiNormalizer.normalize(request.apiDocsUrl());
        List<Capability> capabilities = capabilityExtractor.extract(evidence);

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
        boolean anySupportedScheme = evidence.securitySchemes().stream().anyMatch(SecuritySchemeEvidence::isSupportedByMvp);
        boolean anySchemePresent = !evidence.securitySchemes().isEmpty();

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

        if (evidence.truncatedOperationCount() > 0) {
            warnings.add("API 개수가 많아 " + evidence.truncatedOperationCount() + "개 오퍼레이션은 분석에서 제외되었습니다.");
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

        AuthStrategy authStrategy = authStrategyDetector.detect(evidence.securitySchemes());
        List<PagePlan> pagePlans = PagePlanMapper.from(pages, capabilities);

        return new PreviewAnalysisResult(
                status, evidence.serverUrls(), capabilities, pages, pagePlans, unresolved, warnings, evidenceRefs,
                authStrategy, generationMode);
    }
}
