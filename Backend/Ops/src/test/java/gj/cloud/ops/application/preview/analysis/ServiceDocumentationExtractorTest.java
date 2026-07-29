package gj.cloud.ops.application.preview.analysis;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceDocumentationExtractorTest {

    private final ServiceDocumentationExtractor extractor =
            new ServiceDocumentationExtractor(new OpenApiDocumentSecurityValidator());

    @Test
    void extractsServiceContextAndDropsExecutableOrDecorativeContent() {
        String html = """
                <html>
                  <head>
                    <title>감자마켓 API</title>
                    <meta name="description" content="지역 기반 중고 거래 서비스">
                    <style>.secret { color: red }</style>
                    <script>window.secret = "token"</script>
                  </head>
                  <body>
                    <nav>문서 메뉴 로그인</nav>
                    <main>
                      <h1>감자마켓</h1>
                      <p>사용자는 상품을 검색하고 판매자와 채팅한 뒤 거래를 예약합니다.</p>
                      <pre>curl https://api.example.com/private</pre>
                    </main>
                  </body>
                </html>
                """;

        String extracted = extractor.extractText(html.getBytes(StandardCharsets.UTF_8));

        assertThat(extracted)
                .contains("감자마켓 API", "지역 기반 중고 거래 서비스", "상품을 검색하고 판매자와 채팅")
                .doesNotContain("window.secret", "문서 메뉴", "curl https://");
    }
}
