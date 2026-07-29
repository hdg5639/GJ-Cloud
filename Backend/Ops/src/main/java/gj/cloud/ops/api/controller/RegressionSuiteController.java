package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.preview.regression.RegressionRunRequest;
import gj.cloud.ops.application.preview.regression.RegressionSuiteCreateRequest;
import gj.cloud.ops.application.preview.regression.RegressionSuiteService;
import gj.cloud.ops.application.preview.regression.RegressionViews.RunView;
import gj.cloud.ops.application.preview.regression.RegressionViews.SuiteView;
import gj.cloud.ops.domain.preview.enums.RegressionTriggerType;
import gj.cloud.ops.global.response.ApiResponse;
import gj.cloud.ops.global.security.OpsPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Scenario Regression", description = "PRO 시나리오 회귀 스위트·CI 자동화")
@RestController
@RequestMapping("/ops/preview/regression-suites")
@RequiredArgsConstructor
public class RegressionSuiteController {

    private final RegressionSuiteService regressionSuiteService;

    @Operation(summary = "회귀 테스트 스위트 생성")
    @PostMapping
    public ApiResponse<SuiteView> create(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @AuthenticationPrincipal OpsPrincipal principal,
            @Valid @RequestBody RegressionSuiteCreateRequest request
    ) {
        return ApiResponse.ok(regressionSuiteService.create(
                token(authorization), principal.userId(), request));
    }

    @Operation(summary = "서비스 회귀 테스트 스위트 목록")
    @GetMapping
    public ApiResponse<List<SuiteView>> list(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @AuthenticationPrincipal OpsPrincipal principal,
            @RequestParam String serviceId
    ) {
        return ApiResponse.ok(regressionSuiteService.list(
                token(authorization), principal.userId(), serviceId));
    }

    @Operation(summary = "회귀 테스트 실행 예약")
    @PostMapping("/{suiteId}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<RunView> run(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @AuthenticationPrincipal OpsPrincipal principal,
            @PathVariable String suiteId,
            @Valid @RequestBody RegressionRunRequest request
    ) {
        return ApiResponse.ok(regressionSuiteService.enqueue(
                token(authorization), principal.userId(), suiteId, request, RegressionTriggerType.MANUAL));
    }

    @Operation(summary = "CI 회귀 테스트 실행 예약")
    @PostMapping("/{suiteId}/ci/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<RunView> runFromCi(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @AuthenticationPrincipal OpsPrincipal principal,
            @PathVariable String suiteId,
            @Valid @RequestBody RegressionRunRequest request
    ) {
        return ApiResponse.ok(regressionSuiteService.enqueue(
                token(authorization), principal.userId(), suiteId, request, RegressionTriggerType.CI));
    }

    @Operation(summary = "회귀 테스트 실행 이력")
    @GetMapping("/{suiteId}/runs")
    public ApiResponse<List<RunView>> runs(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @AuthenticationPrincipal OpsPrincipal principal,
            @PathVariable String suiteId
    ) {
        return ApiResponse.ok(regressionSuiteService.listRuns(
                token(authorization), principal.userId(), suiteId));
    }

    @Operation(summary = "회귀 테스트 실행 상세와 실패 단계")
    @GetMapping("/runs/{runId}")
    public ApiResponse<RunView> runDetail(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @AuthenticationPrincipal OpsPrincipal principal,
            @PathVariable String runId
    ) {
        return ApiResponse.ok(regressionSuiteService.getRun(
                token(authorization), principal.userId(), runId));
    }

    @Operation(summary = "회귀 테스트 스위트 비활성화")
    @DeleteMapping("/{suiteId}")
    public ApiResponse<Void> delete(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @AuthenticationPrincipal OpsPrincipal principal,
            @PathVariable String suiteId
    ) {
        regressionSuiteService.deactivate(token(authorization), principal.userId(), suiteId);
        return ApiResponse.ok();
    }

    private String token(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : "";
    }
}
