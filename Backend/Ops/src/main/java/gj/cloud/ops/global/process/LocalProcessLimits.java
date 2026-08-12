package gj.cloud.ops.global.process;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 로컬 자식 프로세스에 적용할 RLIMIT_NPROC 값을 계산한다.
 *
 * <p>RLIMIT_NPROC는 자식 프로세스 트리만 세는 제한이 아니라 같은 실제 UID가 사용 중인
 * 모든 task를 센다. Ops JVM과 git이 같은 컨테이너 사용자로 실행되므로, clone 예산만
 * 그대로 limit으로 넣으면 JVM 스레드가 예산을 먼저 소비해 git helper fork가 실패한다.</p>
 */
public final class LocalProcessLimits {

    private static final List<Path> CGROUP_PID_CURRENT_PATHS = List.of(
            Path.of("/sys/fs/cgroup/pids.current"),
            Path.of("/sys/fs/cgroup/pids/pids.current")
    );

    private LocalProcessLimits() {
    }

    public static long nprocLimitWithHeadroom(long processHeadroom) {
        return addHeadroom(currentTaskCount(), processHeadroom);
    }

    static long addHeadroom(long currentTaskCount, long processHeadroom) {
        long current = Math.max(1, currentTaskCount);
        long headroom = Math.max(1, processHeadroom);
        if (current > Long.MAX_VALUE - headroom) {
            return Long.MAX_VALUE;
        }
        return current + headroom;
    }

    private static long currentTaskCount() {
        for (Path path : CGROUP_PID_CURRENT_PATHS) {
            try {
                if (Files.isReadable(path)) {
                    return Long.parseLong(Files.readString(path).trim());
                }
            } catch (IOException | NumberFormatException ignored) {
                // cgroup 파일을 읽을 수 없는 로컬 개발 환경에서는 JVM 관측값으로 폴백한다.
            }
        }

        long jvmThreads = ManagementFactory.getThreadMXBean().getThreadCount();
        long descendants = ProcessHandle.current().descendants().count();
        return Math.max(1, jvmThreads + descendants);
    }
}
