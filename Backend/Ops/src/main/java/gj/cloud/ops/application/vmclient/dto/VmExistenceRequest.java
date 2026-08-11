package gj.cloud.ops.application.vmclient.dto;

import java.util.List;

public record VmExistenceRequest(List<String> vmIds) {
}
