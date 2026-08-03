package gj.cloud.vm.infra.cloudflare.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IngressRule(String hostname, String service) {}
