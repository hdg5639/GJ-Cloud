package gj.cloud.auth.domain.user.repository;

import gj.cloud.auth.domain.user.entity.UserEntity;
import gj.cloud.auth.domain.user.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailAndStatusNot(String email, UserStatus status);

    @Modifying
    @Query(value = """
            DELETE FROM users
             WHERE status = 'PENDING_VERIFICATION'
               AND created_at < :threshold
             ORDER BY created_at ASC
             LIMIT :batchSize
            """, nativeQuery = true)
    int deleteExpiredPendingUsers(
            @Param("threshold") LocalDateTime threshold,
            @Param("batchSize") int batchSize);

    @Modifying
    @Query(value = """
            DELETE FROM users
             WHERE status = 'DELETED'
               AND deleted_at < :threshold
             ORDER BY deleted_at ASC
             LIMIT :batchSize
            """, nativeQuery = true)
    int deleteExpiredDeletedUsers(
            @Param("threshold") LocalDateTime threshold,
            @Param("batchSize") int batchSize);
}
