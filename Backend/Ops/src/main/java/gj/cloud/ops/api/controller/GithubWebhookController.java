package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.github.service.GithubWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ops/webhooks/github")
@RequiredArgsConstructor
public class GithubWebhookController {

    private final GithubWebhookService webhookService;

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
