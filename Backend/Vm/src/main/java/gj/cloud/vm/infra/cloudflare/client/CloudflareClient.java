package gj.cloud.vm.infra.cloudflare.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gj.cloud.vm.global.exception.VmException;
import gj.cloud.vm.global.exception.enums.VmErrorCode;
import gj.cloud.vm.infra.cloudflare.config.CloudflareProperties;
import gj.cloud.vm.infra.cloudflare.dto.IngressRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloudflareClient {

    private static final int CLOUDFLARE_MAX_ATTEMPTS = 4;
    private static final Duration CLOUDFLARE_RETRY_BACKOFF = Duration.ofSeconds(1);

    private final WebClient cloudflareWebClient;
    private final CloudflareProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String authHeader() {
        return "Bearer " + props.getApiToken();
    }

    public Mono<String> registerCname(String subdomain) {
        return ensureCname(subdomain).map(CnameRegistration::recordId);
    }

    public Mono<CnameRegistration> ensureCname(String subdomain) {
        String fqdn = subdomain + "." + props.getBaseDomain();
        Map<String, Object> body = Map.of(
                "type", "CNAME",
                "name", fqdn,
                "content", props.getTunnelId() + ".cfargotunnel.com",
                "proxied", true,
                "ttl", 1
        );
        Mono<CnameRegistration> request = cloudflareWebClient.post()
                .uri("/zones/{zoneId}/dns_records", props.getZoneId())
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(res -> res.bodyToMono(String.class).defaultIfEmpty("").flatMap(raw -> {
                    if (!res.statusCode().is2xxSuccessful()) {
                        log.error("CNAME 등록 실패: status={}, body={}", res.statusCode(), raw);
                        if (isDuplicateDnsRecord(raw)) {
                            return findExistingTunnelCnameRecordId(subdomain)
                                    .doOnNext(recordId -> log.info(
                                            "기존 Tunnel CNAME을 재사용: subdomain={}, recordId={}",
                                            subdomain, recordId))
                                    .map(recordId -> new CnameRegistration(recordId, false))
                                    .switchIfEmpty(Mono.error(
                                            new VmException(VmErrorCode.SUBDOMAIN_ALREADY_TAKEN)));
                        }
                        return Mono.error(new CloudflareHttpException(res.statusCode().value()));
                    }
                    try {
                        String recordId = objectMapper.readTree(raw).path("result").path("id").asText();
                        return Mono.just(new CnameRegistration(recordId, true));
                    } catch (Exception e) {
                        return Mono.error(new VmException(VmErrorCode.CLOUDFLARE_ERROR));
                    }
                }));
        return withTransientRetry("CNAME 등록", request)
                .doOnSuccess(result -> log.info(
                        "CNAME 준비 완료: subdomain={}, recordId={}, created={}",
                        subdomain, result.recordId(), result.created()))
                .doOnError(e -> log.error("CNAME 등록 실패: subdomain={}, error={}", subdomain, e.getMessage()));
    }

    public Mono<List<IngressRule>> getIngressRules() {
        Mono<List<IngressRule>> request = cloudflareWebClient.get()
                .uri("/accounts/{accountId}/cfd_tunnel/{tunnelId}/configurations",
                        props.getAccountId(), props.getTunnelId())
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(String.class)
                .map(raw -> {
                    try {
                        JsonNode ingress = objectMapper.readTree(raw)
                                .path("result").path("config").path("ingress");
                        List<IngressRule> rules = new ArrayList<>();
                        ingress.forEach(n -> rules.add(
                                new IngressRule(
                                        n.path("hostname").asText(null),
                                        n.path("service").asText()
                                )
                        ));
                        return rules;
                    } catch (Exception e) {
                        throw new VmException(VmErrorCode.CLOUDFLARE_ERROR);
                    }
                });
        return withTransientRetry("Tunnel ingress 조회", request);
    }

    private Mono<Void> putIngressRules(List<IngressRule> rules) {
        try {
            ObjectNode config = objectMapper.createObjectNode();
            ArrayNode ingressArray = config.putArray("ingress");
            for (IngressRule rule : rules) {
                ObjectNode ruleNode = ingressArray.addObject();
                if (rule.hostname() != null && !rule.hostname().isEmpty()) {
                    ruleNode.put("hostname", rule.hostname());
                }
                ruleNode.put("service", rule.service());
            }

            ObjectNode payload = objectMapper.createObjectNode();
            payload.set("config", config);

            Mono<Void> request = cloudflareWebClient.put()
                    .uri("/accounts/{accountId}/cfd_tunnel/{tunnelId}/configurations",
                            props.getAccountId(), props.getTunnelId())
                    .header(HttpHeaders.AUTHORIZATION, authHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload.toString())
                    .retrieve()
                    .bodyToMono(Void.class)
                    .then();
            return withTransientRetry("Tunnel ingress 갱신", request);
        } catch (Exception e) {
            return Mono.error(new VmException(VmErrorCode.CLOUDFLARE_ERROR));
        }
    }

    public Mono<Void> addIngressRule(String subdomain, String internalIp) {
        return addIngressRule(subdomain, internalIp, 22, "ssh");
    }

    public Mono<Void> addIngressRule(String subdomain, String internalIp, int port, String protocol) {
        String fqdn = subdomain + "." + props.getBaseDomain();
        String service = buildService(protocol, internalIp, port);
        return getIngressRules().flatMap(rules -> {
            List<IngressRule> updated = new ArrayList<>();
            List<IngressRule> catchAll = new ArrayList<>();
            for (IngressRule r : rules) {
                if (r.hostname() == null || r.hostname().isEmpty()) {
                    catchAll.add(r);
                } else if (!fqdn.equals(r.hostname())) {
                    updated.add(r);
                }
            }
            // 같은 호스트 규칙이 이미 있으면 교체한다. 네트워크 응답 유실 뒤 재시도해도 ingress가 중복되지 않는다.
            updated.add(new IngressRule(fqdn, service));
            updated.addAll(catchAll);
            return putIngressRules(updated);
        })
        .doOnSuccess(v -> log.info("Ingress 규칙 추가: subdomain={}, service={}", subdomain, service))
        .doOnError(e -> log.error("Ingress 규칙 추가 실패: subdomain={}, error={}", subdomain, e.getMessage()));
    }

    private String buildService(String protocol, String ip, int port) {
        return switch (protocol.toLowerCase()) {
            case "ssh"  -> "ssh://" + ip + ":" + port;
            case "http" -> "http://" + ip + ":" + port;
            case "tcp"  -> "tcp://" + ip + ":" + port;
            default     -> throw new VmException(VmErrorCode.CLOUDFLARE_ERROR);
        };
    }

    public Mono<Void> removeIngressRule(String subdomain) {
        String fqdn = subdomain + "." + props.getBaseDomain();
        return getIngressRules().flatMap(rules -> {
            List<IngressRule> updated = rules.stream()
                    .filter(r -> !fqdn.equals(r.hostname()))
                    .toList();
            return putIngressRules(updated);
        })
        .doOnSuccess(v -> log.info("Ingress 규칙 제거: subdomain={}", subdomain))
        .doOnError(e -> log.error("Ingress 규칙 제거 실패: subdomain={}, error={}", subdomain, e.getMessage()));
    }

    public Mono<String> createAccessApp(String subdomain) {
        return createAccessApp(subdomain, "ssh");
    }

    public Mono<String> createAccessApp(String subdomain, String appType) {
        String fqdn = subdomain + "." + props.getBaseDomain();
        Map<String, Object> body = Map.of(
                "name", subdomain,
                "domain", fqdn,
                "type", appType,
                "session_duration", "24h"
        );
        Mono<String> request = cloudflareWebClient.post()
                .uri("/accounts/{accountId}/access/apps", props.getAccountId())
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(raw -> {
                    try {
                        return objectMapper.readTree(raw).path("result").path("id").asText();
                    } catch (Exception e) {
                        throw new VmException(VmErrorCode.CLOUDFLARE_ERROR);
                    }
                });
        // POST 응답이 유실되면 서버에서는 생성됐을 수 있으므로 무조건 재시도하지 않는다.
        // 중복 Access App을 만드는 것보다 상위 프로비저닝을 실패시켜 알려진 리소스를 보상하는 편이 안전하다.
        return request
                .doOnSuccess(id -> log.info("Access App 생성: subdomain={}, appId={}", subdomain, id))
                .doOnError(e -> log.error("Access App 생성 실패: subdomain={}, error={}", subdomain, e.getMessage()));
    }

    public Mono<String> createAccessPolicy(String appId, List<String> emails) {
        List<Map<String, Object>> include = emails.stream()
                .map(email -> Map.<String, Object>of("email", Map.of("email", email)))
                .toList();
        Map<String, Object> body = Map.of(
                "name", "allowed-users",
                "decision", "allow",
                "include", include
        );
        Mono<String> request = cloudflareWebClient.post()
                .uri("/accounts/{accountId}/access/apps/{appId}/policies",
                        props.getAccountId(), appId)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(raw -> {
                    try {
                        return objectMapper.readTree(raw).path("result").path("id").asText();
                    } catch (Exception e) {
                        throw new VmException(VmErrorCode.CLOUDFLARE_ERROR);
                    }
                });
        // 생성 POST는 멱등성을 보장할 수 없어 자동 재시도 대상에서 제외한다.
        return request
                .doOnSuccess(id -> log.info("Access Policy 생성: appId={}, policyId={}, emails={}", appId, id, emails.size()))
                .doOnError(e -> log.error("Access Policy 생성 실패: appId={}, error={}", appId, e.getMessage()));
    }

    public Mono<Void> updateAccessPolicy(String appId, String policyId, List<String> emails) {
        List<Map<String, Object>> include = emails.stream()
                .map(email -> Map.<String, Object>of("email", Map.of("email", email)))
                .toList();
        Map<String, Object> body = Map.of(
                "name", "allowed-users",
                "decision", "allow",
                "include", include
        );
        Mono<Void> request = cloudflareWebClient.put()
                .uri("/accounts/{accountId}/access/apps/{appId}/policies/{policyId}",
                        props.getAccountId(), appId, policyId)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .then();
        return withTransientRetry("Access Policy 갱신", request)
                .doOnSuccess(v -> log.info("Access Policy 갱신: appId={}, policyId={}, emails={}", appId, policyId, emails.size()))
                .doOnError(e -> log.error("Access Policy 갱신 실패: appId={}, error={}", appId, e.getMessage()));
    }

    public Mono<Void> deleteAccessPolicy(String appId, String policyId) {
        Mono<Void> request = cloudflareWebClient.delete()
                .uri("/accounts/{accountId}/access/apps/{appId}/policies/{policyId}",
                        props.getAccountId(), appId, policyId)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(Void.class)
                .then();
        return withTransientRetry("Access Policy 삭제", request)
                .doOnSuccess(v -> log.info("Access Policy 삭제: appId={}, policyId={}", appId, policyId))
                .onErrorResume(e -> {
                    log.warn("Access Policy 삭제 실패 (무시): appId={}, error={}", appId, e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> deleteAccessApp(String appId) {
        Mono<Void> request = cloudflareWebClient.delete()
                .uri("/accounts/{accountId}/access/apps/{appId}",
                        props.getAccountId(), appId)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(Void.class)
                .then();
        return withTransientRetry("Access App 삭제", request)
                .doOnSuccess(v -> log.info("Access App 삭제: appId={}", appId))
                .onErrorResume(e -> {
                    log.warn("Access App 삭제 실패 (무시): appId={}, error={}", appId, e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> deleteCname(String dnsRecordId) {
        Mono<Void> request = cloudflareWebClient.delete()
                .uri("/zones/{zoneId}/dns_records/{recordId}",
                        props.getZoneId(), dnsRecordId)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(Void.class)
                .then();
        return withTransientRetry("CNAME 삭제", request)
                .doOnSuccess(v -> log.info("CNAME 삭제: recordId={}", dnsRecordId))
                .onErrorResume(e -> {
                    log.warn("CNAME 삭제 실패 (무시): recordId={}, error={}", dnsRecordId, e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> deleteCnameStrict(String dnsRecordId) {
        Mono<Void> request = cloudflareWebClient.delete()
                .uri("/zones/{zoneId}/dns_records/{recordId}", props.getZoneId(), dnsRecordId)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful() || response.statusCode().value() == 404) {
                        return response.releaseBody();
                    }
                    return response.bodyToMono(String.class).defaultIfEmpty("")
                            .flatMap(body -> Mono.error(new VmException(VmErrorCode.CLOUDFLARE_ERROR)));
                });
        return withTransientRetry("CNAME 삭제", request);
    }

    private boolean isDuplicateDnsRecord(String responseBody) {
        return responseBody != null
                && (responseBody.contains("\"code\":81057")
                || responseBody.toLowerCase().contains("already exists"));
    }

    private Mono<String> findExistingTunnelCnameRecordId(String subdomain) {
        String fqdn = subdomain + "." + props.getBaseDomain();
        String expectedContent = props.getTunnelId() + ".cfargotunnel.com";
        Mono<String> request = cloudflareWebClient.get()
                .uri(uri -> uri.path("/zones/{zoneId}/dns_records")
                        .queryParam("type", "CNAME")
                        .queryParam("name", fqdn)
                        .build(props.getZoneId()))
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(raw -> {
                    try {
                        JsonNode result = objectMapper.readTree(raw).path("result");
                        if (!result.isArray()) return Mono.empty();
                        for (JsonNode record : result) {
                            if (fqdn.equals(record.path("name").asText())
                                    && expectedContent.equals(record.path("content").asText())) {
                                String recordId = record.path("id").asText("");
                                if (!recordId.isBlank()) return Mono.just(recordId);
                            }
                        }
                        return Mono.empty();
                    } catch (Exception error) {
                        return Mono.error(new VmException(VmErrorCode.CLOUDFLARE_ERROR));
                    }
                });
        return withTransientRetry("기존 CNAME 조회", request);
    }

    private <T> Mono<T> withTransientRetry(String operation, Mono<T> request) {
        return request
                .retryWhen(Retry.backoff(CLOUDFLARE_MAX_ATTEMPTS - 1, CLOUDFLARE_RETRY_BACKOFF)
                        .maxBackoff(Duration.ofSeconds(4))
                        .filter(this::isTransientFailure)
                        .doBeforeRetry(signal -> log.warn(
                                "Cloudflare 일시 오류 재시도: operation={}, attempt={}/{}, error={}",
                                operation,
                                signal.totalRetries() + 2,
                                CLOUDFLARE_MAX_ATTEMPTS,
                                signal.failure().getMessage())))
                .onErrorMap(error -> !(error instanceof VmException),
                        error -> new VmException(VmErrorCode.CLOUDFLARE_ERROR));
    }

    private boolean isTransientFailure(Throwable error) {
        if (error instanceof WebClientRequestException) {
            return true;
        }
        if (error instanceof WebClientResponseException responseError) {
            return responseError.getStatusCode().value() == 429
                    || responseError.getStatusCode().is5xxServerError();
        }
        if (error instanceof CloudflareHttpException httpError) {
            return httpError.statusCode == 429 || httpError.statusCode >= 500;
        }
        return false;
    }

    private static final class CloudflareHttpException extends RuntimeException {
        private final int statusCode;

        private CloudflareHttpException(int statusCode) {
            super("Cloudflare HTTP " + statusCode);
            this.statusCode = statusCode;
        }
    }

    public record CnameRegistration(String recordId, boolean created) {
    }
}
