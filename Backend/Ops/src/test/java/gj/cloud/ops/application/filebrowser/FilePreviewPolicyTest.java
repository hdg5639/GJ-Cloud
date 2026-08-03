package gj.cloud.ops.application.filebrowser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilePreviewPolicyTest {

    @Test
    void allowsRasterImageAndMedia() {
        assertThat(FilePreviewPolicy.isPreviewable("/home/ubuntu/photo.webp")).isTrue();
        assertThat(FilePreviewPolicy.isPreviewable("/home/ubuntu/movie.MP4")).isTrue();
    }

    @Test
    void rejectsSvgAndActiveDocuments() {
        assertThat(FilePreviewPolicy.isPreviewable("/home/ubuntu/payload.svg")).isFalse();
        assertThat(FilePreviewPolicy.isPreviewable("/home/ubuntu/page.html")).isFalse();
        assertThat(FilePreviewPolicy.isPreviewable("/home/ubuntu/data.xml")).isFalse();
    }
}
