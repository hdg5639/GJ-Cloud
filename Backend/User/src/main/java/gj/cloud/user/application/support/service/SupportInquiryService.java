package gj.cloud.user.application.support.service;

import gj.cloud.user.application.support.dto.AdminSupportInquiryUpdateRequest;
import gj.cloud.user.application.support.dto.CreateSupportInquiryRequest;
import gj.cloud.user.application.support.dto.SupportInquiryResponse;
import gj.cloud.user.domain.support.entity.SupportInquiryEntity;
import gj.cloud.user.domain.support.enums.SupportInquiryStatus;
import gj.cloud.user.domain.support.repository.SupportInquiryRepository;
import gj.cloud.user.global.exception.UserException;
import gj.cloud.user.global.exception.enums.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupportInquiryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SupportInquiryRepository repository;

    @Transactional
    public SupportInquiryResponse create(String userId, String requesterEmail, CreateSupportInquiryRequest request) {
        SupportInquiryEntity inquiry = SupportInquiryEntity.create(
                userId,
                requesterEmail,
                request.category(),
                cleanRequired(request.title()),
                cleanRequired(request.content()),
                cleanOptional(request.sourceArticleSlug()),
                cleanOptional(request.sourceArticleTitle())
        );
        return SupportInquiryResponse.from(repository.save(inquiry));
    }

    @Transactional(readOnly = true)
    public Page<SupportInquiryResponse> listMine(String userId, int page, int size) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable(page, size))
                .map(SupportInquiryResponse::from);
    }

    @Transactional
    public SupportInquiryResponse closeMine(String userId, UUID inquiryId) {
        SupportInquiryEntity inquiry = repository.findByIdAndUserId(inquiryId, userId)
                .orElseThrow(() -> new UserException(UserErrorCode.SUPPORT_INQUIRY_NOT_FOUND));
        inquiry.close();
        return SupportInquiryResponse.from(inquiry);
    }

    @Transactional(readOnly = true)
    public Page<SupportInquiryResponse> listAdmin(SupportInquiryStatus status, int page, int size) {
        Pageable pageable = pageable(page, size);
        Page<SupportInquiryEntity> inquiries = status == null
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findAllByStatusOrderByCreatedAtDesc(status, pageable);
        return inquiries.map(SupportInquiryResponse::from);
    }

    @Transactional
    public SupportInquiryResponse updateAdmin(
            String adminId,
            UUID inquiryId,
            AdminSupportInquiryUpdateRequest request
    ) {
        SupportInquiryEntity inquiry = repository.findById(inquiryId)
                .orElseThrow(() -> new UserException(UserErrorCode.SUPPORT_INQUIRY_NOT_FOUND));

        switch (request.status()) {
            case ANSWERED -> {
                String response = cleanRequired(request.response());
                if (response.isBlank()) {
                    throw new UserException(UserErrorCode.SUPPORT_INQUIRY_RESPONSE_REQUIRED);
                }
                inquiry.answer(adminId, response);
            }
            case CLOSED -> inquiry.close();
            case OPEN -> inquiry.reopen();
        }
        return SupportInquiryResponse.from(inquiry);
    }

    private Pageable pageable(int page, int size) {
        int safePage = Math.max(1, page) - 1;
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        return PageRequest.of(safePage, safeSize);
    }

    private String cleanRequired(String value) {
        return value == null ? "" : value.trim();
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
