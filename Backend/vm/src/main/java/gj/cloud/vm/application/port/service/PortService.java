package gj.cloud.vm.application.port.service;

import gj.cloud.vm.application.port.dto.PortAccessAddRequest;
import gj.cloud.vm.application.port.dto.PortAddRequest;
import gj.cloud.vm.application.port.dto.PortResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PortService {
    Mono<PortResponse> addPort(String userId, String ownerEmail, UUID vmId, PortAddRequest request, String bearerToken);
    Flux<PortResponse> getPorts(String userId, UUID vmId);
    Mono<Void> deletePort(String userId, UUID vmId, UUID portId);
    Mono<PortResponse> addPortAccessEmail(String userId, UUID vmId, UUID portId, PortAccessAddRequest request);
    Mono<PortResponse> removePortAccessEmail(String userId, UUID vmId, UUID portId, String email);
    Mono<Void> teardownAllPortsForVm(UUID vmId);
    Mono<Boolean> checkSubdomainAvailable(String bearerToken, String subdomain);
}
