package gj.cloud.ops.application.preview.blueprint.search;

import gj.cloud.ops.application.preview.blueprint.search.BlueprintCompatibilityFilter.FilterResult;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintCandidate;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintMetadata;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintReindexResult;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintSearchDiagnostics;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintSearchQuery;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BlueprintRetrievalService implements BlueprintSearchEngine {

    private final BlueprintSearchProperties properties;
    private final ElasticsearchBlueprintIndex elasticsearch;

    public BlueprintRetrievalService(
            BlueprintSearchProperties properties,
            ElasticsearchBlueprintIndex elasticsearch
    ) {
        this.properties = properties;
        this.elasticsearch = elasticsearch;
    }

    @Override
    public BlueprintSearchResult search(BlueprintSearchQuery query) {
        long startedAt = System.nanoTime();
        List<BlueprintMetadata> registry = BlueprintRegistryIndexProjection.projectAll();
        FilterResult filtered = BlueprintCompatibilityFilter.filter(registry, query);
        Map<String, BlueprintMetadata> compatibleById = filtered.compatible().stream()
                .collect(Collectors.toMap(BlueprintMetadata::blueprintId, value -> value,
                        (left, right) -> left, LinkedHashMap::new));

        boolean fallback = !properties.enabled();
        String message = properties.enabled() ? "Elasticsearch 검색 완료" : "Registry fallback 검색";
        List<BlueprintMetadata> retrieved = filtered.compatible();
        Map<String, Double> retrievalScores = new LinkedHashMap<>();
        if (properties.enabled() && !compatibleById.isEmpty()) {
            try {
                List<ElasticsearchBlueprintIndex.SearchHit> hits = elasticsearch.search(
                        query.text(), List.copyOf(compatibleById.keySet()), query.limit() * 3);
                List<BlueprintMetadata> indexed = hits.stream()
                        .map(hit -> compatibleById.get(hit.blueprintId()))
                        .filter(java.util.Objects::nonNull)
                        .toList();
                if (!indexed.isEmpty()) {
                    retrieved = indexed;
                    hits.forEach(hit -> retrievalScores.put(hit.blueprintId(), hit.score()));
                } else {
                    fallback = true;
                    message = "Elasticsearch 결과가 비어 Registry 후보로 대체";
                }
            } catch (RuntimeException e) {
                fallback = true;
                message = "Elasticsearch 검색 실패로 Registry 후보 사용: " + concise(e);
                log.warn("Blueprint Elasticsearch 검색 실패, registry fallback: {}", e.getMessage());
            }
        }
        List<BlueprintCandidate> ranked = BlueprintCandidateRanker.rank(retrieved, query, retrievalScores);
        long tookMs = (System.nanoTime() - startedAt) / 1_000_000;
        BlueprintSearchDiagnostics diagnostics = new BlueprintSearchDiagnostics(
                properties.enabled() && !fallback ? "elasticsearch" : "registry",
                properties.index(),
                registry.size(),
                filtered.compatible().size(),
                retrieved.size(),
                ranked.size(),
                fallback,
                tookMs,
                filtered.rejectionCounts(),
                message,
                BlueprintSearchModels.METADATA_VERSION,
                BlueprintSearchModels.SELECTION_POLICY_VERSION
        );
        log.info("EVENT blueprint.search engine={} compatible={} retrieved={} returned={} fallback={} tookMs={}",
                diagnostics.engine(), diagnostics.hardCompatibleCount(), diagnostics.retrievedCount(),
                diagnostics.returnedCount(), diagnostics.fallbackUsed(), diagnostics.tookMs());
        return new BlueprintSearchResult(ranked, diagnostics);
    }

    @Override
    public BlueprintReindexResult reindex() {
        long startedAt = System.nanoTime();
        if (!properties.enabled()) {
            return new BlueprintReindexResult(properties.index(), 0, false, 0,
                    "Elasticsearch 검색이 비활성화되어 있습니다.");
        }
        try {
            List<BlueprintMetadata> documents = BlueprintRegistryIndexProjection.projectAll();
            int count = elasticsearch.rebuild(documents);
            long tookMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("EVENT blueprint.reindex.succeeded index={} count={} tookMs={}",
                    properties.index(), count, tookMs);
            return new BlueprintReindexResult(properties.index(), count, true, tookMs, "재색인 완료");
        } catch (RuntimeException e) {
            long tookMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.error("EVENT blueprint.reindex.failed index={} tookMs={} error={}",
                    properties.index(), tookMs, e.getMessage());
            return new BlueprintReindexResult(properties.index(), 0, false, tookMs, concise(e));
        }
    }

    public List<BlueprintMetadata> registryDocuments() {
        return BlueprintRegistryIndexProjection.projectAll();
    }

    private static String concise(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
