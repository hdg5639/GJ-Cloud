package gj.cloud.ops.global.ssh;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.io.BoundedOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.io.OutputStream;

// 배포 파이프라인(git/docker compose/헬스체크 curl 등)이 공통으로 쓰는 원격 명령 실행 모듈.
// 콘솔(ChannelShell)·파일 브라우저(ChannelSftp)에 이은 세 번째 SSH 채널 타입.
// 민감 정보(PAT 등)는 커맨드 문자열에 절대 넣지 않는 것을 전제로 함(호출부가 GIT_ASKPASS 등으로 분리) —
// 이 컴포넌트는 어떤 인자가 비밀인지 알 수 없으므로 커맨드 원문은 로그에 남기지 않음.
@Slf4j
@Component
public class SshCommandExecutor {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final long POLL_INTERVAL_MS = 100;
    private static final int MAX_CAPTURE_BYTES_PER_STREAM = 1024 * 1024;

    public CommandResult exec(Session session, String command, long timeoutMs) {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            BoundedOutputStream stdout = new BoundedOutputStream(MAX_CAPTURE_BYTES_PER_STREAM);
            BoundedOutputStream stderr = new BoundedOutputStream(MAX_CAPTURE_BYTES_PER_STREAM);
            channel.setOutputStream(stdout);
            channel.setErrStream(stderr);

            channel.connect(CONNECT_TIMEOUT_MS);

            long start = System.currentTimeMillis();
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() - start > timeoutMs) {
                    throw new OpsException(OpsErrorCode.SSH_COMMAND_TIMEOUT);
                }
                Thread.sleep(POLL_INTERVAL_MS);
            }

            return new CommandResult(channel.getExitStatus(),
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8));
        } catch (JSchException e) {
            log.error("SSH 명령 실행 실패: error={}", e.getMessage());
            throw new OpsException(OpsErrorCode.SSH_COMMAND_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpsException(OpsErrorCode.SSH_COMMAND_FAILED);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }

    public CommandResult execTo(Session session, String command, OutputStream stdoutTarget, long timeoutMs) {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            BoundedOutputStream stderr = new BoundedOutputStream(MAX_CAPTURE_BYTES_PER_STREAM);
            channel.setOutputStream(stdoutTarget, true);
            channel.setErrStream(stderr);
            channel.connect(CONNECT_TIMEOUT_MS);

            long start = System.currentTimeMillis();
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() - start > timeoutMs) {
                    throw new OpsException(OpsErrorCode.SSH_COMMAND_TIMEOUT);
                }
                Thread.sleep(POLL_INTERVAL_MS);
            }
            return new CommandResult(channel.getExitStatus(), "", stderr.toString(StandardCharsets.UTF_8));
        } catch (JSchException e) {
            log.error("SSH 스트리밍 명령 실행 실패: error={}", e.getMessage());
            throw new OpsException(OpsErrorCode.SSH_COMMAND_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpsException(OpsErrorCode.SSH_COMMAND_FAILED);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }

    // exit code != 0이면 즉시 예외로 전환 (파이프라인 각 단계는 실패를 즉시 감지해야 함)
    public CommandResult execOrThrow(Session session, String command, long timeoutMs) {
        CommandResult result = exec(session, command, timeoutMs);
        if (!result.isSuccess()) {
            log.error("SSH 명령 실패(exit={}): stderr={}", result.exitStatus(), result.stderr());
            throw new OpsException(OpsErrorCode.SSH_COMMAND_FAILED);
        }
        return result;
    }
}
