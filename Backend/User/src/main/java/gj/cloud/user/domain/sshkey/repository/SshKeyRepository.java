package gj.cloud.user.domain.sshkey.repository;

import gj.cloud.user.domain.sshkey.entity.SshKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SshKeyRepository extends JpaRepository<SshKeyEntity, String> {

    List<SshKeyEntity> findAllByUserId(String userId);

    boolean existsByFingerprint(String fingerprint);

    long countByUserId(String userId);
}
