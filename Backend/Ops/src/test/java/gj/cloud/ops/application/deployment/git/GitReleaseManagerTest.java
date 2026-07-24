package gj.cloud.ops.application.deployment.git;

import com.jcraft.jsch.Session;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.ssh.CommandResult;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitReleaseManagerTest {

    private final SshCommandExecutor sshCommandExecutor = mock(SshCommandExecutor.class);
    private final Session session = mock(Session.class);
    private GitReleaseManager manager;

    @BeforeEach
    void setUp() {
        manager = spy(new GitReleaseManager(
                sshCommandExecutor,
                mock(VmSshSessionFactory.class),
                mock(GitCloneSecurityValidator.class)));
        doNothing().when(manager).pauseBeforeGitRetry();
    }

    @Test
    void retriesTransientDnsFailureAndThenSucceeds() {
        String command = "git fetch";
        when(sshCommandExecutor.exec(eq(session), eq(command), anyLong()))
                .thenReturn(new CommandResult(128, "", "Could not resolve host: github.com"))
                .thenReturn(new CommandResult(0, "", ""));

        CommandResult result = manager.execGitNetworkCommandWithRetry(session, command, "fetch");

        assertThat(result.isSuccess()).isTrue();
        verify(sshCommandExecutor, times(2)).exec(eq(session), eq(command), anyLong());
    }

    @Test
    void doesNotRetryAuthenticationFailure() {
        String command = "git fetch";
        when(sshCommandExecutor.exec(eq(session), eq(command), anyLong()))
                .thenReturn(new CommandResult(128, "", "Authentication failed"));

        assertThatThrownBy(() -> manager.execGitNetworkCommandWithRetry(session, command, "fetch"))
                .isInstanceOfSatisfying(OpsException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(OpsErrorCode.SSH_COMMAND_FAILED));
        verify(sshCommandExecutor).exec(eq(session), eq(command), anyLong());
    }

    @Test
    void reportsRepositoryNetworkErrorAfterRetryBudgetIsExhausted() {
        String command = "git fetch";
        when(sshCommandExecutor.exec(eq(session), eq(command), anyLong()))
                .thenReturn(new CommandResult(128, "", "Temporary failure in name resolution"));

        assertThatThrownBy(() -> manager.execGitNetworkCommandWithRetry(session, command, "fetch"))
                .isInstanceOfSatisfying(OpsException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(OpsErrorCode.REPOSITORY_NETWORK_UNAVAILABLE));
        verify(sshCommandExecutor, times(3)).exec(eq(session), eq(command), anyLong());
    }

    @Test
    void resolvesTheExactWebhookCommitInsteadOfTheMovingBranchHead() {
        String requestedRevision = "0123456789abcdef0123456789abcdef01234567";
        when(sshCommandExecutor.exec(eq(session), org.mockito.ArgumentMatchers.contains(" fetch --prune origin"), anyLong()))
                .thenReturn(new CommandResult(0, "", ""));
        when(sshCommandExecutor.execOrThrow(
                eq(session),
                eq("git -C '/home/null/gamjabox/apps/target-1/repository.git' cat-file -e '"
                        + requestedRevision + "^{commit}'"),
                anyLong()))
                .thenReturn(new CommandResult(0, "", ""));
        when(sshCommandExecutor.execOrThrow(
                eq(session),
                eq("git -C '/home/null/gamjabox/apps/target-1/repository.git' rev-parse '"
                        + requestedRevision + "'"),
                anyLong()))
                .thenReturn(new CommandResult(0, requestedRevision + "\n", ""));

        String resolved = manager.fetchAndResolveCommit(
                session, "target-1", "main", null, requestedRevision);

        assertThat(resolved).isEqualTo(requestedRevision);
    }

    @Test
    void rejectsInvalidWebhookRevisionBeforeRunningGit() {
        assertThatThrownBy(() -> manager.fetchAndResolveCommit(
                session, "target-1", "main", null, "main; touch /tmp/pwned"))
                .isInstanceOfSatisfying(OpsException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(OpsErrorCode.INVALID_REPO_CONFIG));

        verifyNoInteractions(sshCommandExecutor);
    }

    @Test
    void rejectsCredentialsEmbeddedInRepositoryUrl() {
        assertThatThrownBy(() -> manager.ensureBareRepo(
                session,
                "target-1",
                "https://secret-token@github.com/example/repository.git",
                null))
                .isInstanceOfSatisfying(OpsException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(
                                OpsErrorCode.INVALID_REPO_CONFIG));

        verifyNoInteractions(sshCommandExecutor);
    }
}
