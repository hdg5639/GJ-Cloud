package gj.cloud.vm.infra.proxmox.record;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GuestAgentDiskInfo(
        List<DiskEntry> value
) {
    public List<DiskEntry> disks() {
        return value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiskEntry(
            @JsonProperty("name")
            String name,
            @JsonProperty("used-bytes")
            Long used,
            @JsonProperty("total-bytes")
            Long total
    ) {}
}
