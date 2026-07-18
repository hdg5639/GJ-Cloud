package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.terminal.dto.TerminalTicketResponse;
import gj.cloud.ops.application.terminal.service.TerminalTicketService;
import gj.cloud.ops.global.response.ApiResponse;
import gj.cloud.ops.global.security.OpsPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Terminal", description = "웹 SSH 콘솔 티켓 발급")
@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
public class TerminalController {

    private final TerminalTicketService terminalTicketService;

    @Operation(summary = "터미널 접속용 일회용 티켓 발급", description = "30초 내에 WebSocket 연결에 사용해야 하는 일회용 티켓을 발급합니다.")
    @PostMapping("/{vmId}/terminal-ticket")
    public ApiResponse<TerminalTicketResponse> issueTicket(
            @AuthenticationPrincipal OpsPrincipal principal,
            HttpServletRequest request,
            @PathVariable UUID vmId
    ) {
        String bearerToken = extractToken(request);
        String ticket = terminalTicketService.issueTicket(bearerToken, principal.userId(), vmId);
        return ApiResponse.ok(new TerminalTicketResponse(ticket));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : header;
    }
}
