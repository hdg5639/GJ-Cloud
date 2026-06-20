package gj.cloud.auth.domain.user.repository;

import gj.cloud.auth.domain.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);

    @Modifying
    @Query("DELETE FROM UserEntity u WHERE u.status = 'PENDING_VERIFICATION' AND u.createdAt < :threshold")
    int deleteExpiredPendingUsers(@Param("threshold") LocalDateTime threshold);
}
