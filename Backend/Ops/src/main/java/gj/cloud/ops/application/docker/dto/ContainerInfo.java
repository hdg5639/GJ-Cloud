package gj.cloud.ops.application.docker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// `docker ps -a --format '{{json .}}'`의 한 줄(JSON) 그대로 매핑 — 표 형식 파싱 금지(C.3)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContainerInfo(
        @JsonProperty("ID") String id,
        @JsonProperty("Image") String image,
        @JsonProperty("Names") String names,
        @JsonProperty("Command") String command,
        @JsonProperty("Status") String status,
        @JsonProperty("State") String state,
        @JsonProperty("Ports") String ports,
        @JsonProperty("CreatedAt") String createdAt
) {
}
