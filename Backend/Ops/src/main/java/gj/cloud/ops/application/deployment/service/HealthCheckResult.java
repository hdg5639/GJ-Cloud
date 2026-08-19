package gj.cloud.ops.application.deployment.service;

import gj.cloud.ops.global.ssh.CommandResult;

record HealthCheckResult(boolean healthy, Integer httpStatus, CommandResult commandResult) {
}
