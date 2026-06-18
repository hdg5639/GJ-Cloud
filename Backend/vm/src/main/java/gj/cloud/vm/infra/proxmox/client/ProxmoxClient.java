package gj.cloud.vm.infra.proxmox.client;

import com.fasterxml.jackson.databind.JsonNode;
import gj.cloud.vm.domain.vm.enums.PlanType;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import gj.cloud.vm.infra.proxmox.config.ProxmoxProperties;
import gj.cloud.vm.infra.proxmox.record.ProxmoxTaskStatus;
import gj.cloud.vm.infra.proxmox.record.VmCreate;
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

        return proxmoxWebClient.post()
                .uri("/nodes/{node}/qemu/{vmid}/clone", props.getNode(), templateVmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(cloneParams))
                .exchangeToMono(res -> {
                    log.info("Proxmox 클론 HTTP status={}, headers={}", res.statusCode(), res.headers().asHttpHeaders());
                    return res.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(rawBody -> {
                                log.info("Proxmox 클론 raw body={}", rawBody);
                                if (!res.statusCode().is2xxSuccessful()) {
                                    log.error("Proxmox 클론 오류 응답: status={}, body={}", res.statusCode(), rawBody);
                                    return Mono.error(new VmException(VmErrorCode.PROXMOX_CLONE_FAILED));
                                }
                                try {
                                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                    JsonNode json = mapper.readTree(rawBody);
                                    String taskId = json.path("data").asText();
                                    if (taskId == null || taskId.isEmpty() || "null".equals(taskId)) {
                                        log.error("Proxmox 클론 응답에 taskId 없음: vmid={}, body={}", newVmid, rawBody);
                                        return Mono.error(new VmException(VmErrorCode.PROXMOX_CLONE_FAILED));
                                    }
                                    return Mono.just(taskId);
                                } catch (Exception e) {
                                    log.error("Proxmox 클론 응답 파싱 실패: body={}, error={}", rawBody, e.getMessage());
                                    return Mono.error(new VmException(VmErrorCode.PROXMOX_CLONE_FAILED));
                                }
                            });
                })
                .doOnSubscribe(s -> log.info("VM 클론 시작: vmid={}, templateVmid={}, plan={}", newVmid, templateVmid, planType))
                .doOnSuccess(t -> log.info("VM 클론 태스크 등록: vmid={}, taskId={}", newVmid, t))
                .doOnError(e -> log.error("VM 클론 실패: vmid={}, error={}", newVmid, e.getMessage()));
    }

    public Mono<Void> configureVm(int vmid, PlanType planType, String sshPublicKey) {
        VmCreate config = VmCreate.from(planType, vmid, null, sshPublicKey,
                props.getBridge(), props.getStorage());

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
                .bodyToMono(JsonNode.class)
                .map(json -> new ProxmoxTaskStatus(
                        json.path("data").path("status").asText(),
                        json.path("data").path("exitstatus").asText("")
                ));
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
                .bodyToMono(JsonNode.class)
                .flatMap(json -> {
                    Optional<String> ipOpt = StreamSupport
                            .stream(json.path("data").path("result").spliterator(), false)
                            .filter(n -> "eth0".equals(n.path("name").asText()))
                            .flatMap(n -> StreamSupport.stream(n.path("ip-addresses").spliterator(), false))
                            .filter(n -> "ipv4".equals(n.path("ip-address-type").asText()))
                            .map(n -> n.path("ip-address").asText())
                            .findFirst();

                    return Mono.justOrEmpty(ipOpt)
                            .switchIfEmpty(Mono.error(new IllegalStateException("eth0 IPv4 not found")));
                });
    }

    public Mono<Void> deleteVm(int vmid) {
        return proxmoxWebClient.delete()
                .uri("/nodes/{node}/qemu/{vmid}", props.getNode(), vmid)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSubscribe(s -> log.info("VM 삭제 요청: vmid={}", vmid))
                .doOnSuccess(v -> log.info("VM 삭제 완료: vmid={}", vmid))
                .doOnError(e -> log.error("VM 삭제 실패: vmid={}, error={}", vmid, e.getMessage()))
                .then();
    }
}
