package gj.cloud.ops.application.deployment.dto;

import gj.cloud.ops.global.ssh.CommandResult;

import java.util.regex.Pattern;

/**
 * 배포 이벤트에 저장하는 원격 명령 결과. 실제 명령 문자열은 비밀값 포함 여부를 판별할 수 없으므로
 * 기록하지 않고, 호출부가 정한 안전한 작업명과 대상만 남긴다.
 */
public record DeploymentCommandLogPayload(
        String operation,
        String subject,
        int exitStatus,
        long durationMs,
        String stdout,
        String stderr,
        boolean stdoutTruncated,
        boolean stderrTruncated
) {
    static final int MAX_OUTPUT_CHARS = 20_000;
    private static final int FAILURE_SUMMARY_CHARS = 2_000;
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\u001B\\[[;?0-9]*[ -/]*[@-~]");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(authorization\\s*:\\s*(?:basic|bearer)\\s+)[^\\s]+");
    private static final Pattern GITHUB_TOKEN = Pattern.compile("\\bgh[pousr]_[A-Za-z0-9_]{16,}\\b");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?im)^([ \\t]*(?:export[ \\t]+)?[\\\"']?[A-Za-z0-9_.-]*"
                    + "(?:password|passwd|token|secret|api[_-]?key|client[_-]?secret)"
                    + "[A-Za-z0-9_.-]*[\\\"']?[ \\t]*[=:][ \\t]*)[^\\r\\n]+$");

    public static DeploymentCommandLogPayload from(
            String operation,
            String subject,
            CommandResult result,
            long durationMs
    ) {
        String cleanStdout = sanitize(result.stdout());
        String cleanStderr = sanitize(result.stderr());
        return new DeploymentCommandLogPayload(
                operation,
                subject,
                result.exitStatus(),
                Math.max(0, durationMs),
                tail(cleanStdout, MAX_OUTPUT_CHARS),
                tail(cleanStderr, MAX_OUTPUT_CHARS),
                cleanStdout.length() > MAX_OUTPUT_CHARS,
                cleanStderr.length() > MAX_OUTPUT_CHARS);
    }

    public static String failureSummary(CommandResult result) {
        String detail = sanitize(result.stderr());
        if (detail.isBlank()) {
            detail = sanitize(result.stdout());
        }
        if (detail.isBlank()) {
            return "출력 없음";
        }
        return tail(detail, FAILURE_SUMMARY_CHARS).trim();
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String clean = ANSI_ESCAPE.matcher(value).replaceAll("");
        clean = AUTHORIZATION.matcher(clean).replaceAll("$1[REDACTED]");
        clean = GITHUB_TOKEN.matcher(clean).replaceAll("[REDACTED_GITHUB_TOKEN]");
        clean = SECRET_ASSIGNMENT.matcher(clean).replaceAll("$1[REDACTED]");
        return clean.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
    }

    private static String tail(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(value.length() - maxChars);
    }
}
