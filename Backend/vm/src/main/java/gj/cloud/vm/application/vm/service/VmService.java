package gj.cloud.vm.application.vm.service;

import gj.cloud.vm.application.vm.dto.VmCreateRequest;
import gj.cloud.vm.application.vm.dto.VmPowerRequest;
import gj.cloud.vm.application.vm.dto.VmResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface VmService {
    Mono<VmResponse> createVm(String userId, String bearerToken, VmCreateRequest request);
    Flux<VmResponse> getVms(String userId);
    Mono<VmResponse> getVm(String userId, UUID vmId);
    Mono<Void> deleteVm(String userId, UUID vmId);
    Mono<VmResponse> changePower(String userId, UUID vmId, VmPowerRequest request);
}
