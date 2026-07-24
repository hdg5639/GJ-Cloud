package gj.cloud.ops.application.filebrowser;

import java.util.Set;
import java.util.regex.Pattern;

// AUTHZ-001: 파일 브라우저의 루트(/home/{vmSshUsername})는 SftpPathResolver가 이미 강제하고 있지만,
// 그 안에는 배포 파이프라인이 써놓은 .env(실제 시크릿)와 SSH/Git 자격증명 디렉토리가 함께 있다.
// FILE_READ만으로 파일 "내용"까지 읽을 수 있게 두면 MEMBER가 다른 사람이 배포한 앱의 시크릿을
// 그대로 읽을 수 있으므로, 이런 경로는 SECRET_READ가 추가로 필요하도록 표시만 담당한다
// (목록 조회는 대상이 아님 — 파일명 존재 자체는 낮은 민감도로 취급).
public final class SensitiveFilePolicy {

    private static final Pattern ENV_FILE = Pattern.compile("(^|/)\\.env(\\..*)?$");
    private static final Pattern DEPLOYED_APPS_DIR = Pattern.compile("(^|/)gamjabox/apps(/|$)");
    private static final Set<String> SENSITIVE_DOT_DIRS = Set.of(".ssh", ".aws", ".gnupg", ".docker");
    private static final Set<String> SENSITIVE_FILENAMES = Set.of(
            ".git-credentials", ".netrc", ".pgpass", "id_rsa", "id_ed25519", "id_ecdsa"
    );

    private SensitiveFilePolicy() {
    }

    public static boolean isSensitive(String resolvedPath) {
        if (ENV_FILE.matcher(resolvedPath).find() || DEPLOYED_APPS_DIR.matcher(resolvedPath).find()) {
            return true;
        }
        String[] segments = resolvedPath.split("/");
        for (String segment : segments) {
            if (SENSITIVE_DOT_DIRS.contains(segment) || SENSITIVE_FILENAMES.contains(segment)) {
                return true;
            }
        }
        return false;
    }
}
