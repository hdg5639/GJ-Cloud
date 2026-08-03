package gj.cloud.vm.domain.org.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("organization_vms")
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationVmEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    @Builder.Default
    private boolean isNew = false;

    @Override
    public boolean isNew() { return isNew; }

    @Column("organization_id")
    private UUID organizationId;

    @Column("vm_id")
    private UUID vmId;

    @Column("added_at")
    private LocalDateTime addedAt;

    public static OrganizationVmEntity create(UUID orgId, UUID vmId) {
        return OrganizationVmEntity.builder()
                .id(UUID.randomUUID())
                .isNew(true)
                .organizationId(orgId)
                .vmId(vmId)
                .addedAt(LocalDateTime.now())
                .build();
    }
}
