package gj.cloud.ops.global.process;

import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.io.BoundedOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

// Ops 컨테이너 자신의 프로세스로 로컬 명령을 실행 (SSH로 원격 VM에 실행하는 SshCommandExecutor와는 별개 — 배포
// 스펙 생성 전 저장소를 분석할 때 사용, 아직 VM SSH 세션이 없는 시점에도 동작해야 하므로 로컬 실행이 필요함).
// 인자를 셸 문자열로 조립하지 않고 List<String> argv로 그대로 ProcessBuilder에 넘기므로(셸을 거치지 않음)
// SshCommandExecutor 쪽의 "커맨드 문자열 조립" 인젝션 위험 자체가 없음 — 그래도 호출부(RepositorySnapshotBuilder)는
// repoUrl/branch를 방어적으로 검증한다.
@Slf4j
@Component
public class LocalCommandExecutor {

    private static final int MAX_CAPTURE_BYTES_PER_STREAM = 1024 * 1024;
    private static final long LIMIT_POLL_INTERVAL_MS = 1_000;

    public LocalCommandResult exec(List<String> command, File workingDir, Map<String, String> extraEnv, long timeoutMs) {
        return exec(command, workingDir, extraEnv, timeoutMs, null, -1);
    }

    public LocalCommandResult exec(
            List<String> command,
            File workingDir,
            Map<String, String> extraEnv,
            long timeoutMs,
            File monitoredDirectory,
            long maxDirectoryBytes
    ) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (workingDir != null) {
                builder.directory(workingDir);
            }
            if (extraEnv != null) {
                builder.environment().putAll(extraEnv);
            }

            process = builder.start();

            StreamGobbler stdoutGobbler = new StreamGobbler(process.getInputStream());
            StreamGobbler stderrGobbler = new StreamGobbler(process.getErrorStream());
            Thread stdoutThread = new Thread(stdoutGobbler, "local-cmd-stdout");
            Thread stderrThread = new Thread(stderrGobbler, "local-cmd-stderr");
            stdoutThread.setDaemon(true);
            stderrThread.setDaemon(true);
            stdoutThread.start();
            stderrThread.start();

            long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (process.isAlive()) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    terminateAndJoin(process, stdoutThread, stderrThread);
                    throw new OpsException(OpsErrorCode.LOCAL_COMMAND_TIMEOUT);
                }
                long waitMillis = Math.min(
                        LIMIT_POLL_INTERVAL_MS,
                        Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
                process.waitFor(waitMillis, TimeUnit.MILLISECONDS);
                if (directoryExceedsLimit(monitoredDirectory, maxDirectoryBytes)) {
                    terminateAndJoin(process, stdoutThread, stderrThread);
                    throw new OpsException(OpsErrorCode.REPOSITORY_TOO_LARGE);
                }
            }

            // Process.isAlive() 관측만으로 종료 처리를 끝내지 않고 추적 중인 직접 자식을 명시적으로
            // 회수한다. git이 남긴 고아 helper는 컨테이너 init이 회수하고, 이 waitFor는 Java가 직접
            // 시작한 prlimit/git 프로세스의 종료 경계를 보장한다.
            process.waitFor();

            stdoutThread.join(5_000);
            stderrThread.join(5_000);

            return new LocalCommandResult(process.exitValue(), stdoutGobbler.result(), stderrGobbler.result());
        } catch (IOException e) {
            log.error("로컬 명령 실행 실패: command={}, error={}", command, e.getMessage());
            throw new OpsException(OpsErrorCode.LOCAL_COMMAND_FAILED);
        } catch (InterruptedException e) {
            if (process != null) {
                terminateProcessTree(process);
            }
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

    private void terminateProcessTree(Process process) {
        ArrayList<ProcessHandle> descendants = new ArrayList<>(process.descendants().toList());
        Collections.reverse(descendants);
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            process.waitFor(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) {
            process.destroyForcibly();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void terminateAndJoin(Process process, Thread stdoutThread, Thread stderrThread)
            throws InterruptedException {
        terminateProcessTree(process);
        stdoutThread.join(5_000);
        stderrThread.join(5_000);
    }

    private boolean directoryExceedsLimit(File directory, long maxBytes) {
        if (directory == null || maxBytes <= 0 || !directory.exists()) {
            return false;
        }
        try (Stream<Path> stream = Files.walk(directory.toPath())) {
            Iterator<Path> files = stream.filter(Files::isRegularFile).iterator();
            long total = 0L;
            while (files.hasNext()) {
                long size = Files.size(files.next());
                if (size > maxBytes - total) {
                    return true;
                }
                total += size;
            }
            return false;
        } catch (IOException ignored) {
            // clone이 동시에 파일을 교체하면 일시적으로 walk가 실패할 수 있으므로 다음 poll에서 다시 확인한다.
            return false;
        }
    }

    // 파이프 버퍼가 차서 프로세스가 블로킹되는 걸 막기 위해 별도 스레드에서 stdout/stderr를 즉시 소비
    private static final class StreamGobbler implements Runnable {
        private final InputStream input;
        private final BoundedOutputStream buffer = new BoundedOutputStream(MAX_CAPTURE_BYTES_PER_STREAM);

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
