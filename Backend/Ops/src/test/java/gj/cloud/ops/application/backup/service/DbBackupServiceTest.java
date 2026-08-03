package gj.cloud.ops.application.backup.service;

import gj.cloud.ops.application.backup.dto.DbBackupRequest;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.domain.backup.repository.DbBackupRepository;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.ssh.SshCommandExecutor;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DbBackupServiceTest {

    @Test
    void rejectsPasswordControlCharactersBeforeAnyRemoteCall() {
        VmServiceClient vmServiceClient = mock(VmServiceClient.class);
        DbBackupService service = new DbBackupService(
                vmServiceClient,
                mock(VmSshSessionFactory.class),
                mock(SshCommandExecutor.class),
                mock(DbBackupRepository.class),
                mock(BackupFileCipher.class));
        DbBackupRequest request = new DbBackupRequest(
                "postgres", "postgresql", "app", "app_user", "secret\nnext-command");

        assertThatThrownBy(() -> service.backup("token", "vm-id", request))
                .isInstanceOf(OpsException.class);
        verifyNoInteractions(vmServiceClient);
    }
}
