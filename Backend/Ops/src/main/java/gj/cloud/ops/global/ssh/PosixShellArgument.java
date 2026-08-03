package gj.cloud.ops.global.ssh;

// SSH exec는 POSIX 셸 문자열을 전송하므로 동적 인자는 반드시 하나의 공통 규칙으로 quote한다.
// 시크릿 값은 이 유틸로 quote해도 명령줄에 넣지 않고 SFTP/임시 credential 파일로 전달한다.
public final class PosixShellArgument {

    private PosixShellArgument() {
    }

    public static String quote(String value) {
        if (value == null || value.codePoints().anyMatch(PosixShellArgument::isControlCharacter)) {
            throw new IllegalArgumentException("Shell argument contains a control character");
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static boolean isControlCharacter(int codePoint) {
        return Character.isISOControl(codePoint) || codePoint == 0x2028 || codePoint == 0x2029;
    }
}
