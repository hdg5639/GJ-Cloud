package gj.cloud.ops.application.docker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ImageInfo(
        @JsonProperty("ID") String id,
        @JsonProperty("Repository") String repository,
        @JsonProperty("Tag") String tag,
        @JsonProperty("Size") String size,
        @JsonProperty("CreatedAt") String createdAt
) {
}
