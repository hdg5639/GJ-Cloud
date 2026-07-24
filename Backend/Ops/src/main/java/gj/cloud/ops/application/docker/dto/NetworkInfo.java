package gj.cloud.ops.application.docker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NetworkInfo(
        @JsonProperty("ID") String id,
        @JsonProperty("Name") String name,
        @JsonProperty("Driver") String driver,
        @JsonProperty("Scope") String scope
) {
}
