package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.github.service.GithubWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "GitHub Webhook", description = "GitHub App push 이벤트 수신")
@SecurityRequirements
@RestController
@RequestMapping("/ops/webhooks/github")
@RequiredArgsConstructor
public class GithubWebhookController {

    private final GithubWebhookService webhookService;

    @Operation(
            summary = "GitHub Webhook 수신",
            description = "X-Hub-Signature-256 HMAC 서명을 검증하고 push의 정확한 commit SHA로 자동 재배포를 예약합니다. Bearer JWT 대신 GitHub 서명을 사용합니다."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void receive(
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] body
    ) {
        webhookService.handle(deliveryId, eventType, signature, body);
    }
}
