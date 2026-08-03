package gj.cloud.ops.global.ssh;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PosixShellArgumentTest {

    @Test
    void quotesSingleQuoteWithoutEndingArgument() {
        assertThat(PosixShellArgument.quote("value'with quote"))
                .isEqualTo("'value'\"'\"'with quote'");
    }

    @Test
    void rejectsControlCharacters() {
        assertThatThrownBy(() -> PosixShellArgument.quote("first\nsecond"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
