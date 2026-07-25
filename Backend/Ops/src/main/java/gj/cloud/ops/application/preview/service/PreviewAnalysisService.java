package gj.cloud.ops.application.preview.service;

import gj.cloud.ops.application.deployment.ai.GenerationStatus;
import gj.cloud.ops.application.deployment.ai.UnresolvedField;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.CapabilityExtractor;
import gj.cloud.ops.application.preview.analysis.CapabilityType;
import gj.cloud.ops.application.preview.analysis.OpenApiEvidence;
import gj.cloud.ops.application.preview.analysis.OpenApiNormalizer;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.PageDraftGenerator;
import gj.cloud.ops.application.preview.analysis.SecuritySchemeEvidence;
import gj.cloud.ops.application.preview.dto.PreviewAnalysisResult;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest;
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

    public PreviewAnalysisResult analyze(PreviewAnalyzeRequest request) {
        OpenApiEvidence evidence = openApiNormalizer.normalize(request.apiDocsUrl());
        List<Capability> capabilities = capabilityExtractor.extract(evidence);
        List<PageDraft> pages = pageDraftGenerator.generate(capabilities);

        List<UnresolvedField> unresolved = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        boolean hasLoginCapability = capabilities.stream().anyMatch(c -> c.type() == CapabilityType.LOGIN);
        boolean anySupportedScheme = evidence.securitySchemes().stream().anyMatch(SecuritySchemeEvidence::isSupportedByMvp);
        boolean anySchemePresent = !evidence.securitySchemes().isEmpty();

        if (anySchemePresent && !anySupportedScheme) {
            unresolved.add(new UnresolvedField("auth.scheme", "AUTH_SCHEME_UNSUPPORTED",
                    "지원하지 않는 인증 방식입니다. Bearer Token 또는 API Key만 자동 인식합니다."));
        } else if (anySchemePresent && !hasLoginCapability) {
            unresolved.add(new UnresolvedField("auth.login", "AUTH_LOGIN_NOT_FOUND",
                    "인증이 필요한 API로 보이지만 로그인 오퍼레이션을 확인하지 못했습니다."));
        }

        if (pages.isEmpty()) {
            unresolved.add(new UnresolvedField("pages", "NO_PAGES_GENERATED",
                    "문서에서 생성 가능한 페이지를 찾지 못했습니다."));
        }

        if (evidence.truncatedOperationCount() > 0) {
            warnings.add("API 개수가 많아 " + evidence.truncatedOperationCount() + "개 오퍼레이션은 분석에서 제외되었습니다.");
        }

        List<String> evidenceRefs = capabilities.stream()
                .flatMap(c -> c.evidence().stream())
                .distinct()
                .toList();

        GenerationStatus status = pages.isEmpty() ? GenerationStatus.UNSUPPORTED
                : unresolved.isEmpty() ? GenerationStatus.READY
                : GenerationStatus.NEEDS_INPUT;

        return new PreviewAnalysisResult(
                status, evidence.serverUrls(), capabilities, pages, unresolved, warnings, evidenceRefs);
    }
}
