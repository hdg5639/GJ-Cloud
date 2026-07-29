package gj.cloud.ops.application.preview.blueprint.composition;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class BlueprintUsageTracker {

    private final Map<String, LongAdder> recommendationCounts = new ConcurrentHashMap<>();

    public long frequency(String componentId) {
        LongAdder value = recommendationCounts.get(componentId);
        return value == null ? 0 : value.sum();
    }

    public void record(String componentId) {
        if (componentId == null || componentId.isBlank()) return;
        recommendationCounts.computeIfAbsent(componentId, ignored -> new LongAdder()).increment();
    }

    Map<String, Long> snapshot() {
        return recommendationCounts.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue().sum()
        ));
    }
}
