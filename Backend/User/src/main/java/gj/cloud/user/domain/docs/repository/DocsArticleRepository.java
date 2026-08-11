package gj.cloud.user.domain.docs.repository;

import gj.cloud.user.domain.docs.entity.DocsArticleEntity;
import gj.cloud.user.domain.docs.enums.DocsArticleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocsArticleRepository extends JpaRepository<DocsArticleEntity, UUID> {
    Optional<DocsArticleEntity> findBySlugAndStatus(String slug, DocsArticleStatus status);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);
}
