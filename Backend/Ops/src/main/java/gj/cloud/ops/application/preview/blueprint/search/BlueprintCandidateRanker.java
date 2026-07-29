package gj.cloud.ops.application.preview.blueprint.search;

import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintCandidate;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintMetadata;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintSearchQuery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BlueprintCandidateRanker {

    public static List<BlueprintCandidate> rank(
            List<BlueprintMetadata> candidates,
            BlueprintSearchQuery query,
            java.util.Map<String, Double> retrievalScores
    ) {
        Set<String> queryTokens = tokens(query.text());
        return candidates.stream()
                .map(document -> score(document, query, queryTokens,
                        retrievalScores.getOrDefault(document.blueprintId(), 0.0)))
                .sorted(java.util.Comparator.comparingDouble(BlueprintCandidate::rankScore).reversed()
                        .thenComparing(candidate -> candidate.metadata().blueprintId()))
                .limit(query.limit())
                .toList();
    }

    private static BlueprintCandidate score(
            BlueprintMetadata document,
            BlueprintSearchQuery query,
            Set<String> queryTokens,
            double retrievalScore
    ) {
        List<String> signals = new ArrayList<>();
        Set<String> documentTokens = tokens(String.join(" ", document.blueprintId(), document.label(),
                document.family(), document.category().name(),
                String.join(" ", document.contentTags()),
                String.join(" ", document.interactionTags()),
                String.join(" ", document.presentationTags())));
        long overlap = queryTokens.stream().filter(documentTokens::contains).count();
        double score = retrievalScore * 0.20 + overlap * 0.12;
        if (overlap > 0) signals.add("tag_similarity:" + overlap);
        if (query.stageRole() != null && document.supportedStages().contains(query.stageRole())) {
            score += 0.18;
            signals.add("stage");
        }
        if (query.category() != null && document.category() == query.category()) {
            score += 0.22;
            signals.add("category");
        }
        if (query.purpose() != null && document.preferredPurposes().contains(query.purpose())) {
            score += 0.08;
            signals.add("purpose");
        }
        score += document.qualityScore() * 0.12;
        score += document.stabilityScore() * 0.10;
        score -= complexityPenalty(document);
        return new BlueprintCandidate(document, retrievalScore, score, signals);
    }

    private static double complexityPenalty(BlueprintMetadata document) {
        if ("WORKFLOW".equals(document.implementationKind())) return 0.03;
        if ("DASHBOARD".equals(document.implementationKind())) return 0.02;
        return 0;
    }

    private static Set<String> tokens(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^a-z0-9가-힣]+"))
                .filter(token -> token.length() > 1)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private BlueprintCandidateRanker() {
    }
}
