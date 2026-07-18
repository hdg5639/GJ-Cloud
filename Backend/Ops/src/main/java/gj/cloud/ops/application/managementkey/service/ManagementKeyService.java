package gj.cloud.ops.application.managementkey.service;

import gj.cloud.ops.domain.managementkey.entity.VmManagementKeyEntity;
import gj.cloud.ops.domain.managementkey.enums.KeyStatus;
import gj.cloud.ops.domain.managementkey.repository.VmManagementKeyRepository;
import gj.cloud.ops.global.crypto.AesGcmCipher;
import gj.cloud.ops.global.crypto.ManagementKeyPair;
import gj.cloud.ops.global.crypto.ManagementSshKeyGenerator;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagementKeyService {

    private final VmManagementKeyRepository repository;
    private final ManagementSshKeyGenerator keyGenerator;
    private final AesGcmCipher cipher;

    // 멱등: 이미 ACTIVE 키가 발급된 vmId면 새로 만들지 않고 기존 공개키를 그대로 반환 (VM 프로비저닝 재시도 대응).
    // OPS-SEC-004: ACTIVE가 아닌 기존 키(REVOKE_PENDING/REVOKED/ORPHANED)는 재사용하지 않고 새 키쌍으로 교체함 —
    // 이전에는 상태와 무관하게 존재하는 키의 공개키를 그대로 돌려줘서, 재프로비저닝 시 이미 못 쓰게 된 키가 반환될 수 있었음.
    @Transactional
    public String issue(String vmId) {
        return repository.findByVmId(vmId)
                .map(existing -> existing.getStatus() == KeyStatus.ACTIVE
                        ? existing.getPublicKey()
                        : rotateKey(existing))
                .orElseGet(() -> createKey(vmId));
    }

    private String createKey(String vmId) {
        ManagementKeyPair pair = keyGenerator.generate();
        String encryptedPrivateKey = cipher.encrypt(pair.privateKeyPemBytes());
        repository.save(VmManagementKeyEntity.create(vmId, pair.publicKeyLine(), encryptedPrivateKey));
        return pair.publicKeyLine();
    }

    private String rotateKey(VmManagementKeyEntity existing) {
        ManagementKeyPair pair = keyGenerator.generate();
        String encryptedPrivateKey = cipher.encrypt(pair.privateKeyPemBytes());
        repository.save(existing.withRotatedKey(pair.publicKeyLine(), encryptedPrivateKey));
        return pair.publicKeyLine();
    }

    // VM 삭제와 강결합 금지: 키가 없거나 이미 REVOKE_PENDING/REVOKED여도 에러로 취급하지 않음
    @Transactional
    public void revoke(String vmId) {
        repository.findByVmId(vmId).ifPresent(key -> {
            if (key.getStatus() == KeyStatus.ACTIVE) {
                repository.save(key.withStatus(KeyStatus.REVOKE_PENDING));
            }
        });
    }

    // OPS-SEC-004: REVOKED만 걸러내면 REVOKE_PENDING/ORPHANED 상태의 키로도 SSH 세션을 열 수 있었음 — ACTIVE만 허용
    @Transactional(readOnly = true)
    public VmManagementKeyEntity getActiveKeyOrThrow(String vmId) {
        VmManagementKeyEntity key = repository.findByVmId(vmId)
                .orElseThrow(() -> new OpsException(OpsErrorCode.MANAGEMENT_KEY_NOT_FOUND));
        if (key.getStatus() != KeyStatus.ACTIVE) {
            throw new OpsException(OpsErrorCode.MANAGEMENT_KEY_NOT_FOUND);
        }
        return key;
    }

    // 개인키는 byte[]로 반환 — 호출부(JSch 연결)에서 사용 후 zero-fill 책임을 짐
    public byte[] decryptPrivateKey(VmManagementKeyEntity key) {
        return cipher.decrypt(key.getEncryptedPrivateKey());
    }
}
