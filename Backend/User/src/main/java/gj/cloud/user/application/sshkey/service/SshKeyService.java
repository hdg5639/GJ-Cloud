package gj.cloud.user.application.sshkey.service;

import gj.cloud.user.application.sshkey.dto.SshKeyRegisterRequest;
import gj.cloud.user.application.sshkey.dto.SshKeyResponse;

import java.util.List;

public interface SshKeyService {
    SshKeyResponse registerKey(String userId, SshKeyRegisterRequest request);
    List<SshKeyResponse> getKeys(String userId);
    void deleteKey(String userId, String keyId);
}
