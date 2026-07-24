package gj.cloud.vm.application.port.service;

import gj.cloud.vm.application.port.dto.DeploymentRouteItem;
import gj.cloud.vm.application.port.dto.PortAccessAddRequest;
import gj.cloud.vm.application.port.dto.PortAddRequest;
import gj.cloud.vm.application.port.dto.PortResponse;
import gj.cloud.vm.application.port.dto.SubdomainCheckResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface PortService {
    Mono<PortResponse> addPort(String userId, String ownerEmail, UUID vmId, PortAddRequest request, String bearerToken);
    Flux<PortResponse> getPorts(String userId, String email, UUID vmId);
    Mono<Void> deletePort(String userId, String userEmail, UUID vmId, UUID portId);
    Mono<PortResponse> addPortAccessEmail(String userId, String userEmail, UUID vmId, UUID portId, PortAccessAddRequest request);
    Mono<PortResponse> removePortAccessEmail(String userId, String userEmail, UUID vmId, UUID portId, String targetEmail);
    Mono<Void> teardownAllPortsForVm(UUID vmId);
    Mono<SubdomainCheckResponse> checkSubdomainAvailable(String bearerToken, String subdomain);
    Mono<Void> syncDeploymentRoutes(String requesterId, String requesterEmail, UUID vmId, String deploymentAppId,
                                     String deploymentId,
                                     List<DeploymentRouteItem> routes, String bearerToken);
    Mono<Void> syncDeploymentRoutesAutomation(String requesterId, String requesterEmail, UUID vmId,
                                               String deploymentAppId, String deploymentId,
                                               List<DeploymentRouteItem> routes);
}
