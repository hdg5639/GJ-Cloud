package gj.cloud.vm.infra.proxmox.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import gj.cloud.vm.domain.vm.enums.PlanType;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import gj.cloud.vm.infra.proxmox.config.ProxmoxProperties;
import gj.cloud.vm.infra.proxmox.record.ProxmoxConfigInfo;
import gj.cloud.vm.infra.proxmox.record.ProxmoxStatusInfo;
import gj.cloud.vm.infra.proxmox.record.ProxmoxTaskStatus;
import gj.cloud.vm.infra.proxmox.record.VmCreate;
import gj.cloud.vm.infra.proxmox.record.GuestAgentDiskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProxmoxClient {

    private final WebClient proxmoxWebClient;
    private final ProxmoxProperties props;

    private String authHeader() {
        return "PVEAPIToken=" + props.getTokenId() + "=" + props.getTokenSecret();
    }

    public Mono<String> cloneVm(int newVmid, PlanType planType, String name) {
        int templateVmid = planType.getTemplateVmid();

        MultiValueMap<String, String> cloneParams = new LinkedMultiValueMap<>();
        cloneParams.add("newid", String.valueOf(newVmid));
        cloneParams.add("name", name);
        cloneParams.add("full", "1");
        cloneParams.add("pool", props.getPool());

        return proxmoxWebClient.post()
                .uri("/nodes/{node}/qemu/{vmid}/clone", props.getNode(), templateVmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(cloneParams))
                .exchangeToMono(res -> res.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(rawBody -> {
                            if (!res.statusCode().is2xxSuccessful()) {
                                log.error("Proxmox 클론 실패: status={}, body={}", res.statusCode(), rawBody);
                                return Mono.error(new VmException(VmErrorCode.PROXMOX_CLONE_FAILED));
                            }
                            try {
                                ObjectMapper mapper = new ObjectMapper();
                                JsonNode json = mapper.readTree(rawBody);
                                String taskId = json.path("data").asText();
                                if (taskId == null || taskId.isEmpty() || "null".equals(taskId)) {
                                    log.error("Proxmox 클론 응답에 taskId 없음: vmid={}", newVmid);
                                    return Mono.error(new VmException(VmErrorCode.PROXMOX_CLONE_FAILED));
                                }
                                return Mono.just(taskId);
                            } catch (Exception e) {
                                log.error("Proxmox 클론 응답 파싱 실패: {}", e.getMessage());
                                return Mono.error(new VmException(VmErrorCode.PROXMOX_CLONE_FAILED));
                            }
                        })
                )
                .doOnSubscribe(s -> log.info("VM 클론 시작: vmid={}, templateVmid={}, plan={}", newVmid, templateVmid, planType))
                .doOnSuccess(t -> log.info("VM 클론 태스크 등록: vmid={}, taskId={}", newVmid, t))
                .doOnError(e -> log.error("VM 클론 실패: vmid={}, error={}", newVmid, e.getMessage()));
    }

    public Mono<Void> configureVm(int vmid, PlanType planType, String sshPublicKey, String staticIp) {
        VmCreate config = VmCreate.from(planType, vmid, null, sshPublicKey,
                props.getBridge(), props.getStorage(), staticIp);

        MultiValueMap<String, String> configParams = new LinkedMultiValueMap<>();
        configParams.add("cores", String.valueOf(config.getCores()));
        configParams.add("memory", config.getMemory());
        configParams.add("cpu", config.getCpu());
        configParams.add("sshkeys", UriUtils.encode(config.getSshkeys(), "UTF-8"));
        configParams.add("ipconfig0", config.getIpconfig0());

        if (config.getCpulimit() > 0) {
            configParams.add("cpulimit", String.valueOf(config.getCpulimit()));
        }
        if (config.getCpuunits() != 1024) {
            configParams.add("cpuunits", String.valueOf(config.getCpuunits()));
        }

        return proxmoxWebClient.put()
                .uri("/nodes/{node}/qemu/{vmid}/config", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(configParams))
                .exchangeToMono(res -> {
                    if (res.statusCode().is2xxSuccessful()) {
                        return res.bodyToMono(Void.class);
                    }
                    return res.bodyToMono(String.class).flatMap(body -> {
                        log.error("Proxmox config 오류: status={}, body={}", res.statusCode(), body);
                        return Mono.error(new VmException(VmErrorCode.PROXMOX_CLONE_FAILED));
                    });
                })
                .doOnSuccess(v -> log.info("VM config 완료: vmid={}", vmid))
                .doOnError(e -> log.error("VM config 실패: vmid={}, error={}", vmid, e.getMessage()))
                .then();
    }

    public Mono<Void> waitForTaskCompletion(String upid) {
        return Mono.defer(() -> checkTaskStatus(upid))
                .filter(ProxmoxTaskStatus::isCompleted)
                .repeatWhenEmpty(count -> count.delayElements(Duration.ofSeconds(3)))
                .timeout(Duration.ofMinutes(5))
                .flatMap(taskStatus -> taskStatus.isSuccess()
                        ? Mono.empty()
                        : Mono.error(new VmException(VmErrorCode.PROXMOX_CLONE_FAILED)))
                .then();
    }

    private Mono<ProxmoxTaskStatus> checkTaskStatus(String upid) {
        return proxmoxWebClient.get()
                .uri("/nodes/{node}/tasks/{upid}/status", props.getNode(), upid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode json = mapper.readTree(body);
                        return new ProxmoxTaskStatus(
                                json.path("data").path("status").asText(),
                                json.path("data").path("exitstatus").asText("")
                        );
                    } catch (Exception e) {
                        throw new VmException(VmErrorCode.PROXMOX_CLONE_FAILED);
                    }
                });
    }

    public Mono<Void> startVm(int vmid) {
        return proxmoxWebClient.post()
                .uri("/nodes/{node}/qemu/{vmid}/status/start", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSubscribe(s -> log.info("VM 시작: vmid={}", vmid))
                .then();
    }

    public Mono<Void> stopVm(int vmid) {
        return proxmoxWebClient.post()
                .uri("/nodes/{node}/qemu/{vmid}/status/stop", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        String taskId = new ObjectMapper().readTree(body).path("data").asText();
                        return waitForTaskCompletion(taskId);
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })
                .doOnSubscribe(s -> log.info("VM 정지: vmid={}", vmid))
                .then();
    }

    public Mono<Void> suspendVm(int vmid) {
        return proxmoxWebClient.post()
                .uri("/nodes/{node}/qemu/{vmid}/status/suspend", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        String taskId = new ObjectMapper().readTree(body).path("data").asText();
                        return waitForTaskCompletion(taskId);
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })
                .doOnSubscribe(s -> log.info("VM 일시정지: vmid={}", vmid))
                .then();
    }

    public Mono<String> waitForIpAssignment(int vmid) {
        return Mono.delay(Duration.ofSeconds(20))
                .then(getVmIp(vmid)
                        .retryWhen(Retry.backoff(5, Duration.ofSeconds(2)))
                        .timeout(Duration.ofSeconds(60)))
                .doOnSuccess(ip -> log.info("IP 획득: vmid={}, ip={}", vmid, ip))
                .doOnError(e -> log.error("IP 획득 실패: vmid={}", vmid));
    }

    private Mono<String> getVmIp(int vmid) {
        return proxmoxWebClient.get()
                .uri("/nodes/{node}/qemu/{vmid}/agent/network-get-interfaces", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode json = mapper.readTree(body);
                        Optional<String> ipOpt = StreamSupport
                                .stream(json.path("data").path("result").spliterator(), false)
                                .filter(n -> "eth0".equals(n.path("name").asText()))
                                .flatMap(n -> StreamSupport.stream(n.path("ip-addresses").spliterator(), false))
                                .filter(n -> "ipv4".equals(n.path("ip-address-type").asText()))
                                .map(n -> n.path("ip-address").asText())
                                .findFirst();
                        return Mono.justOrEmpty(ipOpt)
                                .switchIfEmpty(Mono.error(new IllegalStateException("eth0 IPv4 not found")));
                    } catch (Exception e) {
                        return Mono.error(new IllegalStateException("IP 파싱 실패: " + e.getMessage()));
                    }
                });
    }

    public Mono<Void> ensurePoolExists(String poolId) {
        return proxmoxWebClient.get()
                .uri("/pools/{poolId}", poolId)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .exchangeToMono(res -> {
                    if (res.statusCode().is2xxSuccessful()) {
                        return res.bodyToMono(Void.class).then(Mono.<Void>empty());
                    }
                    return res.bodyToMono(Void.class).then(
                            proxmoxWebClient.post()
                                    .uri("/pools")
                                    .header(HttpHeaders.AUTHORIZATION, authHeader())
                                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                    .body(BodyInserters.fromFormData("poolid", poolId))
                                    .exchangeToMono(createRes -> createRes.bodyToMono(Void.class).then(Mono.<Void>empty()))
                                    .doOnSuccess(v -> log.info("Proxmox pool 생성: {}", poolId))
                                    .onErrorResume(e -> {
                                        log.warn("Pool 생성 실패 (이미 존재할 수 있음): poolId={}, error={}", poolId, e.getMessage());
                                        return Mono.empty();
                                    })
                    );
                });
    }

    public Mono<Set<Integer>> getProxmoxVmids() {
        return proxmoxWebClient.get()
                .uri("/nodes/{node}/qemu", props.getNode())
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode data = mapper.readTree(body).path("data");
                        Set<Integer> vmids = new java.util.HashSet<>();
                        data.forEach(vm -> vmids.add(vm.path("vmid").asInt()));
                        log.debug("Proxmox 기존 vmid 목록: {}", vmids);
                        return vmids;
                    } catch (Exception e) {
                        log.warn("Proxmox vmid 목록 조회 실패: {}", e.getMessage());
                        return new HashSet<>();
                    }
                });
    }

    public Mono<Void> resizeDisk(int vmid, int targetDiskGb) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("disk", "scsi0");
        params.add("size", targetDiskGb + "G");

        return proxmoxWebClient.put()
                .uri("/nodes/{node}/qemu/{vmid}/resize", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(params))
                .exchangeToMono(res -> res.statusCode().is2xxSuccessful()
                        ? res.bodyToMono(Void.class).then(Mono.<Void>empty())
                        : res.bodyToMono(String.class).flatMap(body -> {
                            log.error("디스크 리사이즈 실패: vmid={}, status={}, body={}", vmid, res.statusCode(), body);
                            return Mono.error(new VmException(VmErrorCode.PROXMOX_CLONE_FAILED));
                        }))
                .doOnSuccess(v -> log.info("디스크 리사이즈 완료: vmid={}, size={}G", vmid, targetDiskGb))
                .doOnError(e -> log.error("디스크 리사이즈 실패: vmid={}, error={}", vmid, e.getMessage()));
    }

    public Mono<Void> rebootVm(int vmid) {
        return proxmoxWebClient.post()
                .uri("/nodes/{node}/qemu/{vmid}/status/reboot", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSubscribe(s -> log.info("VM 재시작: vmid={}", vmid))
                .then();
    }

    public Mono<Void> updateResources(int vmid, PlanType planType) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("cores", String.valueOf(planType.getCores()));
        params.add("memory", planType.getMemory());
        params.add("cpu", "host,hidden=1");
        if (planType == PlanType.FREE) {
            params.add("cpulimit", "4");
            params.add("cpuunits", "1024");
        } else {
            params.add("cpulimit", "0");
            params.add("cpuunits", "3072");
        }

        return proxmoxWebClient.put()
                .uri("/nodes/{node}/qemu/{vmid}/config", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(params))
                .exchangeToMono(res -> res.statusCode().is2xxSuccessful()
                        ? res.bodyToMono(Void.class).then(Mono.<Void>empty())
                        : res.bodyToMono(String.class).flatMap(body -> {
                            log.error("리소스 변경 실패: vmid={}, status={}, body={}", vmid, res.statusCode(), body);
                            return Mono.error(new VmException(VmErrorCode.PROXMOX_CLONE_FAILED));
                        }))
                .doOnSuccess(v -> log.info("리소스 변경 완료: vmid={}, plan={}", vmid, planType))
                .doOnError(e -> log.error("리소스 변경 실패: vmid={}, error={}", vmid, e.getMessage()));
    }

    public Mono<ProxmoxConfigInfo> getConfig(int vmid) {
        return proxmoxWebClient.get()
                .uri("/nodes/{node}/qemu/{vmid}/config", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        JsonNode data = new ObjectMapper().readTree(body).path("data");
                        return new ProxmoxConfigInfo(
                                data.path("cores").asInt(),
                                data.path("memory").asLong()
                        );
                    } catch (Exception e) {
                        throw new VmException(VmErrorCode.PROXMOX_CLONE_FAILED);
                    }
                });
    }

    public Mono<ProxmoxStatusInfo> getCurrentStatus(int vmid) {
        return proxmoxWebClient.get()
                .uri("/nodes/{node}/qemu/{vmid}/status/current", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        JsonNode data = new ObjectMapper().readTree(body).path("data");
                        return new ProxmoxStatusInfo(
                                data.path("cpus").asInt(),
                                data.path("maxmem").asLong() / (1024 * 1024)
                        );
                    } catch (Exception e) {
                        throw new VmException(VmErrorCode.PROXMOX_CLONE_FAILED);
                    }
                });
    }

    public Mono<Boolean> needsReboot(int vmid) {
        return Mono.zip(getConfig(vmid), getCurrentStatus(vmid))
                .map(tuple -> {
                    ProxmoxConfigInfo config = tuple.getT1();
                    ProxmoxStatusInfo current = tuple.getT2();
                    return config.cores() != current.cores()
                            || config.memory() != current.memory();
                })
                .onErrorReturn(false);
    }

    public Mono<Void> deleteVm(int vmid) {
        return proxmoxWebClient.post()
                .uri("/nodes/{node}/qemu/{vmid}/status/stop", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        String stopTaskId = new ObjectMapper().readTree(body).path("data").asText();
                        return waitForTaskCompletion(stopTaskId);
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.warn("VM 정지 실패 (이미 꺼져있을 수 있음): vmid={}", vmid);
                    return Mono.empty();
                })
                .then(proxmoxWebClient.delete()
                        .uri("/nodes/{node}/qemu/{vmid}", props.getNode(), vmid)
                        .header(HttpHeaders.AUTHORIZATION, authHeader())
                        .retrieve()
                        .bodyToMono(Void.class)
                        .doOnSubscribe(s -> log.info("VM 삭제 요청: vmid={}", vmid))
                        .doOnSuccess(v -> log.info("VM 삭제 완료: vmid={}", vmid))
                        .doOnError(e -> log.error("VM 삭제 실패: vmid={}, error={}", vmid, e.getMessage()))
                        .then());
    }

    public Mono<JsonNode> getVmMetricsCurrent(int vmid) {
        return proxmoxWebClient.get()
                .uri("/nodes/{node}/qemu/{vmid}/status/current", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        return new ObjectMapper().readTree(body).path("data");
                    } catch (Exception e) {
                        log.error("메트릭 현재값 파싱 실패: vmid={}, error={}", vmid, e.getMessage());
                        throw new VmException(VmErrorCode.PROXMOX_CLONE_FAILED);
                    }
                })
                .doOnError(e -> log.error("메트릭 현재값 조회 실패: vmid={}, error={}", vmid, e.getMessage()));
    }

    public Mono<JsonNode> getVmMetricsHistory(int vmid, String timeframe) {
        return proxmoxWebClient.get()
                .uri("/nodes/{node}/qemu/{vmid}/rrddata?timeframe={timeframe}&cf=average",
                        props.getNode(), vmid, timeframe)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        return new ObjectMapper().readTree(body).path("data");
                    } catch (Exception e) {
                        log.error("메트릭 히스토리 파싱 실패: vmid={}, timeframe={}, error={}",
                                vmid, timeframe, e.getMessage());
                        throw new VmException(VmErrorCode.PROXMOX_CLONE_FAILED);
                    }
                })
                .doOnError(e -> log.error("메트릭 히스토리 조회 실패: vmid={}, timeframe={}, error={}",
                        vmid, timeframe, e.getMessage()));
    }

    public Mono<GuestAgentDiskInfo> getVmDiskInfo(int vmid) {
        return proxmoxWebClient.get()
                .uri("/nodes/{node}/qemu/{vmid}/agent/get-fsinfo", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        JsonNode data = new ObjectMapper().readTree(body).path("data").path("result");
                        return new ObjectMapper().convertValue(data, GuestAgentDiskInfo.class);
                    } catch (Exception e) {
                        log.error("게스트 에이전트 디스크 정보 파싱 실패: vmid={}, error={}", vmid, e.getMessage());
                        throw new VmException(VmErrorCode.PROXMOX_CLONE_FAILED);
                    }
                })
                .onErrorReturn(new GuestAgentDiskInfo(java.util.List.of()))
                .doOnError(e -> log.warn("게스트 에이전트 디스크 정보 조회 실패: vmid={}, error={}", vmid, e.getMessage()));
    }
}
