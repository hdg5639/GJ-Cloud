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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
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
        return cloneVm(newVmid, PlanType.TEMPLATE_VMID, name)
                .doOnSubscribe(s -> log.info("VM 클론 시작: vmid={}, templateVmid={}, plan={}",
                        newVmid, PlanType.TEMPLATE_VMID, planType));
    }

    public Mono<String> cloneVm(int newVmid, int templateVmid, String name) {
        return cloneVm(newVmid, templateVmid, name, props.getPool());
    }

    public Mono<String> cloneVm(int newVmid, int templateVmid, String name, String pool) {

        MultiValueMap<String, String> cloneParams = new LinkedMultiValueMap<>();
        cloneParams.add("newid", String.valueOf(newVmid));
        cloneParams.add("name", name);
        cloneParams.add("full", "1");
        cloneParams.add("pool", pool);

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
                .doOnSubscribe(s -> log.info("시스템 VM 클론 시작: vmid={}, templateVmid={}", newVmid, templateVmid))
                .doOnSuccess(t -> log.info("VM 클론 태스크 등록: vmid={}, taskId={}", newVmid, t))
                .doOnError(e -> log.error("VM 클론 실패: vmid={}, error={}", newVmid, e.getMessage()));
    }

    public Mono<Void> configureVm(int vmid, PlanType planType, List<String> sshPublicKeys) {
        VmCreate config = VmCreate.from(planType, vmid, null, sshPublicKeys,
                props.getBridge(), props.getStorage());

        return configureVm(vmid, config.getCores(), Integer.parseInt(config.getMemory()),
                config.getCpulimit(), config.getCpuunits(), sshPublicKeys);
    }

    public Mono<Void> configureVm(int vmid, int cores, int memoryMb, List<String> sshPublicKeys) {
        return configureVm(vmid, cores, memoryMb, 0, 1024, sshPublicKeys);
    }

    private Mono<Void> configureVm(int vmid, int cores, int memoryMb, int cpuLimit, int cpuUnits,
                                   List<String> sshPublicKeys) {

        MultiValueMap<String, String> configParams = new LinkedMultiValueMap<>();
        configParams.add("cores", String.valueOf(cores));
        configParams.add("vcpus", String.valueOf(cores));
        configParams.add("memory", String.valueOf(memoryMb));
        configParams.add("cpu", "host,hidden=1");
        // 단일 템플릿(9026) 클론 직후에는 cloud-init이 첫 부팅 시 자동 apt upgrade를 실행하지 않도록
        // 반드시 시작 전에 ciupgrade=0을 적용해야 함 — 늦게 적용하면 이미 부팅이 진행되어 의미가 없음
        configParams.add("ciupgrade", "0");
        // 템플릿 기본 사용자에 의존하면 템플릿 교체/재생성 시 키가 다른 계정에 들어갈 수 있다.
        configParams.add("ciuser", "ubuntu");
        // Proxmox의 sshkeys 파라미터는 자체 검증기가 "urlencoded 문자열이어야 함"을 요구함 — HTTP 폼
        // 전송단의 인코딩(BodyInserters.fromFormData가 자동 처리)과는 별개로, Proxmox가 전달받는 값 자체가
        // 이미 percent-encoded 상태여야 통과함(원본 그대로 보내면 400 invalid urlencoded string 에러).
        // 그래서 여기서 한 번 인코딩한 뒤 폼 전송단에서 한 번 더 인코딩되는 이중 인코딩이 정상 동작임 — 되돌리지 말 것.
        configParams.add("sshkeys", UriUtils.encode(String.join("\n", sshPublicKeys), StandardCharsets.UTF_8));
        configParams.add("ipconfig0", "ip=dhcp");
        configParams.add("nameserver", "1.1.1.1 8.8.8.8");

        if (cpuLimit > 0) {
            configParams.add("cpulimit", String.valueOf(cpuLimit));
        }
        if (cpuUnits != 1024) {
            configParams.add("cpuunits", String.valueOf(cpuUnits));
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
                .then(verifyCloudInitConfig(vmid, sshPublicKeys))
                .doOnSuccess(v -> log.info("VM config 완료: vmid={}", vmid))
                .doOnError(e -> log.error("VM config 실패: vmid={}, error={}", vmid, e.getMessage()))
                .then();
    }

    private Mono<Void> verifyCloudInitConfig(int vmid, List<String> expectedPublicKeys) {
        return proxmoxWebClient.get()
                .uri("/nodes/{node}/qemu/{vmid}/config", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        JsonNode data = new ObjectMapper().readTree(body).path("data");
                        boolean hasCloudInitDrive = data.properties().stream()
                                .filter(entry -> entry.getKey().matches("(?:ide|sata|scsi)\\d+"))
                                .map(java.util.Map.Entry::getValue)
                                .map(JsonNode::asText)
                                .anyMatch(value -> value.contains("cloudinit"));
                        boolean hasExpectedUser = "ubuntu".equals(data.path("ciuser").asText());
                        boolean usesDhcp = "ip=dhcp".equals(data.path("ipconfig0").asText());
                        String configuredKeys = decodePercentEncoded(data.path("sshkeys").asText());
                        boolean hasAllKeys = expectedPublicKeys.stream()
                                .map(this::canonicalPublicKey)
                                .allMatch(configuredKeys::contains);

                        if (hasCloudInitDrive && hasExpectedUser && usesDhcp && hasAllKeys) {
                            return Mono.<Void>empty();
                        }
                        log.error(
                                "Proxmox cloud-init 설정 검증 실패: vmid={}, drive={}, ciuser={}, dhcp={}, sshKeyCount={}",
                                vmid, hasCloudInitDrive, hasExpectedUser, usesDhcp, expectedPublicKeys.size());
                        return Mono.error(new VmException(VmErrorCode.PROXMOX_CLONE_FAILED));
                    } catch (Exception e) {
                        log.error("Proxmox cloud-init 설정 응답 파싱 실패: vmid={}, error={}", vmid, e.getMessage());
                        return Mono.error(new VmException(VmErrorCode.PROXMOX_CLONE_FAILED));
                    }
                });
    }

    private String decodePercentEncoded(String value) {
        String decoded = value;
        for (int i = 0; i < 3 && decoded.contains("%"); i++) {
            String next = UriUtils.decode(decoded, StandardCharsets.UTF_8);
            if (next.equals(decoded)) {
                break;
            }
            decoded = next;
        }
        return decoded;
    }

    private String canonicalPublicKey(String publicKey) {
        return Stream.of(publicKey.trim().split("\\s+"))
                .limit(2)
                .reduce((left, right) -> left + " " + right)
                .orElse(publicKey);
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
        params.add("vcpus", String.valueOf(planType.getCores()));
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

    public Mono<SystemVmInfo> getSystemVmInfo(int vmid) {
        Mono<JsonNode> config = proxmoxWebClient.get()
                .uri("/nodes/{node}/qemu/{vmid}/config", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve().bodyToMono(String.class)
                .map(body -> readData(body));
        Mono<JsonNode> status = proxmoxWebClient.get()
                .uri("/nodes/{node}/qemu/{vmid}/status/current", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve().bodyToMono(String.class)
                .map(body -> readData(body));
        return Mono.zip(config, status).map(tuple -> new SystemVmInfo(
                tuple.getT1().path("name").asText(),
                tuple.getT1().path("cores").asInt(),
                tuple.getT1().path("memory").asInt(),
                parseDiskGb(tuple.getT1()),
                tuple.getT2().path("status").asText("unknown")
        ));
    }

    private int parseDiskGb(JsonNode config) {
        for (String key : List.of("scsi0", "sata0", "virtio0", "ide0")) {
            String value = config.path(key).asText("");
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:^|,)size=(\\d+)G(?:,|$)").matcher(value);
            if (matcher.find()) return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    private JsonNode readData(String body) {
        try {
            return new ObjectMapper().readTree(body).path("data");
        } catch (Exception e) {
            throw new VmException(VmErrorCode.PROXMOX_CLONE_FAILED);
        }
    }

    public record SystemVmInfo(String name, int cores, int memoryMb, int diskGb, String powerState) {}

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
                .uri(uriBuilder -> uriBuilder
                        .path("/nodes/{node}/qemu/{vmid}/rrddata")
                        .queryParam("timeframe", timeframe)
                        .queryParam("cf", "average")
                        .build(props.getNode(), vmid))
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .exchangeToMono(res -> res.bodyToMono(String.class)
                        .flatMap(body -> {
                            if (res.statusCode().is2xxSuccessful()) {
                                try {
                                    return Mono.just(new ObjectMapper().readTree(body).path("data"));
                                } catch (Exception e) {
                                    log.debug("메트릭 히스토리 파싱 실패: vmid={}, timeframe={}, error={}",
                                            vmid, timeframe, e.getMessage());
                                    return Mono.just(new ObjectMapper().createArrayNode());
                                }
                            }
                            log.debug("메트릭 히스토리 조회 실패 (Proxmox API): vmid={}, timeframe={}, status={}",
                                    vmid, timeframe, res.statusCode());
                            return Mono.just(new ObjectMapper().createArrayNode());
                        }));
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
                        if (data.isArray()) {
                            var disks = new java.util.ArrayList<GuestAgentDiskInfo.DiskEntry>();
                            for (JsonNode item : data) {
                                disks.add(new ObjectMapper().convertValue(item, GuestAgentDiskInfo.DiskEntry.class));
                            }
                            return new GuestAgentDiskInfo(disks);
                        }
                        return new GuestAgentDiskInfo(java.util.List.of());
                    } catch (Exception e) {
                        log.error("게스트 에이전트 디스크 정보 파싱 실패: vmid={}, error={}", vmid, e.getMessage());
                        return new GuestAgentDiskInfo(java.util.List.of());
                    }
                })
                .onErrorReturn(new GuestAgentDiskInfo(java.util.List.of()))
                .doOnError(e -> log.warn("게스트 에이전트 디스크 정보 조회 실패: vmid={}, error={}", vmid, e.getMessage()));
    }
}
