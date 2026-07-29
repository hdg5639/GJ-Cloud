package gj.cloud.ops.application.preview.blueprint.search;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BlueprintIndexStartup implements ApplicationRunner {

    private final BlueprintSearchProperties properties;
    private final BlueprintSearchEngine searchEngine;

    public BlueprintIndexStartup(
            BlueprintSearchProperties properties,
            BlueprintSearchEngine searchEngine
    ) {
        this.properties = properties;
        this.searchEngine = searchEngine;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.enabled() && properties.reindexOnStartup()) {
            searchEngine.reindex();
        }
    }
}
