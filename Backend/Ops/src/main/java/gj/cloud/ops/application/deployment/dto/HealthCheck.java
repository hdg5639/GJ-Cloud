package gj.cloud.ops.application.deployment.dto;

// D.7 헬스체크 기준 — hostPort(127.0.0.1 기준) 또는 containerPort(compose 네트워크 내부 기준) 중 하나로 확인
public record HealthCheck(String serviceName, String path, Integer hostPort, Integer containerPort) {
}
