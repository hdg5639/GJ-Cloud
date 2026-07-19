package gj.cloud.ops.global.process;

import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// Ops 컨테이너 자신의 프로세스로 로컬 명령을 실행 (SSH로 원격 VM에 실행하는 SshCommandExecutor와는 별개 — 배포
// 스펙 생성 전 저장소를 분석할 때 사용, 아직 VM SSH 세션이 없는 시점에도 동작해야 하므로 로컬 실행이 필요함).
// 인자를 셸 문자열로 조립하지 않고 List<String> argv로 그대로 ProcessBuilder에 넘기므로(셸을 거치지 않음)
// SshCommandExecutor 쪽의 "커맨드 문자열 조립" 인젝션 위험 자체가 없음 — 그래도 호출부(RepositorySnapshotBuilder)는
// repoUrl/branch를 방어적으로 검증한다.
@Slf4j
@Component
public class LocalCommandExecutor {

    public LocalCommandResult exec(List<String> command, File workingDir, Map<String, String> extraEnv, long timeoutMs) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (workingDir != null) {
                builder.directory(workingDir);
            }
            if (extraEnv != null) {
                builder.environment().putAll(extraEnv);
            }

            Process process = builder.start();

            StreamGobbler stdoutGobbler = new StreamGobbler(process.getInputStream());
            StreamGobbler stderrGobbler = new StreamGobbler(process.getErrorStream());
            Thread stdoutThread = new Thread(stdoutGobbler, "local-cmd-stdout");
            Thread stderrThread = new Thread(stderrGobbler, "local-cmd-stderr");
            stdoutThread.setDaemon(true);
            stderrThread.setDaemon(true);
            stdoutThread.start();
            stderrThread.start();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new OpsException(OpsErrorCode.LOCAL_COMMAND_TIMEOUT);
            }

            stdoutThread.join(5_000);
            stderrThread.join(5_000);

            return new LocalCommandResult(process.exitValue(), stdoutGobbler.result(), stderrGobbler.result());
        } catch (IOException e) {
            log.error("로컬 명령 실행 실패: command={}, error={}", command, e.getMessage());
            throw new OpsException(OpsErrorCode.LOCAL_COMMAND_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpsException(OpsErrorCode.LOCAL_COMMAND_FAILED);
        }
    }

    public LocalCommandResult execOrThrow(List<String> command, File workingDir, Map<String, String> extraEnv, long timeoutMs) {
        LocalCommandResult result = exec(command, workingDir, extraEnv, timeoutMs);
        if (!result.isSuccess()) {
            log.warn("로컬 명령 실패(exit={}): command={}, stderr={}", result.exitStatus(), command, trim(result.stderr()));
            throw new OpsException(OpsErrorCode.LOCAL_COMMAND_FAILED);
        }
        return result;
    }

    private String trim(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }

    // 파이프 버퍼가 차서 프로세스가 블로킹되는 걸 막기 위해 별도 스레드에서 stdout/stderr를 즉시 소비
    private static final class StreamGobbler implements Runnable {
        private final InputStream input;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        StreamGobbler(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            try {
                input.transferTo(buffer);
            } catch (IOException ignored) {
                // 프로세스가 강제 종료되면 스트림도 끊기는 게 정상 흐름
            }
        }

        String result() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
