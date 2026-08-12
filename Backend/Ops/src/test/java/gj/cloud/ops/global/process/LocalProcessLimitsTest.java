package gj.cloud.ops.global.process;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalProcessLimitsTest {

    @Test
    void addsCloneHeadroomToAlreadyUsedContainerTasks() {
        assertThat(LocalProcessLimits.addHeadroom(52, 64)).isEqualTo(116);
    }

    @Test
    void normalizesInvalidValuesAndSaturatesOverflow() {
        assertThat(LocalProcessLimits.addHeadroom(0, 0)).isEqualTo(2);
        assertThat(LocalProcessLimits.addHeadroom(Long.MAX_VALUE - 10, 64))
                .isEqualTo(Long.MAX_VALUE);
    }
}
