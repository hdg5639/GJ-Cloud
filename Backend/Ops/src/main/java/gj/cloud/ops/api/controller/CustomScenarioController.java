package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.preview.custom.CustomScenarioBuilderService;
import gj.cloud.ops.application.preview.custom.CustomScenarioGenerateRequest;
import gj.cloud.ops.application.preview.custom.CustomScenarioRevalidateRequest;
import gj.cloud.ops.application.preview.custom.CustomScenarioView;
import gj.cloud.ops.global.response.ApiResponse;
import gj.cloud.ops.global.security.OpsPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Custom Scenario", description = "PRO 자연어 Custom Scenario 생성·검증·리비전 관리")
@RestController
@RequestMapping("/ops/preview/custom-scenarios")
@RequiredArgsConstructor
public class CustomScenarioController {

    private final CustomScenarioBuilderService customScenarioBuilderService;

    @Operation(summary = "자연어 Custom Scenario 초안 생성")
    @PostMapping
    public ApiResponse<CustomScenarioView> generate(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @AuthenticationPrincipal OpsPrincipal principal,
            @Valid @RequestBody CustomScenarioGenerateRequest request
    ) {
        return ApiResponse.ok(customScenarioBuilderService.generate(
                bearerToken(authorization), principal.userId(), request));
    }

    @Operation(summary = "서비스에 저장된 Custom Scenario 목록")
    @GetMapping
    public ApiResponse<List<CustomScenarioView>> list(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @AuthenticationPrincipal OpsPrincipal principal,
            @RequestParam String serviceId
    ) {
        return ApiResponse.ok(customScenarioBuilderService.list(
                bearerToken(authorization), principal.userId(), serviceId));
    }

    @Operation(summary = "검증된 Custom Scenario 활성화")
    @PostMapping("/{scenarioId}/activate")
    public ApiResponse<CustomScenarioView> activate(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @AuthenticationPrincipal OpsPrincipal principal,
            @PathVariable String scenarioId
    ) {
        return ApiResponse.ok(customScenarioBuilderService.activate(
                bearerToken(authorization), principal.userId(), scenarioId));
    }

    @Operation(summary = "변경된 OpenAPI에 Custom Scenario 재검증")
    @PostMapping("/{scenarioId}/revalidate")
    public ApiResponse<CustomScenarioView> revalidate(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @AuthenticationPrincipal OpsPrincipal principal,
            @PathVariable String scenarioId,
            @Valid @RequestBody CustomScenarioRevalidateRequest request
    ) {
        return ApiResponse.ok(customScenarioBuilderService.revalidate(
                bearerToken(authorization), principal.userId(), scenarioId, request));
    }

    private String bearerToken(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : "";
    }
}
