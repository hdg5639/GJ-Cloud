package gj.cloud.ops.application.preview.service;

import gj.cloud.ops.application.preview.analysis.OpenApiEvidence;
import gj.cloud.ops.application.preview.analysis.ServiceDocumentationExtractor;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ServiceContextResolver {

    private static final int MAX_CONTEXT_CHARS = 12_000;

    private final ServiceDocumentationExtractor documentationExtractor;

    public ResolvedServiceContext resolve(PreviewAnalyzeRequest request, OpenApiEvidence evidence) {
        List<String> sections = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        if (hasText(request.serviceDescription())) {
            sections.add("[사용자 서비스 설명]\n" + request.serviceDescription().trim());
            sources.add("USER_DESCRIPTION");
        }
        if (hasText(request.scenarioIntent())) {
            sections.add("[사용자가 원하는 시나리오]\n" + request.scenarioIntent().trim());
            sources.add("SCENARIO_INTENT");
        }
        if (hasText(request.documentationPageUrl())) {
            String documentation = documentationExtractor.extract(request.documentationPageUrl().trim());
            if (hasText(documentation)) {
                sections.add("[서비스 문서 페이지]\n" + documentation);
                sources.add("DOCUMENTATION_PAGE");
            }
        }
        if (hasText(evidence.description())) {
            sections.add("[OpenAPI 서비스 설명]\n" + evidence.description().trim());
            sources.add("OPENAPI_INFO");
        } else if (hasText(evidence.title())) {
            sections.add("[OpenAPI 서비스명]\n" + evidence.title().trim());
            sources.add("OPENAPI_INFO");
        }
        String resolved = String.join("\n\n", sections);
        if (resolved.length() > MAX_CONTEXT_CHARS) {
            resolved = resolved.substring(0, MAX_CONTEXT_CHARS);
        }
        return new ResolvedServiceContext(resolved, List.copyOf(sources));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ResolvedServiceContext(String description, List<String> sources) {
    }
}
