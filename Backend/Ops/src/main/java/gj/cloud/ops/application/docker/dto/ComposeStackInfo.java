package gj.cloud.ops.application.docker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// `docker compose ls --format json`은 ps/images와 달리 JSON 배열 하나로 반환됨(JSON-lines 아님)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ComposeStackInfo(
        @JsonProperty("Name") String name,
        @JsonProperty("Status") String status,
        @JsonProperty("ConfigFiles") String configFiles
) {
}
