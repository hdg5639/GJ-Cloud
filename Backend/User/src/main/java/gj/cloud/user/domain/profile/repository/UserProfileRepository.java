package gj.cloud.user.domain.profile.repository;

import gj.cloud.user.domain.profile.entity.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, String> {
    // 조직 초대용 사용자 검색 — 닉네임 또는 이메일에 부분 일치, 최대 10건
    List<UserProfileEntity> findTop10ByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String nickname, String email);

    @Query(value = """
            SELECT profile.*
              FROM user_profiles profile
             WHERE profile.email LIKE CONCAT(:query, '%')
                OR profile.nickname LIKE CONCAT(:query, '%')
             ORDER BY (profile.email = :query) DESC,
                      (profile.nickname = :query) DESC,
                      profile.created_at DESC
             LIMIT 10
            """, nativeQuery = true)
    List<UserProfileEntity> findTop10ByEmailOrNicknamePrefix(@Param("query") String query);

    Page<UserProfileEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<UserProfileEntity> findAllByUserIdIn(Collection<String> userIds);
}
