package gj.cloud.vm.global.cache;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MetricsCache {
    private static final long TTL_MILLIS = 5000; // 5초

    @Getter
    private static class CacheEntry {
        private final JsonNode data;
        private final long expiresAt;

        CacheEntry(JsonNode data) {
            this.data = data;
            this.expiresAt = System.currentTimeMillis() + TTL_MILLIS;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final Map<String, CacheEntry> currentMetricsCache = new ConcurrentHashMap<>();

    public JsonNode get(String key) {
        CacheEntry entry = currentMetricsCache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.getData();
        }
        currentMetricsCache.remove(key);
        return null;
    }

    public void put(String key, JsonNode data) {
        currentMetricsCache.put(key, new CacheEntry(data));
    }

    public void invalidate(String key) {
        currentMetricsCache.remove(key);
    }

    public void clear() {
        currentMetricsCache.clear();
    }
}
