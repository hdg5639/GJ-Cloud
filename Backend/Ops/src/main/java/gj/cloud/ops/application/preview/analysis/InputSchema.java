package gj.cloud.ops.application.preview.analysis;

import java.util.List;
import java.util.Map;

/** Bounded, local-ref-only input contract. No defaults or executable model content. */
public record InputSchema(String type, String title, String description, String format,
        List<String> required, Map<String, InputSchema> properties, InputSchema items,
        List<String> enumValues, Double minimum, Double maximum) {
    public InputSchema {
        required = required == null ? List.of() : List.copyOf(required);
        properties = properties == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(properties));
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }
}
