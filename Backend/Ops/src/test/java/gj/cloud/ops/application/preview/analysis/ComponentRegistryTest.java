package gj.cloud.ops.application.preview.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentRegistryTest {

    @Test
    void everySixFixedComponentsAreRegisteredAsSystemOfficial() {
        assertThat(ComponentRegistry.ALL.keySet()).containsExactlyInAnyOrderElementsOf(ComponentContracts.ALL.keySet());

        ComponentRegistry.ALL.values().forEach(entry -> {
            assertThat(entry.status()).isEqualTo(RegistryStatus.OFFICIAL);
            assertThat(entry.scope()).isEqualTo(RegistryScope.SYSTEM);
        });
    }

    @Test
    void entryWrapsTheMatchingContract() {
        ComponentRegistryEntry entry = ComponentRegistry.ALL.get("resource-table");

        assertThat(entry.contract()).isEqualTo(ComponentContracts.ALL.get("resource-table"));
    }
}
