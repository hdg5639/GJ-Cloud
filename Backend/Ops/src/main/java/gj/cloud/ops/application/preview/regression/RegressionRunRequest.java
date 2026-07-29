package gj.cloud.ops.application.preview.regression;

import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public record RegressionRunRequest(
        Map<String, Object> initialState,
        @Size(max = 20) Map<String, String> headers,
        boolean allowStateChanging,
        boolean failFast
) {
    public RegressionRunRequest {
        initialState = initialState == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(initialState));
        headers = headers == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    public static RegressionRunRequest automated(boolean allowStateChanging) {
        return new RegressionRunRequest(Map.of(), Map.of(), allowStateChanging, true);
    }
}
