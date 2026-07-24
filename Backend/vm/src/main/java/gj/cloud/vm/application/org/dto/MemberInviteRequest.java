package gj.cloud.vm.application.org.dto;

import gj.cloud.vm.domain.org.enums.MemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// email은 항상 필수(검색 결과 선택이든 미가입 사용자 직접 초대든 검색 결과/폼 둘 다 email을 갖고 있음).
// userId/nickname/profileImageUrl은 검색 결과에서 선택해 초대한 경우에만 채워지는 스냅샷 — 예전부터
// email을 클라이언트가 준 값 그대로 신뢰해온 것과 같은 수준으로 그대로 저장한다.
public record MemberInviteRequest(
        String userId,
        @NotBlank @Email String email,
        String nickname,
        String profileImageUrl,
        MemberRole role
) {}
