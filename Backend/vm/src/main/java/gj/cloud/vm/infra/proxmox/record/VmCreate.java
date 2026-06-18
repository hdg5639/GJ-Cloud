package gj.cloud.vm.infra.proxmox.record;

import gj.cloud.vm.domain.vm.enums.PlanType;
import lombok.Getter;

@Getter
public class VmCreate {

    private final int newVmid;
    private final int templateVmid;
    private final String name;
    private final int cores;
    private final String cpu;
    private final int cpulimit;
    private final int cpuunits;
    private final String affinity;
    private final String memory;
    private final String pool;
    private final String agent;
    private final String scsihw;
    private final String net0;
    private final String ide2;
    private final String cipassword;
    private final String sshkeys;
    private final String ipconfig0;

    public static VmCreate from(PlanType planType, int newVmid, String name, String sshPublicKey,
                                 String bridge, String storage) {
        return new VmCreate(planType, newVmid, name, sshPublicKey, bridge, storage);
    }

    private VmCreate(PlanType planType, int newVmid, String name, String sshPublicKey,
                     String bridge, String storage) {
        this.newVmid = newVmid;
        this.templateVmid = planType.getTemplateVmid();
        this.name = name;
        this.sshkeys = sshPublicKey;

        this.cores = planType.getCores();
        this.memory = planType.getMemory();
        this.affinity = planType.getAffinity();

        this.cpu = "host,hidden=1";
        this.cpulimit = planType == PlanType.FREE ? 4 : 0;
        this.cpuunits = planType == PlanType.FREE ? 1024 : 3072;
        this.pool = "user-vm";
        this.agent = "1";
        this.scsihw = "virtio-scsi-pci";
        this.net0 = "virtio,bridge=" + bridge;
        this.ide2 = storage + ":cloudinit";
        this.cipassword = "password";
        this.ipconfig0 = "ip=dhcp";
    }
}
