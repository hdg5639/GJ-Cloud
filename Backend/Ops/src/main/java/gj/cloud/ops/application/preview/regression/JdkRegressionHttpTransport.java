package gj.cloud.ops.application.preview.regression;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.global.exception.OpsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class JdkRegressionHttpTransport implements RegressionHttpTransport {

    private static final Set<String> FORBIDDEN_HEADERS =
            Set.of("host", "content-length", "connection", "transfer-encoding", "upgrade");

    private final ObjectMapper objectMapper;
    private final RegressionTargetSecurityValidator securityValidator;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final int maxResponseBytes;

    public JdkRegressionHttpTransport(
            ObjectMapper objectMapper,
            RegressionTargetSecurityValidator securityValidator,
            @Value("${ops.regression-executor.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${ops.regression-executor.request-timeout-ms:20000}") long requestTimeoutMs,
            @Value("${ops.regression-executor.max-response-bytes:2097152}") int maxResponseBytes
    ) {
        this.objectMapper = objectMapper;
        this.securityValidator = securityValidator;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public Response execute(Request request) {
        securityValidator.validate(request.url());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(request.url()))
                .timeout(requestTimeout)
                .header("Accept", "application/json, */*");
        request.headers().forEach((name, value) -> {
            if (safeHeader(name, value)) builder.header(name, value);
        });

        String method = request.method().toUpperCase(Locale.ROOT);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
        if (!request.body().isEmpty() && !method.equals("GET") && !method.equals("DELETE")) {
            try {
                publisher = HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(request.body()), StandardCharsets.UTF_8);
                builder.header("Content-Type", "application/json");
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("회귀 테스트 요청 본문을 직렬화하지 못했습니다.", error);
            }
        }
        builder.method(method, publisher);

        try {
            HttpResponse<InputStream> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] responseBody;
            try (InputStream input = response.body()) {
                responseBody = input.readNBytes(maxResponseBytes + 1);
            }
            if (responseBody.length > maxResponseBytes) {
                throw new IllegalStateException("회귀 테스트 응답이 허용 크기를 초과했습니다.");
            }
            Map<String, String> headers = new LinkedHashMap<>();
            response.headers().map().forEach((name, values) ->
                    headers.put(name, String.join(", ", values)));
            return new Response(response.statusCode(), headers, parseBody(responseBody));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("회귀 테스트 요청이 중단되었습니다.", error);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("회귀 테스트 대상 API에 연결하지 못했습니다.", error);
        } catch (OpsException error) {
            throw error;
        }
    }

    private Object parseBody(byte[] body) {
        if (body.length == 0) return null;
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (Exception ignored) {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private boolean safeHeader(String name, String value) {
        if (name == null || name.isBlank() || value == null || value.contains("\r") || value.contains("\n")) {
            return false;
        }
        return !FORBIDDEN_HEADERS.contains(name.toLowerCase(Locale.ROOT));
    }
}
