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
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloudflareClient {

    private final WebClient cloudflareWebClient;
    private final CloudflareProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String authHeader() {
        return "Bearer " + props.getApiToken();
    }

    public Mono<String> registerCname(String subdomain) {
        String fqdn = subdomain + "." + props.getBaseDomain();
        Map<String, Object> body = Map.of(
                "type", "CNAME",
                "name", fqdn,
                "content", props.getTunnelId() + ".cfargotunnel.com",
                "proxied", true,
                "ttl", 1
        );
        return cloudflareWebClient.post()
                .uri("/zones/{zoneId}/dns_records", props.getZoneId())
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(res -> res.bodyToMono(String.class).defaultIfEmpty("").flatMap(raw -> {
                    if (!res.statusCode().is2xxSuccessful()) {
                        log.error("CNAME 등록 실패: status={}, body={}", res.statusCode(), raw);
                        return Mono.error(new VmException(VmErrorCode.CLOUDFLARE_ERROR));
                    }
                    try {
                        return Mono.just(objectMapper.readTree(raw).path("result").path("id").asText());
                    } catch (Exception e) {
                        return Mono.error(new VmException(VmErrorCode.CLOUDFLARE_ERROR));
                    }
                }))
                .doOnSuccess(id -> log.info("CNAME 등록 완료: subdomain={}, recordId={}", subdomain, id))
                .doOnError(e -> log.error("CNAME 등록 실패: subdomain={}, error={}", subdomain, e.getMessage()));
    }

    public Mono<List<IngressRule>> getIngressRules() {
        return cloudflareWebClient.get()
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

            return cloudflareWebClient.put()
                    .uri("/accounts/{accountId}/cfd_tunnel/{tunnelId}/configurations",
                            props.getAccountId(), props.getTunnelId())
                    .header(HttpHeaders.AUTHORIZATION, authHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload.toString())
                    .retrieve()
                    .bodyToMono(Void.class)
                    .then();
        } catch (Exception e) {
            return Mono.error(new VmException(VmErrorCode.CLOUDFLARE_ERROR));
        }
    }

    public Mono<Void> addIngressRule(String subdomain, String internalIp) {
        String fqdn = subdomain + "." + props.getBaseDomain();
        return getIngressRules().flatMap(rules -> {
            List<IngressRule> updated = new ArrayList<>();
            // existing rules except catch-all
            List<IngressRule> catchAll = new ArrayList<>();
            for (IngressRule r : rules) {
                if (r.hostname() == null || r.hostname().isEmpty()) {
                    catchAll.add(r);
                } else {
                    updated.add(r);
                }
            }
            updated.add(new IngressRule(fqdn, "ssh://" + internalIp + ":22"));
            updated.addAll(catchAll);
            return putIngressRules(updated);
        })
        .doOnSuccess(v -> log.info("Ingress 규칙 추가: subdomain={}, ip={}", subdomain, internalIp))
        .doOnError(e -> log.error("Ingress 규칙 추가 실패: subdomain={}, error={}", subdomain, e.getMessage()));
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
        String fqdn = subdomain + "." + props.getBaseDomain();
        Map<String, Object> body = Map.of(
                "name", subdomain,
                "domain", fqdn,
                "type", "ssh",
                "session_duration", "24h"
        );
        return cloudflareWebClient.post()
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
                })
                .doOnSuccess(id -> log.info("Access App 생성: subdomain={}, appId={}", subdomain, id))
                .doOnError(e -> log.error("Access App 생성 실패: subdomain={}, error={}", subdomain, e.getMessage()));
    }

    public Mono<String> createAccessPolicy(String appId, String ownerEmail) {
        Map<String, Object> include = Map.of("email", Map.of("email", ownerEmail));
        Map<String, Object> body = Map.of(
                "name", "owner-only",
                "decision", "allow",
                "include", List.of(include)
        );
        return cloudflareWebClient.post()
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
                })
                .doOnSuccess(id -> log.info("Access Policy 생성: appId={}, policyId={}", appId, id))
                .doOnError(e -> log.error("Access Policy 생성 실패: appId={}, error={}", appId, e.getMessage()));
    }

    public Mono<Void> deleteAccessPolicy(String appId, String policyId) {
        return cloudflareWebClient.delete()
                .uri("/accounts/{accountId}/access/apps/{appId}/policies/{policyId}",
                        props.getAccountId(), appId, policyId)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(Void.class)
                .then()
                .doOnSuccess(v -> log.info("Access Policy 삭제: appId={}, policyId={}", appId, policyId))
                .onErrorResume(e -> {
                    log.warn("Access Policy 삭제 실패 (무시): appId={}, error={}", appId, e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> deleteAccessApp(String appId) {
        return cloudflareWebClient.delete()
                .uri("/accounts/{accountId}/access/apps/{appId}",
                        props.getAccountId(), appId)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(Void.class)
                .then()
                .doOnSuccess(v -> log.info("Access App 삭제: appId={}", appId))
                .onErrorResume(e -> {
                    log.warn("Access App 삭제 실패 (무시): appId={}, error={}", appId, e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> deleteCname(String dnsRecordId) {
        return cloudflareWebClient.delete()
                .uri("/zones/{zoneId}/dns_records/{recordId}",
                        props.getZoneId(), dnsRecordId)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(Void.class)
                .then()
                .doOnSuccess(v -> log.info("CNAME 삭제: recordId={}", dnsRecordId))
                .onErrorResume(e -> {
                    log.warn("CNAME 삭제 실패 (무시): recordId={}, error={}", dnsRecordId, e.getMessage());
                    return Mono.empty();
                });
    }
}
