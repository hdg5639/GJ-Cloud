package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.deployment.dto.AdminDeploymentOperationsResponse;
import gj.cloud.ops.application.deployment.dto.DeploymentEventPayload;
import gj.cloud.ops.application.deployment.dto.OrphanReconcileResult;
import gj.cloud.ops.application.deployment.service.AdminDeploymentOperationsService;
import gj.cloud.ops.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/deployment-operations")
@RequiredArgsConstructor
public class AdminDeploymentOperationsController {
    private final AdminDeploymentOperationsService service;

    @GetMapping
    public ApiResponse<AdminDeploymentOperationsResponse> getOverview() {
        return ApiResponse.ok(service.getOverview());
    }

    @GetMapping("/deployments/{deploymentId}/events")
    public ApiResponse<List<DeploymentEventPayload>> getDeploymentEvents(
            @PathVariable String deploymentId
    ) {
        return ApiResponse.ok(service.getDeploymentEvents(deploymentId));
    }

    @PostMapping("/reconcile-orphans")
    public ApiResponse<OrphanReconcileResult> reconcileOrphans() {
        return ApiResponse.ok(service.reconcileOrphans());
    }
}
