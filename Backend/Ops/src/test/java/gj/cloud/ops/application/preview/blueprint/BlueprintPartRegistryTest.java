package gj.cloud.ops.application.preview.blueprint;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintPartRegistryTest {

    @Test
    void loadsEveryBlueprintPartFromCanonicalManifest() {
        assertThat(BlueprintPartRegistry.ALL)
                .hasSize(281)
                .extracting(BlueprintPartRegistry.BlueprintPart::componentId)
                .doesNotHaveDuplicates();

        Map<String, Long> countByImplementationKind = BlueprintPartRegistry.ALL.stream()
                .collect(Collectors.groupingBy(
                        BlueprintPartRegistry.BlueprintPart::implementationKind,
                        Collectors.counting()
                ));

        assertThat(countByImplementationKind).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("ACTION", 16L),
                Map.entry("COLLECTION", 38L),
                Map.entry("DASHBOARD", 36L),
                Map.entry("DETAIL", 32L),
                Map.entry("FEEDBACK", 14L),
                Map.entry("FORM", 18L),
                Map.entry("LAYOUT", 28L),
                Map.entry("MODAL", 41L),
                Map.entry("NAVIGATION", 14L),
                Map.entry("THEME", 16L),
                Map.entry("WORKFLOW", 28L)
        ));
    }
}
