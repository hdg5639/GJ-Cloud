package gj.cloud.vm.application.vm.service;

import gj.cloud.vm.application.vm.dto.VmAvailabilityResponse;
import gj.cloud.vm.application.vm.dto.VmCreateRequest;
import gj.cloud.vm.application.vm.dto.VmPlanUpdateRequest;
import gj.cloud.vm.application.vm.dto.VmPowerRequest;
import gj.cloud.vm.application.vm.dto.VmResponse;
import gj.cloud.vm.application.vm.dto.VmMetricsCurrentResponse;
import gj.cloud.vm.application.vm.dto.VmMetricsHistoryResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface VmService {
    Mono<VmResponse> createVm(String userId, String bearerToken, VmCreateRequest request);
    Mono<VmResponse> createVmWithEmail(String userId, String bearerToken, VmCreateRequest request, String ownerEmail);
    Flux<VmResponse> getVms(String userId);
    Mono<VmResponse> getVm(String userId, UUID vmId);
    Mono<Void> deleteVm(String userId, UUID vmId);
    Flux<VmResponse> getAllVms();
    Mono<VmResponse> getVmAdmin(UUID vmId);
    Mono<Void> forceDeleteVm(UUID vmId);
    Mono<VmResponse> changePower(String userId, UUID vmId, VmPowerRequest request);
    Mono<VmResponse> updatePlan(String userId, UUID vmId, VmPlanUpdateRequest request);
    Mono<VmAvailabilityResponse> getAvailability();
    Mono<List<String>> getSshAccessEmails(String userId, UUID vmId);
    Mono<List<String>> addSshAccessEmail(String userId, String ownerEmail, UUID vmId, String email);
    Mono<List<String>> removeSshAccessEmail(String userId, String ownerEmail, UUID vmId, String email);
    Mono<VmMetricsCurrentResponse> getVmMetricsCurrent(String userId, UUID vmId);
    Mono<VmMetricsHistoryResponse> getVmMetricsHistory(String userId, UUID vmId, String timeframe);
}
