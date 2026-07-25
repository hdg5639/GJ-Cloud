package gj.cloud.ops.application.preview.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// GamjaBox_2.0_Key_Features.md 5절 — OpenAPI에서 결정론적으로 뽑을 수 있는 정보는 AI를 부르지 않고
// 코드로 처리한다. RepositorySnapshotBuilder(저장소 버전)와 같은 역할을 OpenAPI 문서에 대해 수행함.
// $ref는 이 문서 내부(#/components/schemas/*)만 한 단계 해석하고, 외부 URL을 가리키는 $ref는 절대
// 따라가지 않는다(추가 네트워크 호출은 새 SSRF 벡터가 됨).
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenApiNormalizer {

    private static final Set<String> SUPPORTED_METHODS = Set.of("get", "post", "put", "patch", "delete");
    private static final Set<String> ARRAY_ENVELOPE_KEYS =
            Set.of("content", "items", "data", "list", "results", "records", "rows", "elements", "result", "payload");
    // 순환 $ref로 인한 무한 재귀를 막기 위한 목록 봉투 해제 최대 깊이.
    private static final int MAX_ENVELOPE_UNWRAP_DEPTH = 4;
    // 응답 스칼라 필드 dot-path 수집 상한 — 필드가 매우 많은 응답 스키마에서 과도한 작업을 막는다.
    private static final int MAX_RESPONSE_FIELD_PATHS = 60;

    private final ObjectMapper objectMapper;

    // 필드 초기값을 @Value 기본값과 맞춰둠 — Spring이 주입하면 덮어쓰지만, 테스트에서 new로 직접
    // 생성할 때도(파싱/추출 로직만 검증하고 싶을 때) 0으로 남아 모든 오퍼레이션이 스킵되는 걸 방지.
    @Value("${ops.preview.openapi-fetch-timeout-ms:15000}")
    private long fetchTimeoutMs = 15000;

    @Value("${ops.preview.openapi-max-bytes:5242880}") // 5MB
    private int maxDocumentBytes = 5_242_880;

    @Value("${ops.preview.openapi-max-operations:300}")
    private int maxOperations = 300;

    private final OpenApiDocumentSecurityValidator securityValidator;

    public OpenApiEvidence normalize(String apiDocsUrl) {
        securityValidator.validate(apiDocsUrl);
        byte[] body = fetch(apiDocsUrl);
        JsonNode root = parse(body);
        return extract(root);
    }

    private byte[] fetch(String apiDocsUrl) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(fetchTimeoutMs))
                // 검증을 통과한 뒤 리다이렉트로 내부 주소를 우회할 수 있으므로 따라가지 않음
                // (GitReleaseManager의 http.followRedirects=false와 동일한 이유, DEP-001).
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiDocsUrl))
                .timeout(Duration.ofMillis(fetchTimeoutMs))
                .header("Accept", "application/json, application/yaml, text/yaml, */*")
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            log.warn("API 문서 fetch 실패: url={}, error={}", apiDocsUrl, e.getMessage());
            throw new OpsException(OpsErrorCode.API_DOCS_FETCH_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpsException(OpsErrorCode.API_DOCS_FETCH_FAILED);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("API 문서 fetch 실패(status={}): url={}", response.statusCode(), apiDocsUrl);
            throw new OpsException(OpsErrorCode.API_DOCS_FETCH_FAILED);
        }

        return readBounded(response.body());
    }

    private byte[] readBounded(InputStream in) {
        try (in) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(chunk)) != -1) {
                total += read;
                if (total > maxDocumentBytes) {
                    throw new OpsException(OpsErrorCode.API_DOCS_TOO_LARGE);
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new OpsException(OpsErrorCode.API_DOCS_FETCH_FAILED);
        }
    }

    // 테스트에서 fetch(네트워크) 없이 파싱~추출 로직만 검증할 수 있도록 패키지 접근으로 열어둠.
    JsonNode parse(byte[] body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException jsonError) {
            try {
                LoaderOptions loaderOptions = new LoaderOptions();
                Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));
                Object parsed = yaml.load(new String(body, StandardCharsets.UTF_8));
                return objectMapper.valueToTree(parsed);
            } catch (Exception yamlError) {
                throw new OpsException(OpsErrorCode.API_DOCS_PARSE_FAILED);
            }
        }
    }

    OpenApiEvidence extract(JsonNode root) {
        String openapiVersion = root.path("openapi").asText(null);
        if (openapiVersion == null || !openapiVersion.startsWith("3.")) {
            throw new OpsException(OpsErrorCode.API_DOCS_UNSUPPORTED_VERSION);
        }

        String title = root.path("info").path("title").asText(null);
        String version = root.path("info").path("version").asText(null);

        List<String> serverUrls = new ArrayList<>();
        for (JsonNode server : arrayOrEmpty(root.path("servers"))) {
            String url = server.path("url").asText(null);
            if (url != null && !url.isBlank()) {
                serverUrls.add(url);
            }
        }

        List<SecuritySchemeEvidence> securitySchemes = extractSecuritySchemes(root);
        boolean globalSecurityPresent = root.path("security").isArray() && !root.path("security").isEmpty();
        JsonNode schemas = root.path("components").path("schemas");

        List<ApiOperationEvidence> operations = new ArrayList<>();
        int skipped = 0;
        JsonNode paths = root.path("paths");
        var pathFieldNames = paths.fieldNames();
        while (pathFieldNames.hasNext()) {
            String path = pathFieldNames.next();
            JsonNode pathItem = paths.path(path);
            List<ApiParameterEvidence> sharedParams = extractParameters(pathItem.path("parameters"));

            var methodFieldNames = pathItem.fieldNames();
            while (methodFieldNames.hasNext()) {
                String method = methodFieldNames.next();
                if (!SUPPORTED_METHODS.contains(method.toLowerCase())) {
                    continue;
                }
                if (operations.size() + skipped >= maxOperations) {
                    skipped++;
                    continue;
                }
                JsonNode operation = pathItem.path(method);
                operations.add(extractOperation(path, method.toUpperCase(), operation, sharedParams,
                        schemas, globalSecurityPresent));
            }
        }

        return new OpenApiEvidence(title, version, serverUrls, securitySchemes, operations, skipped);
    }

    private List<SecuritySchemeEvidence> extractSecuritySchemes(JsonNode root) {
        List<SecuritySchemeEvidence> result = new ArrayList<>();
        JsonNode schemes = root.path("components").path("securitySchemes");
        var names = schemes.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode scheme = schemes.path(name);
            result.add(new SecuritySchemeEvidence(
                    name,
                    scheme.path("type").asText(null),
                    scheme.path("scheme").asText(null),
                    scheme.path("in").asText(null),
                    scheme.path("name").asText(null)
            ));
        }
        return result;
    }

    private ApiOperationEvidence extractOperation(
            String path, String method, JsonNode operation, List<ApiParameterEvidence> sharedParams,
            JsonNode schemas, boolean globalSecurityPresent
    ) {
        String operationId = operation.path("operationId").asText(null);
        String summary = firstNonBlank(operation.path("summary").asText(null), operation.path("description").asText(null));

        List<String> tags = new ArrayList<>();
        for (JsonNode tag : arrayOrEmpty(operation.path("tags"))) {
            tags.add(tag.asText());
        }

        List<ApiParameterEvidence> ownParams = extractParameters(operation.path("parameters"));
        List<ApiParameterEvidence> parameters = mergeParameters(sharedParams, ownParams);

        List<String> requestBodyFields = extractRequestBodyFields(operation.path("requestBody"), schemas);

        boolean requiresAuth = operation.has("security")
                ? operation.path("security").isArray() && !operation.path("security").isEmpty()
                : globalSecurityPresent;

        boolean responseIsArray = extractResponseIsArray(operation.path("responses"), schemas);
        List<String> responseFieldPaths = extractResponseFieldPaths(operation.path("responses"), schemas);

        return new ApiOperationEvidence(path, method, operationId, summary, tags, parameters,
                requestBodyFields, requiresAuth, responseIsArray, responseFieldPaths);
    }

    private List<ApiParameterEvidence> extractParameters(JsonNode parametersNode) {
        List<ApiParameterEvidence> result = new ArrayList<>();
        for (JsonNode param : arrayOrEmpty(parametersNode)) {
            String name = param.path("name").asText(null);
            if (name == null) {
                continue;
            }
            String in = param.path("in").asText(null);
            String type = param.path("schema").path("type").asText(null);
            boolean required = param.path("required").asBoolean(false);
            result.add(new ApiParameterEvidence(name, in, type, required));
        }
        return result;
    }

    private List<ApiParameterEvidence> mergeParameters(List<ApiParameterEvidence> shared, List<ApiParameterEvidence> own) {
        Set<String> ownKeys = new LinkedHashSet<>();
        for (ApiParameterEvidence p : own) {
            ownKeys.add(p.in() + ":" + p.name());
        }
        List<ApiParameterEvidence> merged = new ArrayList<>(own);
        for (ApiParameterEvidence p : shared) {
            if (!ownKeys.contains(p.in() + ":" + p.name())) {
                merged.add(p);
            }
        }
        return merged;
    }

    // requestBody.content.{첫 content-type}.schema만 본다 — multipart/form-data 등 파일 업로드는
    // MVP 범위 밖(문서 42절 exclude 목록과 별개로, Phase A는 CRUD 폼 필드 목록만 필요하기 때문).
    private List<String> extractRequestBodyFields(JsonNode requestBody, JsonNode schemas) {
        JsonNode content = requestBody.path("content");
        if (!content.isObject() || content.isEmpty()) {
            return List.of();
        }
        JsonNode firstMediaType = content.elements().next();
        JsonNode schema = resolveSchema(firstMediaType.path("schema"), schemas);
        return schemaPropertyNames(schema, schemas);
    }

    private boolean extractResponseIsArray(JsonNode responses, JsonNode schemas) {
        var codes = responses.fieldNames();
        while (codes.hasNext()) {
            String code = codes.next();
            if (!code.startsWith("2")) {
                continue;
            }
            JsonNode content = responses.path(code).path("content");
            if (!content.isObject() || content.isEmpty()) {
                continue;
            }
            JsonNode schema = resolveSchema(content.elements().next().path("schema"), schemas);
            if (looksLikeArraySchema(schema, schemas)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeArraySchema(JsonNode schema, JsonNode schemas) {
        return looksLikeArraySchema(schema, schemas, 0);
    }

    // API마다 목록 응답 봉투 설계가 제각각임(순수 배열 / {data:[...]} / {success,data:{content:[...],
    // totalElements}}처럼 겹겹이 감싸는 Spring Data Page + 공통 응답 포맷 조합 등). 이름이 알려진 키를
    // 먼저 확인해 오탐 가능성을 줄이되, 모르는 이름이어도 배열을 담은 속성이 있으면 목록으로 취급하도록
    // 구조 기반으로 재귀 탐색한다.
    private boolean looksLikeArraySchema(JsonNode schema, JsonNode schemas, int depth) {
        if (depth > MAX_ENVELOPE_UNWRAP_DEPTH) {
            return false;
        }
        JsonNode resolved = resolveSchema(schema, schemas);
        if (resolved.isMissingNode()) {
            return false;
        }
        if ("array".equals(resolved.path("type").asText(null))) {
            return true;
        }

        JsonNode properties = mergedProperties(resolved, schemas);
        if (!properties.isObject() || properties.isEmpty()) {
            return false;
        }

        for (String key : ARRAY_ENVELOPE_KEYS) {
            if (properties.has(key) && looksLikeArraySchema(properties.path(key), schemas, depth + 1)) {
                return true;
            }
        }
        var names = properties.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (ARRAY_ENVELOPE_KEYS.contains(name)) {
                continue;
            }
            if (looksLikeArraySchema(properties.path(name), schemas, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    // 성공 응답 스키마에서 스칼라(문자열/숫자/불리언) leaf 필드의 dot-path를 모두 모은다(예: "data.accessToken").
    // 로그인 응답에서 access token이 어디 있는지 CapabilityExtractor가 이름 힌트로 찾을 때 필요 — looksLikeArraySchema와
    // 반대로 배열 내부는 절대 들어가지 않는다(토큰은 배열 안에 있지 않음).
    private List<String> extractResponseFieldPaths(JsonNode responses, JsonNode schemas) {
        var codes = responses.fieldNames();
        while (codes.hasNext()) {
            String code = codes.next();
            if (!code.startsWith("2")) {
                continue;
            }
            JsonNode content = responses.path(code).path("content");
            if (!content.isObject() || content.isEmpty()) {
                continue;
            }
            JsonNode schema = resolveSchema(content.elements().next().path("schema"), schemas);
            List<String> paths = new ArrayList<>();
            collectScalarFieldPaths(schema, schemas, "", 0, paths);
            if (!paths.isEmpty()) {
                return paths;
            }
        }
        return List.of();
    }

    private void collectScalarFieldPaths(JsonNode schema, JsonNode schemas, String prefix, int depth, List<String> out) {
        if (depth > MAX_ENVELOPE_UNWRAP_DEPTH || out.size() >= MAX_RESPONSE_FIELD_PATHS) {
            return;
        }
        JsonNode resolved = resolveSchema(schema, schemas);
        if (resolved.isMissingNode() || "array".equals(resolved.path("type").asText(null))) {
            return;
        }
        JsonNode properties = mergedProperties(resolved, schemas);
        if (!properties.isObject() || properties.isEmpty()) {
            return;
        }
        var names = properties.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            String path = prefix.isEmpty() ? name : prefix + "." + name;
            JsonNode propertySchema = resolveSchema(properties.path(name), schemas);
            String propertyType = propertySchema.path("type").asText(null);
            if ("array".equals(propertyType)) {
                // 배열은 대상 아님 — 건너뜀
            } else if ("object".equals(propertyType) || propertySchema.has("properties") || propertySchema.has("allOf")) {
                collectScalarFieldPaths(propertySchema, schemas, path, depth + 1, out);
            } else {
                out.add(path);
            }
            if (out.size() >= MAX_RESPONSE_FIELD_PATHS) {
                return;
            }
        }
    }

    // properties 직접 선언 + allOf로 합성된 속성(재귀적으로)까지 모두 합친다.
    private JsonNode mergedProperties(JsonNode schema, JsonNode schemas) {
        ObjectNode merged = objectMapper.createObjectNode();
        copyProperties(schema.path("properties"), merged);
        for (JsonNode part : arrayOrEmpty(schema.path("allOf"))) {
            copyProperties(mergedProperties(resolveSchema(part, schemas), schemas), merged);
        }
        return merged;
    }

    private void copyProperties(JsonNode source, ObjectNode target) {
        if (!source.isObject()) {
            return;
        }
        var fields = source.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            target.set(entry.getKey(), entry.getValue());
        }
    }

    // #/components/schemas/{Name} 형태의 로컬 $ref만 한 단계 해석. 그 외(외부 URL 등)는 원본을 그대로 반환.
    private JsonNode resolveSchema(JsonNode schema, JsonNode schemas) {
        String ref = schema.path("$ref").asText(null);
        if (ref == null) {
            return schema;
        }
        String prefix = "#/components/schemas/";
        if (!ref.startsWith(prefix)) {
            return schema;
        }
        String schemaName = ref.substring(prefix.length());
        JsonNode resolved = schemas.path(schemaName);
        return resolved.isMissingNode() ? schema : resolved;
    }

    // properties 직접 선언 + allOf 안의 properties(한 단계)까지만 합쳐서 필드명만 뽑는다.
    private List<String> schemaPropertyNames(JsonNode schema, JsonNode schemas) {
        List<String> fields = new ArrayList<>();
        JsonNode properties = schema.path("properties");
        properties.fieldNames().forEachRemaining(fields::add);

        for (JsonNode part : arrayOrEmpty(schema.path("allOf"))) {
            JsonNode resolvedPart = resolveSchema(part, schemas);
            resolvedPart.path("properties").fieldNames().forEachRemaining(fields::add);
        }
        return fields;
    }

    private Iterable<JsonNode> arrayOrEmpty(JsonNode node) {
        return node.isArray() ? node : new ArrayNode(objectMapper.getNodeFactory());
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
