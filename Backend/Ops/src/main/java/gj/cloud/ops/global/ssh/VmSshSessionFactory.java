package gj.cloud.ops.global.ssh;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import gj.cloud.ops.application.managementkey.service.ManagementKeyService;
import gj.cloud.ops.domain.managementkey.entity.VmManagementKeyEntity;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

// 콘솔(A절)·파일 브라우저(B절)·향후 배포 파이프라인이 공통으로 재활용하는 SSH 세션 생성 모듈.
// VM 관리 키(Ed25519, AES-256-GCM 암호화 저장) 복호화 → JSch 세션 연결까지를 캡슐화함.
@Slf4j
@Component
@RequiredArgsConstructor
public class VmSshSessionFactory {

    private static final int SSH_PORT = 22;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int SERVER_ALIVE_INTERVAL_MS = 15_000;
    private static final int SERVER_ALIVE_COUNT_MAX = 4;

    private final ManagementKeyService managementKeyService;

    @Value("${ops.vm-ssh-username:ubuntu}")
    private String vmSshUsername;

    public String vmSshUsername() {
        return vmSshUsername;
    }

    public Session createSession(String vmId, String internalIp) {
        try {
            return connect(vmId, internalIp);
        } catch (JSchException e) {
            log.error("VM SSH 연결 실패: vmId={}, error={}", vmId, e.getMessage());
            throw new OpsException(OpsErrorCode.SSH_CONNECTION_FAILED);
        }
    }

    /**
     * VM 최초 부팅 중에는 sshd나 authorized_keys가 아직 준비되지 않은 것이 정상이다.
     * 준비 상태 폴링에서는 예상 가능한 연결 실패를 예외/ERROR 로그로 확대하지 않고 빈 값으로 반환한다.
     */
    public Optional<Session> tryCreateSession(String vmId, String internalIp) {
        try {
            return Optional.of(connect(vmId, internalIp));
        } catch (JSchException e) {
            log.debug("VM SSH 준비 대기: vmId={}, error={}", vmId, e.getMessage());
            return Optional.empty();
        }
    }

    private Session connect(String vmId, String internalIp) throws JSchException {
        byte[] privateKeyBytes = null;
        try {
            VmManagementKeyEntity managementKey = managementKeyService.getActiveKeyOrThrow(vmId);
            privateKeyBytes = managementKeyService.decryptPrivateKey(managementKey);

            JSch jsch = new JSch();
            jsch.addIdentity("ops-mgmt-" + vmId, privateKeyBytes, null, null);
            // SEC-011: StrictHostKeyChecking=no는 어떤 호스트 키든 무조건 수락해 MITM에 취약했음.
            // 최초 연결에서 지문을 캡처(TOFU)해 저장하고, 이후 연결은 저장된 지문과 정확히 일치할
            // 때만 허용하는 커스텀 저장소로 대체.
            jsch.setHostKeyRepository(new PinnedHostKeyRepository(jsch, vmId, managementKey.getSshHostKeyFingerprint()));

            Session session = jsch.getSession(vmSshUsername, internalIp, SSH_PORT);
            session.setConfig("StrictHostKeyChecking", "yes");
            // apt 설치처럼 출력 간격이 길 수 있는 명령도 NAT/방화벽의 유휴 연결 정리에 끊기지 않도록
            // SSH transport 레벨 keepalive를 보낸다. 명령 자체의 실행 상한은 SshCommandExecutor가 담당한다.
            session.setServerAliveInterval(SERVER_ALIVE_INTERVAL_MS);
            session.setServerAliveCountMax(SERVER_ALIVE_COUNT_MAX);
            session.connect(CONNECT_TIMEOUT_MS);
            return session;
        } finally {
            // best-effort zeroization: JSch가 내부적으로 별도 복사본을 가질 수 있어 완전한 제거는 보장되지 않음
            if (privateKeyBytes != null) {
                Arrays.fill(privateKeyBytes, (byte) 0);
            }
        }
    }

    // SEC-011: known_hosts 파일 대신 vm_management_keys.ssh_host_key_fingerprint 컬럼을 신뢰 저장소로 사용.
    // storedFingerprint가 없으면(최초 연결) 수락 후 즉시 DB에 캡처하고, 있으면 정확히 일치할 때만 수락한다.
    private class PinnedHostKeyRepository implements HostKeyRepository {
        private final JSch jsch;
        private final String vmId;
        private final String storedFingerprint;

        PinnedHostKeyRepository(JSch jsch, String vmId, String storedFingerprint) {
            this.jsch = jsch;
            this.vmId = vmId;
            this.storedFingerprint = storedFingerprint;
        }

        @Override
        public int check(String host, byte[] key) {
            String fingerprint;
            try {
                fingerprint = new HostKey(host, key).getFingerPrint(jsch);
            } catch (JSchException e) {
                log.warn("SSH 호스트 키 파싱 실패: vmId={}, error={}", vmId, e.getMessage());
                return CHANGED;
            }

            if (storedFingerprint == null) {
                log.info("SSH 호스트 키 최초 캡처(TOFU): vmId={}, fingerprint={}", vmId, fingerprint);
                managementKeyService.recordHostKeyFingerprint(vmId, fingerprint);
                return OK;
            }
            if (storedFingerprint.equals(fingerprint)) {
                return OK;
            }
            log.error("SSH 호스트 키 불일치(변조 의심): vmId={}, 저장된 지문={}, 수신 지문={}", vmId, storedFingerprint, fingerprint);
            return CHANGED;
        }

        @Override
        public void add(HostKey hostkey, UserInfo userInfo) {
            // 지문 저장은 check()에서 이미 처리
        }

        @Override
        public void remove(String host, String type) {
        }

        @Override
        public void remove(String host, String type, byte[] key) {
        }

        @Override
        public String getKnownHostsRepositoryID() {
            return "vm-management-key:" + vmId;
        }

        @Override
        public HostKey[] getHostKey() {
            return new HostKey[0];
        }

        @Override
        public HostKey[] getHostKey(String host, String type) {
            return new HostKey[0];
        }
    }
}
