package gj.cloud.user.application.support.service;

import gj.cloud.user.application.support.dto.AdminSupportInquiryUpdateRequest;
import gj.cloud.user.application.support.dto.CreateSupportInquiryRequest;
import gj.cloud.user.domain.support.entity.SupportInquiryEntity;
import gj.cloud.user.domain.support.enums.SupportInquiryCategory;
import gj.cloud.user.domain.support.enums.SupportInquiryStatus;
import gj.cloud.user.domain.support.repository.SupportInquiryRepository;
import gj.cloud.user.global.exception.UserException;
import gj.cloud.user.global.exception.enums.UserErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportInquiryServiceTest {

    @Mock
    private SupportInquiryRepository repository;

    private SupportInquiryService service;

    @BeforeEach
    void setUp() {
        service = new SupportInquiryService(repository);
    }

    @Test
    void createUsesAuthenticatedUserAndNormalizesText() {
        when(repository.save(any(SupportInquiryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(
                "user-1",
                "user@example.com",
                new CreateSupportInquiryRequest(
                        SupportInquiryCategory.DOCS,
                        "  설명서 보완 요청  ",
                        "  필요한 내용입니다.  ",
                        "getting-started",
                        "  시작하기  "
                )
        );

        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.requesterEmail()).isEqualTo("user@example.com");
        assertThat(response.title()).isEqualTo("설명서 보완 요청");
        assertThat(response.content()).isEqualTo("필요한 내용입니다.");
        assertThat(response.sourceArticleTitle()).isEqualTo("시작하기");
        assertThat(response.status()).isEqualTo(SupportInquiryStatus.OPEN);
    }

    @Test
    void listMineScopesQueryToAuthenticatedUser() {
        SupportInquiryEntity inquiry = inquiry();
        when(repository.findAllByUserIdOrderByCreatedAtDesc(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inquiry)));

        var result = service.listMine("user-1", 1, 20);

        assertThat(result.getContent()).hasSize(1);
        verify(repository).findAllByUserIdOrderByCreatedAtDesc(eq("user-1"), any(Pageable.class));
    }

    @Test
    void closeMineDoesNotLoadAnotherUsersInquiry() {
        UUID inquiryId = UUID.randomUUID();
        when(repository.findByIdAndUserId(inquiryId, "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.closeMine("user-1", inquiryId))
                .isInstanceOf(UserException.class)
                .extracting(cause -> ((UserException) cause).getErrorCode())
                .isEqualTo(UserErrorCode.SUPPORT_INQUIRY_NOT_FOUND);
    }

    @Test
    void adminAnswerRequiresContentAndRecordsResponse() {
        UUID inquiryId = UUID.randomUUID();
        SupportInquiryEntity inquiry = inquiry();
        when(repository.findById(inquiryId)).thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> service.updateAdmin(
                "admin-1",
                inquiryId,
                new AdminSupportInquiryUpdateRequest(SupportInquiryStatus.ANSWERED, "  ")
        ))
                .isInstanceOf(UserException.class)
                .extracting(cause -> ((UserException) cause).getErrorCode())
                .isEqualTo(UserErrorCode.SUPPORT_INQUIRY_RESPONSE_REQUIRED);

        var response = service.updateAdmin(
                "admin-1",
                inquiryId,
                new AdminSupportInquiryUpdateRequest(SupportInquiryStatus.ANSWERED, "  확인 후 보완하겠습니다.  ")
        );

        assertThat(response.status()).isEqualTo(SupportInquiryStatus.ANSWERED);
        assertThat(response.response()).isEqualTo("확인 후 보완하겠습니다.");
        assertThat(response.respondedBy()).isEqualTo("admin-1");
        assertThat(response.respondedAt()).isNotNull();
    }

    private SupportInquiryEntity inquiry() {
        return SupportInquiryEntity.create(
                "user-1",
                "user@example.com",
                SupportInquiryCategory.TECHNICAL,
                "배포 문의",
                "배포가 완료되지 않습니다.",
                null,
                null
        );
    }
}
