package gj.cloud.ops.application.preview.blueprint.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchBlueprintIndexContextTest {

    @Test
    void springUsesTheExplicitInjectionConstructorWhenTheTestConstructorAlsoExists() {
        BlueprintSearchProperties properties = new BlueprintSearchProperties(
                false,
                "http://localhost:9200",
                "gamjabox-blueprints-context-test",
                "",
                "",
                false
        );
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(BlueprintSearchProperties.class, () -> properties);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(ElasticsearchBlueprintIndex.class);

            context.refresh();

            assertThat(context.getBean(ElasticsearchBlueprintIndex.class).indexName())
                    .isEqualTo("gamjabox-blueprints-context-test");
        }
    }
}
