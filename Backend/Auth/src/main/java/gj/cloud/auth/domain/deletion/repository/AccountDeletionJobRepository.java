package gj.cloud.auth.domain.deletion.repository;

import gj.cloud.auth.domain.deletion.entity.AccountDeletionJobEntity;
import gj.cloud.auth.domain.deletion.enums.AccountDeletionJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountDeletionJobRepository extends JpaRepository<AccountDeletionJobEntity, String> {
    List<AccountDeletionJobEntity> findAllByStatus(AccountDeletionJobStatus status);
}
