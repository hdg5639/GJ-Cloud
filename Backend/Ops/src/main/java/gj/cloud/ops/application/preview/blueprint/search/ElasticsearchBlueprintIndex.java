package gj.cloud.ops.application.preview.blueprint.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.net.http.HttpClient;
import java.time.Duration;

@Component
@Slf4j
public class ElasticsearchBlueprintIndex {

    private static final Pattern SAFE_INDEX = Pattern.compile("[a-z0-9][a-z0-9._-]{1,126}");

    public record SearchHit(String blueprintId, double score) {
    }

    private final BlueprintSearchProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient client;

    public ElasticsearchBlueprintIndex(BlueprintSearchProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, createClient(properties));
    }

    ElasticsearchBlueprintIndex(
            BlueprintSearchProperties properties,
            ObjectMapper objectMapper,
            RestClient client
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = client;
        requireSafeIndex(properties.index());
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public String indexName() {
        return properties.index();
    }

    /**
     * 정본 Registry 전체로 파생 인덱스를 원자적 의미에서 재생성한다. 대상은 검증된 전용 index 이름 하나뿐이다.
     */
    public int rebuild(List<BlueprintMetadata> documents) {
        if (!enabled()) throw new IllegalStateException("Blueprint Elasticsearch 검색이 비활성화되어 있습니다.");
        deleteIndexIfExists();
        client.put()
                .uri("/{index}", properties.index())
                .contentType(MediaType.APPLICATION_JSON)
                .body(indexDefinition())
                .retrieve()
                .toBodilessEntity();
        if (documents.isEmpty()) return 0;
        StringBuilder ndjson = new StringBuilder();
        try {
            for (BlueprintMetadata document : documents) {
                ndjson.append(objectMapper.writeValueAsString(Map.of(
                        "index", Map.of("_index", properties.index(), "_id", document.blueprintId())
                ))).append('\n');
                ndjson.append(objectMapper.writeValueAsString(document)).append('\n');
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Blueprint 검색 문서를 직렬화하지 못했습니다.", e);
        }
        String responseBody = client.post()
                .uri("/_bulk?refresh=wait_for")
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .body(ndjson.toString())
                .retrieve()
                .body(String.class);
        JsonNode response = readTree(responseBody);
        if (response != null && response.path("errors").asBoolean(false)) {
            throw new IllegalStateException("Elasticsearch bulk 색인 일부가 실패했습니다: "
                    + firstBulkError(response));
        }
        return documents.size();
    }

    public List<SearchHit> search(String text, List<String> allowedBlueprintIds, int limit) {
        if (!enabled() || allowedBlueprintIds.isEmpty()) return List.of();
        List<Map<String, Object>> must = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            must.add(Map.of("multi_match", Map.of(
                    "query", text,
                    "fields", List.of(
                            "label^4", "blueprintId^3", "family^2", "contentTags^3",
                            "interactionTags^2", "presentationTags^2", "category^2"
                    ),
                    "type", "best_fields"
            )));
        } else {
            must.add(Map.of("match_all", Map.of()));
        }
        Map<String, Object> body = Map.of(
                "size", Math.max(limit, Math.min(allowedBlueprintIds.size(), 50)),
                "track_total_hits", false,
                "query", Map.of("bool", Map.of(
                        "must", must,
                        "filter", List.of(Map.of("terms", Map.of("blueprintId", allowedBlueprintIds)))
                ))
        );
        String responseBody = client.post()
                .uri("/{index}/_search", properties.index())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        JsonNode response = readTree(responseBody);
        if (response == null) return List.of();
        List<SearchHit> result = new ArrayList<>();
        for (JsonNode hit : response.path("hits").path("hits")) {
            String id = hit.path("_source").path("blueprintId").asText(hit.path("_id").asText());
            result.add(new SearchHit(id, hit.path("_score").asDouble(0)));
        }
        return List.copyOf(result);
    }

    private void deleteIndexIfExists() {
        try {
            client.delete().uri("/{index}", properties.index()).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 404) throw e;
        }
    }

    private Map<String, Object> indexDefinition() {
        Map<String, Object> keyword = Map.of("type", "keyword");
        Map<String, Object> propertiesMapping = new LinkedHashMap<>();
        propertiesMapping.put("blueprintId", keyword);
        propertiesMapping.put("version", keyword);
        propertiesMapping.put("level", keyword);
        propertiesMapping.put("status", keyword);
        propertiesMapping.put("supportedStages", keyword);
        propertiesMapping.put("purposeTags", keyword);
        propertiesMapping.put("contentTags", keyword);
        propertiesMapping.put("interactionTags", keyword);
        propertiesMapping.put("presentationTags", keyword);
        propertiesMapping.put("requiredCapabilities", keyword);
        propertiesMapping.put("requiredDataShapes", keyword);
        propertiesMapping.put("runtimeVersion", keyword);
        propertiesMapping.put("qualityScore", Map.of("type", "float"));
        propertiesMapping.put("stabilityScore", Map.of("type", "float"));
        propertiesMapping.put("mountPoint", keyword);
        propertiesMapping.put("implementationKind", keyword);
        propertiesMapping.put("category", keyword);
        propertiesMapping.put("acceptedSurfaces", keyword);
        propertiesMapping.put("preferredPurposes", keyword);
        propertiesMapping.put("supportedModes", keyword);
        propertiesMapping.put("autoSelectable", Map.of("type", "boolean"));
        propertiesMapping.put("deprecated", Map.of("type", "boolean"));
        propertiesMapping.put("label", Map.of(
                "type", "text", "fields", Map.of("keyword", keyword)
        ));
        propertiesMapping.put("family", keyword);
        return Map.of(
                "settings", Map.of("number_of_shards", 1, "number_of_replicas", 0),
                "mappings", Map.of("dynamic", "strict", "properties", propertiesMapping)
        );
    }

    private String firstBulkError(JsonNode response) {
        for (JsonNode item : response.path("items")) {
            JsonNode error = item.path("index").path("error");
            if (!error.isMissingNode()) return error.path("reason").asText(error.toString());
        }
        return "원인 불명";
    }

    private JsonNode readTree(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return objectMapper.readTree(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Elasticsearch 응답 JSON을 해석하지 못했습니다.", e);
        }
    }

    private static RestClient createClient(BlueprintSearchProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(4));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.url())
                .requestFactory(requestFactory);
        if (!properties.username().isBlank()) {
            builder.defaultHeaders(headers -> headers.setBasicAuth(properties.username(), properties.password()));
        }
        return builder.build();
    }

    private static void requireSafeIndex(String index) {
        if (index == null || !SAFE_INDEX.matcher(index).matches()) {
            throw new IllegalArgumentException("안전하지 않은 Elasticsearch index 이름입니다: " + index);
        }
    }
}
