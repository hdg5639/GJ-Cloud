package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.filebrowser.FileBrowserService;
import gj.cloud.ops.application.filebrowser.FileStreamService;
import gj.cloud.ops.application.filebrowser.FileStreamTicketService;
import gj.cloud.ops.application.filebrowser.dto.CreateDirectoryRequest;
import gj.cloud.ops.application.filebrowser.dto.FileContentResponse;
import gj.cloud.ops.application.filebrowser.dto.FileContentSaveRequest;
import gj.cloud.ops.application.filebrowser.dto.FileListResponse;
import gj.cloud.ops.application.filebrowser.dto.FileStreamTicketPayload;
import gj.cloud.ops.application.filebrowser.dto.FileStreamTicketResponse;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import gj.cloud.ops.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

// 일반 JWT(aud=ops-service) 기반 REST API — 콘솔과 달리 티켓이 필요 없음 (WebSocket이 아닌 일반 HTTP 요청)
@Tag(name = "FileBrowser", description = "VM 내부 파일 브라우저 (SFTP)")
@RestController
@RequestMapping("/ops/{vmId}/files")
@RequiredArgsConstructor
public class FileBrowserController {

    private final FileBrowserService fileBrowserService;
    private final FileStreamTicketService fileStreamTicketService;
    private final FileStreamService fileStreamService;

    @Operation(summary = "디렉토리 목록 조회")
    @GetMapping
    public ApiResponse<FileListResponse> list(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @RequestParam(required = false) String path
    ) {
        return ApiResponse.ok(fileBrowserService.list(extractToken(request), vmId.toString(), path));
    }

    @Operation(summary = "텍스트 파일 내용 조회", description = "바이너리 파일이거나 최대 크기를 초과하면 편집할 수 없습니다.")
    @GetMapping("/content")
    public ApiResponse<FileContentResponse> readContent(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @RequestParam String path
    ) {
        return ApiResponse.ok(fileBrowserService.readContent(extractToken(request), vmId.toString(), path));
    }

    @Operation(summary = "텍스트 파일 내용 저장")
    @PutMapping("/content")
    public ApiResponse<Void> saveContent(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @RequestParam String path,
            @Valid @RequestBody FileContentSaveRequest saveRequest
    ) {
        fileBrowserService.writeContent(extractToken(request), vmId.toString(), path, saveRequest.content());
        return ApiResponse.ok();
    }

    @Operation(summary = "새 디렉토리 생성")
    @PostMapping("/directories")
    public ApiResponse<Void> createDirectory(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @Valid @RequestBody CreateDirectoryRequest createRequest
    ) {
        fileBrowserService.createDirectory(extractToken(request), vmId.toString(), createRequest.parentPath(), createRequest.name());
        return ApiResponse.ok();
    }

    @Operation(summary = "파일 업로드")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> upload(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @RequestParam String path,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            fileBrowserService.upload(extractToken(request), vmId.toString(), path,
                    file.getOriginalFilename(), file.getInputStream(), file.getSize());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ApiResponse.ok();
    }

    @Operation(summary = "파일 다운로드")
    @GetMapping("/download")
    public void download(
            HttpServletRequest request,
            HttpServletResponse response,
            @PathVariable UUID vmId,
            @RequestParam String path
    ) throws IOException {
        // 헤더는 스트리밍 시작 전에 반드시 먼저 설정해야 하므로, 실제 SFTP 조회 전에
        // 요청받은 path 문자열에서 직접 파일명을 뽑아 씀 (resolve 결과를 기다리지 않음)
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'; base-uri 'none'; form-action 'none'");

        fileBrowserService.download(extractToken(request), vmId.toString(), path, response.getOutputStream());
    }

    @Operation(summary = "미디어 스트리밍 티켓 발급", description = "래스터 이미지/오디오/비디오 미리보기용. SVG 등 active content는 제외되며, seek/버퍼링을 위해 3분 동안 재사용할 수 있습니다.")
    @PostMapping("/stream-ticket")
    public ApiResponse<FileStreamTicketResponse> issueStreamTicket(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @RequestParam String path
    ) {
        String ticket = fileStreamTicketService.issue(extractToken(request), vmId.toString(), path);
        return ApiResponse.ok(new FileStreamTicketResponse(ticket));
    }

    @Operation(summary = "미디어 스트리밍 (Range 지원)", description = "티켓으로만 인증됩니다(JWT 불필요) — <video>/<audio> 태그가 직접 호출할 수 있도록 SecurityConfig에서 permitAll 처리됨.")
    @GetMapping("/stream")
    public void stream(
            HttpServletRequest request,
            HttpServletResponse response,
            @PathVariable UUID vmId,
            @RequestParam String ticket
    ) throws IOException {
        FileStreamTicketPayload payload = fileStreamTicketService.validate(ticket, vmId.toString())
                .orElseThrow(() -> new OpsException(OpsErrorCode.INVALID_TICKET));
        fileStreamService.stream(payload, request, response);
    }

    @Operation(summary = "파일/디렉토리 삭제", description = "디렉토리는 하위 항목까지 재귀적으로 삭제됩니다.")
    @DeleteMapping
    public ApiResponse<Void> delete(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @RequestParam String path
    ) {
        fileBrowserService.delete(extractToken(request), vmId.toString(), path);
        return ApiResponse.ok();
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return header;
    }
}
