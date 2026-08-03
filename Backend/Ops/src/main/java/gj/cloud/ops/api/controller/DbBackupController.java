package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.backup.dto.DbBackupRequest;
import gj.cloud.ops.application.backup.dto.DbBackupResponse;
import gj.cloud.ops.application.backup.dto.PreparedDbBackup;
import gj.cloud.ops.application.backup.service.DbBackupService;
import gj.cloud.ops.domain.backup.entity.DbBackupEntity;
import gj.cloud.ops.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

// 수동 DB 백업 — 덤프는 VM에 AES-GCM 암호문으로만 저장하고 전용 권한 API로만 복호화·다운로드한다.
@Tag(name = "DbBackup", description = "수동 DB 백업")
@RestController
@RequestMapping("/ops/{vmId}/backups")
@RequiredArgsConstructor
public class DbBackupController {

    private final DbBackupService dbBackupService;

    @Operation(summary = "DB 백업 트리거", description = "DEPLOY 권한으로 DB를 덤프해 VM에 AES-GCM 암호문으로 저장하고, 생성 즉시 GCM tag와 SHA-256를 검증합니다.")
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

    @Operation(summary = "암호화 백업 다운로드", description = "BACKUP_READ 권한을 검증한 뒤 AES-GCM 백업을 스트리밍 복호화합니다.")
    @GetMapping("/{backupId}/download")
    public void download(
            HttpServletRequest request,
            HttpServletResponse response,
            @PathVariable UUID vmId,
            @PathVariable String backupId
    ) throws IOException {
        PreparedDbBackup backup = dbBackupService.prepareDownload(extractToken(request), vmId.toString(), backupId);
        String encoded = URLEncoder.encode(backup.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'; base-uri 'none'; form-action 'none'");
        dbBackupService.download(backup, response.getOutputStream());
    }

    @Operation(summary = "백업 복구 준비 검증", description = "암호문 전체를 복호화해 GCM tag와 SHA-256 체크섬을 다시 검증합니다.")
    @PostMapping("/{backupId}/verify")
    public ApiResponse<DbBackupResponse> verify(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @PathVariable String backupId
    ) {
        return ApiResponse.ok(DbBackupResponse.from(
                dbBackupService.verify(extractToken(request), vmId.toString(), backupId)));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return header;
    }
}
