package gj.cloud.auth.api.controller;

import gj.cloud.auth.api.controller.dto.SecurityAuditLogResponse;
import gj.cloud.auth.application.auditlog.service.SecurityAuditLogService;
import gj.cloud.auth.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

// OBS-001: 관리자가 고위험 보안 이벤트(로그인 성공/실패, refresh 재사용 탈취 감지, 계정 정지/복구/탈퇴)를
// 최신순으로 조회하는 최소 API. actorId로 특정 계정의 이력만 좁혀볼 수 있음.
@Hidden
@RestController
@RequestMapping("/admin/security-audit-logs")
@RequiredArgsConstructor
public class AdminSecurityAuditLogController {

    private final SecurityAuditLogService securityAuditLogService;

    @GetMapping
    public ApiResponse<Page<SecurityAuditLogResponse>> list(
            @RequestParam(required = false) String actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        Page<SecurityAuditLogResponse> result = securityAuditLogService.list(actorId, pageable)
                .map(SecurityAuditLogResponse::from);
        return ApiResponse.ok(result);
    }
}
