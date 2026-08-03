package gj.cloud.ops.global.io;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedOutputStreamTest {

    @Test
    void capturesOnlyConfiguredPrefixAndMarksTruncation() {
        BoundedOutputStream output = new BoundedOutputStream(4);
        byte[] bytes = "abcdefgh".getBytes(StandardCharsets.UTF_8);

        output.write(bytes, 0, bytes.length);

        assertThat(output.toString(StandardCharsets.UTF_8))
                .startsWith("abcd")
                .contains("4 bytes discarded");
    }
}
