package gj.cloud.vm.infra.proxmox.record;

import gj.cloud.vm.domain.vm.enums.PlanType;
import lombok.Getter;

import java.util.List;

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
    private final String nameserver;

    public static VmCreate from(PlanType planType, int newVmid, String name, List<String> sshPublicKeys,
                                 String bridge, String storage, String staticIp) {
        return new VmCreate(planType, newVmid, name, sshPublicKeys, bridge, storage, staticIp);
    }

    private VmCreate(PlanType planType, int newVmid, String name, List<String> sshPublicKeys,
                     String bridge, String storage, String staticIp) {
        this.newVmid = newVmid;
        this.templateVmid = planType.getTemplateVmid();
        this.name = name;
        this.sshkeys = String.join("\n", sshPublicKeys);

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
        this.ipconfig0 = "ip=" + staticIp + "/24,gw=192.168.0.1";
        // cloud-init에 nameserver를 지정하지 않으면 게이트웨이(192.168.0.1)가 DNS도 포워딩해준다는 보장이
        // 없어 VM 내부에서 도메인 조회(git clone, curl 등)가 전부 실패할 수 있음 — 공인 DNS로 명시 고정
        this.nameserver = "1.1.1.1 8.8.8.8";
    }
}
