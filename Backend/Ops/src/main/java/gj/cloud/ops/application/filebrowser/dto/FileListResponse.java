package gj.cloud.ops.application.filebrowser.dto;

import java.util.List;

public record FileListResponse(String path, List<FileEntry> entries) {
}
