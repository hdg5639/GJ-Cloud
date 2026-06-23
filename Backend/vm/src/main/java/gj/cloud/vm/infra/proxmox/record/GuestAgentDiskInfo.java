package gj.cloud.vm.infra.proxmox.record;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GuestAgentDiskInfo(
        @JsonProperty("disks")
        List<DiskEntry> disks
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiskEntry(
            @JsonProperty("name")
            String name,
            @JsonProperty("used")
            Long used,
            @JsonProperty("total")
            Long total
    ) {}
}
