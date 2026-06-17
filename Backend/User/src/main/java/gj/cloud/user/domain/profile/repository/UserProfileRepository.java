package gj.cloud.user.domain.profile.repository;

import gj.cloud.user.domain.profile.entity.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, String> {
}
