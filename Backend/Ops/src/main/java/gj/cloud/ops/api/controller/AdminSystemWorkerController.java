package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.systemworker.SystemWorkerService;
import gj.cloud.ops.application.systemworker.dto.SystemWorkerResponse;
import gj.cloud.ops.application.terminal.service.TerminalTicketService;
import gj.cloud.ops.domain.systemworker.entity.SystemWorkerEntity;
import gj.cloud.ops.global.response.ApiResponse;
import gj.cloud.ops.global.security.OpsPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/system-workers/auto-preview")
@RequiredArgsConstructor
public class AdminSystemWorkerController {
    private final SystemWorkerService service;
    private final TerminalTicketService terminalTicketService;

    @GetMapping public ApiResponse<SystemWorkerResponse> get() { return ApiResponse.ok(service.get()); }
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED) public ApiResponse<SystemWorkerResponse> create() { return ApiResponse.ok(service.create()); }
    @PostMapping("/start") public ApiResponse<SystemWorkerResponse> start() { return ApiResponse.ok(service.action("start")); }
    @PostMapping("/stop") public ApiResponse<SystemWorkerResponse> stop() { return ApiResponse.ok(service.action("stop")); }
    @PostMapping("/reboot") public ApiResponse<SystemWorkerResponse> reboot() { return ApiResponse.ok(service.action("reboot")); }
    @PostMapping("/reconcile") public ApiResponse<SystemWorkerResponse> reconcile() { return ApiResponse.ok(service.reconcile()); }
    @PostMapping("/repair") public ApiResponse<SystemWorkerResponse> repair() { return ApiResponse.ok(service.repair()); }

    @PostMapping("/console-ticket")
    public ApiResponse<Map<String, String>> consoleTicket(@AuthenticationPrincipal OpsPrincipal principal) {
        SystemWorkerEntity worker = service.requireActive();
        return ApiResponse.ok(Map.of(
                "ticket", terminalTicketService.issueSystemWorkerTicket(
                        principal.userId(), worker.getSshKeyRef(), worker.getInternalIp()),
                "connectionId", worker.getSshKeyRef()));
    }
}
