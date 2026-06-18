package gj.cloud.user.application.sshkey.service.impl;

import gj.cloud.user.application.sshkey.dto.SshKeyInternalResponse;
import gj.cloud.user.application.sshkey.dto.SshKeyRegisterRequest;
import gj.cloud.user.application.sshkey.dto.SshKeyResponse;
import gj.cloud.user.application.sshkey.service.SshKeyService;
import gj.cloud.user.domain.sshkey.entity.SshKeyEntity;
import gj.cloud.user.domain.sshkey.repository.SshKeyRepository;
import gj.cloud.user.global.exception.UserException;
import gj.cloud.user.global.exception.enums.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SshKeyServiceImpl implements SshKeyService {

    private static final int SSH_KEY_MAX_COUNT = 5;
    private static final Set<String> SUPPORTED_KEY_TYPES = Set.of(
            "ssh-rsa", "ssh-ed25519", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521"
    );

    private final SshKeyRepository sshKeyRepository;

    @Override
    @Transactional
    public SshKeyResponse registerKey(String userId, SshKeyRegisterRequest request) {
        String publicKey = request.publicKey().trim();

        validateKeyFormat(publicKey);

        String fingerprint = computeFingerprint(publicKey);

        if (sshKeyRepository.existsByFingerprint(fingerprint)) {
            throw new UserException(UserErrorCode.SSH_KEY_ALREADY_EXISTS);
        }

        if (sshKeyRepository.countByUserId(userId) >= SSH_KEY_MAX_COUNT) {
            throw new UserException(UserErrorCode.SSH_KEY_LIMIT_EXCEEDED);
        }

        SshKeyEntity entity = SshKeyEntity.create(userId, publicKey, fingerprint, request.name());
        return SshKeyResponse.from(sshKeyRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SshKeyResponse> getKeys(String userId) {
        return sshKeyRepository.findAllByUserId(userId).stream()
                .map(SshKeyResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public void deleteKey(String userId, String keyId) {
        SshKeyEntity key = sshKeyRepository.findById(keyId)
                .orElseThrow(() -> new UserException(UserErrorCode.SSH_KEY_NOT_FOUND));

        if (!key.getUserId().equals(userId)) {
            throw new UserException(UserErrorCode.FORBIDDEN);
        }

        sshKeyRepository.delete(key);
    }

    @Override
    @Transactional(readOnly = true)
    public SshKeyInternalResponse getKeyById(String userId, String keyId) {
        SshKeyEntity key = sshKeyRepository.findById(keyId)
                .orElseThrow(() -> new UserException(UserErrorCode.SSH_KEY_NOT_FOUND));

        if (!key.getUserId().equals(userId)) {
            throw new UserException(UserErrorCode.FORBIDDEN);
        }

        return SshKeyInternalResponse.from(key);
    }

    private void validateKeyFormat(String publicKey) {
        String[] parts = publicKey.split("\\s+");
        if (parts.length < 2 || !SUPPORTED_KEY_TYPES.contains(parts[0])) {
            throw new UserException(UserErrorCode.INVALID_SSH_KEY_FORMAT);
        }
        try {
            Base64.getDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new UserException(UserErrorCode.INVALID_SSH_KEY_FORMAT);
        }
    }

    // Computes SHA256 fingerprint matching `ssh-keygen -lf` output
    private String computeFingerprint(String publicKey) {
        try {
            String[] parts = publicKey.split("\\s+");
            byte[] keyBytes = Base64.getDecoder().decode(parts[1]);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(keyBytes);
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new UserException(UserErrorCode.INVALID_SSH_KEY_FORMAT);
        }
    }
}
