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

    // 멱등: 이미 발급된 vmId면 새로 만들지 않고 기존 공개키를 그대로 반환 (VM 프로비저닝 재시도 대응)
    @Transactional
    public String issue(String vmId) {
        return repository.findByVmId(vmId)
                .map(VmManagementKeyEntity::getPublicKey)
                .orElseGet(() -> {
                    ManagementKeyPair pair = keyGenerator.generate();
                    String encryptedPrivateKey = cipher.encrypt(pair.privateKeyPemBytes());
                    VmManagementKeyEntity entity = VmManagementKeyEntity.create(vmId, pair.publicKeyLine(), encryptedPrivateKey);
                    repository.save(entity);
                    return pair.publicKeyLine();
                });
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

    @Transactional(readOnly = true)
    public VmManagementKeyEntity getActiveKeyOrThrow(String vmId) {
        VmManagementKeyEntity key = repository.findByVmId(vmId)
                .orElseThrow(() -> new OpsException(OpsErrorCode.MANAGEMENT_KEY_NOT_FOUND));
        if (key.getStatus() == KeyStatus.REVOKED) {
            throw new OpsException(OpsErrorCode.MANAGEMENT_KEY_NOT_FOUND);
        }
        return key;
    }

    // 개인키는 byte[]로 반환 — 호출부(JSch 연결)에서 사용 후 zero-fill 책임을 짐
    public byte[] decryptPrivateKey(VmManagementKeyEntity key) {
        return cipher.decrypt(key.getEncryptedPrivateKey());
    }
}
