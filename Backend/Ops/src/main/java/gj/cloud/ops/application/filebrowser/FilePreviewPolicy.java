package gj.cloud.ops.application.filebrowser;

import java.util.Map;
import java.util.Locale;

// 사용자 파일을 Ops API origin에서 inline 스트리밍해도 안전한 래스터 이미지·미디어만 허용한다.
// SVG/HTML/XML은 문서로 직접 열릴 경우 active content가 될 수 있으므로 미리보기 티켓 발급 대상에서 제외한다.
final class FilePreviewPolicy {

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"), Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"), Map.entry("bmp", "image/bmp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"),
            Map.entry("ogg", "audio/ogg"), Map.entry("m4a", "audio/mp4"),
            Map.entry("flac", "audio/flac"), Map.entry("aac", "audio/aac"),
            Map.entry("mp4", "video/mp4"), Map.entry("webm", "video/webm"),
            Map.entry("mov", "video/quicktime"), Map.entry("mkv", "video/x-matroska"),
            Map.entry("avi", "video/x-msvideo")
    );

    private FilePreviewPolicy() {
    }

    static boolean isPreviewable(String path) {
        return CONTENT_TYPES.containsKey(extension(path));
    }

    static String contentType(String path) {
        return CONTENT_TYPES.getOrDefault(extension(path), "application/octet-stream");
    }

    private static String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
