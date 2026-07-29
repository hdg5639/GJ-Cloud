package gj.cloud.ops.application.preview.analysis;

import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Swagger UI, Redoc, 서비스 소개 페이지에서 AI가 참고할 설명 텍스트만 안전하게 추출한다.
 * HTML/스크립트는 저장·실행하지 않으며 외부 링크나 리소스를 추가로 조회하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceDocumentationExtractor {

    private static final List<String> CONTENT_SELECTORS =
            List.of("main", "article", "[role=main]", ".description", ".markdown", ".renderedMarkdown");
    private static final int MAX_EXTRACTED_CHARS = 6_000;

    private final OpenApiDocumentSecurityValidator securityValidator;

    @Value("${ops.preview.documentation-fetch-timeout-ms:10000}")
    private long fetchTimeoutMs = 10_000;

    @Value("${ops.preview.documentation-max-bytes:1048576}")
    private int maxDocumentBytes = 1_048_576;

    public String extract(String pageUrl) {
        if (pageUrl == null || pageUrl.isBlank()) {
            return null;
        }
        securityValidator.validate(pageUrl);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(fetchTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pageUrl))
                .timeout(Duration.ofMillis(fetchTimeoutMs))
                .header("Accept", "text/html, text/plain;q=0.9")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("서비스 문서 페이지 fetch 실패(status={}): url={}", response.statusCode(), pageUrl);
                throw new OpsException(OpsErrorCode.API_DOCS_FETCH_FAILED);
            }
            String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase();
            if (!contentType.isBlank() && !contentType.contains("text/html") && !contentType.contains("text/plain")) {
                throw new OpsException(OpsErrorCode.API_DOCS_PARSE_FAILED);
            }
            return extractText(readBounded(response.body()));
        } catch (IOException e) {
            log.warn("서비스 문서 페이지 fetch 실패: url={}, error={}", pageUrl, e.getMessage());
            throw new OpsException(OpsErrorCode.API_DOCS_FETCH_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpsException(OpsErrorCode.API_DOCS_FETCH_FAILED);
        }
    }

    String extractText(byte[] html) {
        Document document = Jsoup.parse(new String(html, StandardCharsets.UTF_8));
        document.select("script,style,noscript,svg,nav,footer,header,pre,code").remove();

        LinkedHashSet<String> sections = new LinkedHashSet<>();
        add(sections, document.title());
        Element metaDescription = document.selectFirst("meta[name=description]");
        if (metaDescription != null) add(sections, metaDescription.attr("content"));
        for (String selector : CONTENT_SELECTORS) {
            for (Element element : document.select(selector)) {
                add(sections, element.text());
            }
        }
        if (sections.size() < 2 && document.body() != null) {
            add(sections, document.body().text());
        }
        String text = String.join("\n\n", sections);
        return text.length() <= MAX_EXTRACTED_CHARS ? text : text.substring(0, MAX_EXTRACTED_CHARS);
    }

    private byte[] readBounded(InputStream in) throws IOException {
        try (in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(chunk)) != -1) {
                total += read;
                if (total > maxDocumentBytes) {
                    throw new OpsException(OpsErrorCode.API_DOCS_TOO_LARGE);
                }
                out.write(chunk, 0, read);
            }
            return out.toByteArray();
        }
    }

    private void add(LinkedHashSet<String> sections, String value) {
        if (value == null) return;
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (!normalized.isBlank()) sections.add(normalized);
    }
}
