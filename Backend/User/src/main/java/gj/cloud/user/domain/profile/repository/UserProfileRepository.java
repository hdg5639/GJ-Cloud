package gj.cloud.user.domain.profile.repository;

import gj.cloud.user.domain.profile.entity.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, String> {
    // 조직 초대용 사용자 검색 — 닉네임 또는 이메일에 부분 일치, 최대 10건
    List<UserProfileEntity> findTop10ByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String nickname, String email);
}
