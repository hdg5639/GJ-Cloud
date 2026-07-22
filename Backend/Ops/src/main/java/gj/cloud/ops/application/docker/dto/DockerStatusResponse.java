package gj.cloud.ops.application.docker.dto;

public record DockerStatusResponse(boolean installed, boolean installing, String stage, String lastError) {
}
