package gj.cloud.vm.infra.proxmox.record;

public record ProxmoxTaskStatus(String status, String exitstatus) {

    public boolean isCompleted() {
        return "stopped".equals(status);
    }

    public boolean isSuccess() {
        return "OK".equals(exitstatus);
    }
}
