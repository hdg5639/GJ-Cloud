package gj.cloud.vm.application.org.dto;

// User 서비스 /internal/profiles/search 응답을 그대로 옮겨 담는 DTO (조직 초대용 사용자 검색)
public record MemberSearchResult(
        String userId,
        String nickname,
        String email,
        String profileImageUrl
) {}
