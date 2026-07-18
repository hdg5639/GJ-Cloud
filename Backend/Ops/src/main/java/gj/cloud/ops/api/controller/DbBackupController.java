package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.backup.dto.DbBackupRequest;
import gj.cloud.ops.application.backup.dto.DbBackupResponse;
import gj.cloud.ops.application.backup.service.DbBackupService;
import gj.cloud.ops.domain.backup.entity.DbBackupEntity;
import gj.cloud.ops.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// 11절 수동 DB 백업 — 덤프 파일은 VM 파일시스템(backups/)에 저장되며, 다운로드는 기존 파일 브라우저를 그대로 사용
@Tag(name = "DbBackup", description = "수동 DB 백업")
@RestController
@RequestMapping("/api/ops/{vmId}/backups")
@RequiredArgsConstructor
public class DbBackupController {

    private final DbBackupService dbBackupService;

    @Operation(summary = "DB 백업 트리거", description = "지정한 서비스의 DB를 즉시 덤프해 VM의 backups/ 디렉토리에 저장합니다. 다운로드는 파일 브라우저를 이용하세요.")
    @PostMapping
    public ApiResponse<DbBackupResponse> backup(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @Valid @RequestBody DbBackupRequest body
    ) {
        DbBackupEntity entity = dbBackupService.backup(extractToken(request), vmId.toString(), body);
        return ApiResponse.ok(DbBackupResponse.from(entity));
    }

    @Operation(summary = "백업 이력 조회")
    @GetMapping
    public ApiResponse<List<DbBackupResponse>> list(HttpServletRequest request, @PathVariable UUID vmId) {
        List<DbBackupResponse> responses = dbBackupService.history(extractToken(request), vmId.toString())
                .stream()
                .map(DbBackupResponse::from)
                .toList();
        return ApiResponse.ok(responses);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return header;
    }
}
