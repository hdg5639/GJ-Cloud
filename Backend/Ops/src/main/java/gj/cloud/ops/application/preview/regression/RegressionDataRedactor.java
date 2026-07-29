package gj.cloud.ops.application.preview.regression;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RegressionDataRedactor {

    public Object redact(Object value) {
        return redact(value, "", 0);
    }

    private Object redact(Object value, String key, int depth) {
        if (depth > 20) return "[Depth limit]";
        if (sensitive(key) && value != null && !String.valueOf(value).isBlank()) return "••••••";
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((childKey, childValue) -> {
                String name = String.valueOf(childKey);
                result.put(name, redact(childValue, name, depth + 1));
            });
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(item -> result.add(redact(item, key, depth + 1)));
            return result;
        }
        return value;
    }

    private boolean sensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("authorization")
                || normalized.contains("cookie")
                || normalized.contains("credential")
                || normalized.contains("apikey")
                || normalized.contains("privatekey")
                || normalized.equals("pw")
                || normalized.equals("pwd");
    }
}
