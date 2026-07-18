package gj.cloud.ops.global.ssh;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import gj.cloud.ops.application.managementkey.service.ManagementKeyService;
import gj.cloud.ops.domain.managementkey.entity.VmManagementKeyEntity;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;

// 콘솔(A절)·파일 브라우저(B절)·향후 배포 파이프라인이 공통으로 재활용하는 SSH 세션 생성 모듈.
// VM 관리 키(Ed25519, AES-256-GCM 암호화 저장) 복호화 → JSch 세션 연결까지를 캡슐화함.
@Slf4j
@Component
@RequiredArgsConstructor
public class VmSshSessionFactory {

    private static final int SSH_PORT = 22;
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    private final ManagementKeyService managementKeyService;

    @Value("${ops.vm-ssh-username:ubuntu}")
    private String vmSshUsername;

    public String vmSshUsername() {
        return vmSshUsername;
    }

    public Session createSession(String vmId, String internalIp) {
        byte[] privateKeyBytes = null;
        try {
            VmManagementKeyEntity managementKey = managementKeyService.getActiveKeyOrThrow(vmId);
            privateKeyBytes = managementKeyService.decryptPrivateKey(managementKey);

            JSch jsch = new JSch();
            jsch.addIdentity("ops-mgmt-" + vmId, privateKeyBytes, null, null);

            Session session = jsch.getSession(vmSshUsername, internalIp, SSH_PORT);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(CONNECT_TIMEOUT_MS);
            return session;
        } catch (JSchException e) {
            log.error("VM SSH 연결 실패: vmId={}, error={}", vmId, e.getMessage());
            throw new OpsException(OpsErrorCode.SSH_CONNECTION_FAILED);
        } finally {
            // best-effort zeroization: JSch가 내부적으로 별도 복사본을 가질 수 있어 완전한 제거는 보장되지 않음
            if (privateKeyBytes != null) {
                Arrays.fill(privateKeyBytes, (byte) 0);
            }
        }
    }
}
