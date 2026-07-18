package gj.cloud.ops.application.deployment.dto;

import jakarta.validation.constraints.NotBlank;

// D-3 공유 인프라 선택 — version은 비워두면 AI가 안정적인 기본 버전을 선택함
public record InfraSelection(@NotBlank String type, String version) {
}
