package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.docker.dto.ComposeStackInfo;
import gj.cloud.ops.application.docker.dto.ContainerInfo;
import gj.cloud.ops.application.docker.dto.ContainerLogsResponse;
import gj.cloud.ops.application.docker.dto.CreateNetworkRequest;
import gj.cloud.ops.application.docker.dto.DockerStatusResponse;
import gj.cloud.ops.application.docker.dto.ImageInfo;
import gj.cloud.ops.application.docker.dto.NetworkInfo;
import gj.cloud.ops.application.docker.service.DockerService;
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

// C절 Docker 관리 UI — 조회는 DOCKER_READ, 제어(시작/정지/삭제/설치/네트워크 생성)는 DOCKER_ADMIN 권한 필요 (C.5)
@Tag(name = "Docker", description = "VM 내부 Docker 컨테이너/이미지/네트워크 관리")
@RestController
@RequestMapping("/api/ops/{vmId}/docker")
@RequiredArgsConstructor
public class DockerController {

    private final DockerService dockerService;

    @Operation(summary = "Docker 설치 여부 확인")
    @GetMapping("/status")
    public ApiResponse<DockerStatusResponse> status(HttpServletRequest request, @PathVariable UUID vmId) {
        boolean installed = dockerService.isDockerInstalled(extractToken(request), vmId.toString());
        return ApiResponse.ok(new DockerStatusResponse(installed));
    }

    @Operation(summary = "Docker 설치", description = "공식 편의 스크립트(get.docker.com)로 설치합니다.")
    @PostMapping("/install")
    public ApiResponse<Void> install(HttpServletRequest request, @PathVariable UUID vmId) {
        dockerService.installDocker(extractToken(request), vmId.toString());
        return ApiResponse.ok();
    }

    @Operation(summary = "컨테이너 목록 조회")
    @GetMapping("/containers")
    public ApiResponse<List<ContainerInfo>> listContainers(HttpServletRequest request, @PathVariable UUID vmId) {
        return ApiResponse.ok(dockerService.listContainers(extractToken(request), vmId.toString()));
    }

    @Operation(summary = "컨테이너 로그 조회")
    @GetMapping("/containers/{containerId}/logs")
    public ApiResponse<ContainerLogsResponse> containerLogs(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @PathVariable String containerId,
            @RequestParam(defaultValue = "200") int tail
    ) {
        String logs = dockerService.getContainerLogs(extractToken(request), vmId.toString(), containerId, tail);
        return ApiResponse.ok(new ContainerLogsResponse(logs));
    }

    @Operation(summary = "컨테이너 시작")
    @PostMapping("/containers/{containerId}/start")
    public ApiResponse<Void> startContainer(HttpServletRequest request, @PathVariable UUID vmId, @PathVariable String containerId) {
        dockerService.startContainer(extractToken(request), vmId.toString(), containerId);
        return ApiResponse.ok();
    }

    @Operation(summary = "컨테이너 정지")
    @PostMapping("/containers/{containerId}/stop")
    public ApiResponse<Void> stopContainer(HttpServletRequest request, @PathVariable UUID vmId, @PathVariable String containerId) {
        dockerService.stopContainer(extractToken(request), vmId.toString(), containerId);
        return ApiResponse.ok();
    }

    @Operation(summary = "컨테이너 재시작")
    @PostMapping("/containers/{containerId}/restart")
    public ApiResponse<Void> restartContainer(HttpServletRequest request, @PathVariable UUID vmId, @PathVariable String containerId) {
        dockerService.restartContainer(extractToken(request), vmId.toString(), containerId);
        return ApiResponse.ok();
    }

    @Operation(summary = "컨테이너 삭제")
    @DeleteMapping("/containers/{containerId}")
    public ApiResponse<Void> removeContainer(HttpServletRequest request, @PathVariable UUID vmId, @PathVariable String containerId) {
        dockerService.removeContainer(extractToken(request), vmId.toString(), containerId);
        return ApiResponse.ok();
    }

    @Operation(summary = "이미지 목록 조회")
    @GetMapping("/images")
    public ApiResponse<List<ImageInfo>> listImages(HttpServletRequest request, @PathVariable UUID vmId) {
        return ApiResponse.ok(dockerService.listImages(extractToken(request), vmId.toString()));
    }

    @Operation(summary = "이미지 삭제")
    @DeleteMapping("/images/{imageId}")
    public ApiResponse<Void> removeImage(HttpServletRequest request, @PathVariable UUID vmId, @PathVariable String imageId) {
        dockerService.removeImage(extractToken(request), vmId.toString(), imageId);
        return ApiResponse.ok();
    }

    @Operation(summary = "네트워크 목록 조회")
    @GetMapping("/networks")
    public ApiResponse<List<NetworkInfo>> listNetworks(HttpServletRequest request, @PathVariable UUID vmId) {
        return ApiResponse.ok(dockerService.listNetworks(extractToken(request), vmId.toString()));
    }

    @Operation(summary = "네트워크 생성")
    @PostMapping("/networks")
    public ApiResponse<Void> createNetwork(
            HttpServletRequest request,
            @PathVariable UUID vmId,
            @Valid @RequestBody CreateNetworkRequest body
    ) {
        dockerService.createNetwork(extractToken(request), vmId.toString(), body.name(), body.driver());
        return ApiResponse.ok();
    }

    @Operation(summary = "네트워크 삭제")
    @DeleteMapping("/networks/{networkId}")
    public ApiResponse<Void> removeNetwork(HttpServletRequest request, @PathVariable UUID vmId, @PathVariable String networkId) {
        dockerService.removeNetwork(extractToken(request), vmId.toString(), networkId);
        return ApiResponse.ok();
    }

    @Operation(summary = "배포된 Compose 스택 목록 조회")
    @GetMapping("/compose")
    public ApiResponse<List<ComposeStackInfo>> listComposeStacks(HttpServletRequest request, @PathVariable UUID vmId) {
        return ApiResponse.ok(dockerService.listComposeStacks(extractToken(request), vmId.toString()));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return header;
    }
}
