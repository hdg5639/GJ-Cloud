package gj.cloud.user.global.storage;

import gj.cloud.user.global.exception.UserException;
import gj.cloud.user.global.exception.enums.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocsImageStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesPngUsingDetectedSignatureInsteadOfClientContentType() {
        DocsImageStorage storage = new DocsImageStorage(tempDir.toString(), "https://portal.example/users/docs/images", 1024);
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

        var result = storage.store(new MockMultipartFile("file", "fake.svg", "image/svg+xml", png));

        assertThat(result.filename()).endsWith(".png");
        assertThat(result.url()).endsWith(result.filename());
        assertThat(storage.loadAsResource(result.filename()).exists()).isTrue();
    }

    @Test
    void rejectsHtmlEvenWhenClientClaimsItIsAnImage() {
        DocsImageStorage storage = new DocsImageStorage(tempDir.toString(), "https://portal.example/users/docs/images", 1024);
        var file = new MockMultipartFile("file", "attack.png", "image/png", "<script>alert(1)</script>".getBytes());

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(UserException.class)
                .extracting(cause -> ((UserException) cause).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_DOCS_IMAGE_FORMAT);
    }
}
