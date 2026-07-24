package gj.cloud.ops.domain.github.repository;

import gj.cloud.ops.domain.github.entity.GithubInstallationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GithubInstallationRepository extends JpaRepository<GithubInstallationEntity, String> {

    List<GithubInstallationEntity> findAllByUserIdOrderByCreatedAtAsc(String userId);

    Optional<GithubInstallationEntity> findByInstallationIdAndUserId(Long installationId, String userId);
}
