package gj.cloud.user.application.sshkey.service;

import gj.cloud.user.application.sshkey.dto.SshKeyGenerateRequest;
import gj.cloud.user.application.sshkey.dto.SshKeyGenerateResponse;
import gj.cloud.user.application.sshkey.dto.SshKeyInternalResponse;
import gj.cloud.user.application.sshkey.dto.SshKeyRegisterRequest;
import gj.cloud.user.application.sshkey.dto.SshKeyResponse;

import java.util.List;

public interface SshKeyService {
    SshKeyResponse registerKey(String userId, SshKeyRegisterRequest request);
    SshKeyGenerateResponse generateKey(String userId, SshKeyGenerateRequest request);
    List<SshKeyResponse> getKeys(String userId);
    void deleteKey(String userId, String keyId);
    SshKeyInternalResponse getKeyById(String userId, String keyId);
}
