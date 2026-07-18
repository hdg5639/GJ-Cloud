package gj.cloud.ops.application.filebrowser.dto;

import java.time.LocalDateTime;

public record FileEntry(
        String name,
        String path,
        boolean directory,
        long size,
        LocalDateTime modifiedAt
) {
}
