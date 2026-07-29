package gj.cloud.ops.application.preview.blueprint.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ops.blueprint-search.elasticsearch")
public record BlueprintSearchProperties(
        boolean enabled,
        String url,
        String index,
        String username,
        String password,
        boolean reindexOnStartup
) {
    public BlueprintSearchProperties {
        url = blank(url) ? "http://localhost:9200" : url;
        index = blank(index) ? "gamjabox-blueprints-v1" : index;
        username = username == null ? "" : username;
        password = password == null ? "" : password;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
